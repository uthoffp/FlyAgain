# Chat System Design — Phase 1.7

## Overview

Chat system for FlyAgain with three channels: Say (nearby players), Shout (zone-wide), and Whisper (server-wide private messages). Implemented in the World-Service behind a `ChatService` interface for future extensibility (guild chat, party chat in Phase 2/3).

## Channels

| Channel | Scope | Prefix | channel_type |
|---------|-------|--------|--------------|
| Say | SpatialGrid 3x3 neighbors | *(none, default)* | 0 |
| Shout | Entire ZoneChannel | `/shout` | 1 |
| Whisper | Server-wide (any online player) | `/say PlayerName` | 2 (incoming), 3 (outgoing echo) |

## Protocol

### Opcodes

- `CHAT_MESSAGE (0x0501)` — Client to Server
- `CHAT_BROADCAST (0x0502)` — Server to Client
- Whisper errors use existing `ERROR_RESPONSE (0x0603)`

### ChatMessageRequest (0x0501, Client → Server)

| Field | Type | Proto Field # | Description |
|-------|------|---------------|-------------|
| channel_type | int32 | 1 | 0=say, 1=shout, 2=whisper |
| text | string | 2 | Message content, max 200 chars |
| target_name | string | 3 | Whisper recipient name (only for channel_type=2) |

### ChatBroadcastMessage (0x0502, Server → Client)

| Field | Type | Proto Field # | Description |
|-------|------|---------------|-------------|
| sender_name | string | 1 | Character name of sender |
| sender_entity_id | int64 | 2 | Entity ID of sender |
| text | string | 3 | Message content |
| channel_type | int32 | 4 | 0=say, 1=shout, 2=whisper_in, 3=whisper_out |
| timestamp | int64 | 5 | Server time in milliseconds |

## Server Architecture

### ChatService Interface

```kotlin
interface ChatService {
    fun handleSay(player: PlayerEntity, text: String)
    fun handleShout(player: PlayerEntity, text: String)
    fun handleWhisper(player: PlayerEntity, targetName: String, text: String)
}
```

Lives in `world-service/chat/ChatService.kt`. Allows future replacement with a dedicated chat microservice.

### ChatServiceImpl

- **handleSay**: Get nearby entities via `ZoneChannel.getNearbyEntities(player.x, player.z)`, send ChatBroadcast (channel_type=0) to all nearby players including sender
- **handleShout**: Get all players via `ZoneChannel.getAllPlayers()`, send ChatBroadcast (channel_type=1) to all
- **handleWhisper**: Lookup target via `EntityManager.getPlayerByName(targetName)`:
  - Found → send ChatBroadcast (channel_type=2, whisper_in) to recipient + ChatBroadcast (channel_type=3, whisper_out) to sender
  - Not found → ErrorResponse (0x0603) to sender: "Player not found" / "Spieler nicht gefunden"
  - Self-whisper → ErrorResponse

Dependencies: ZoneManager, EntityManager, BroadcastService (injected via Koin).

### ChatHandler

Registered in PacketRouter for opcode `0x0501`. Responsibilities:

1. Parse ChatMessageRequest from protobuf payload
2. Validate input (see Security section)
3. Check rate limit
4. Route to ChatService based on channel_type

### EntityManager Extension

New lookup map: `playersByName: ConcurrentHashMap<String, PlayerEntity>` (lowercase key for case-insensitive lookup).

New method: `getPlayerByName(name: String): PlayerEntity?`

Populated/cleared alongside existing maps in `addPlayer()`/`removePlayer()`.

### Rate Limiting

- **Budget**: 10 messages per 10 seconds, shared across all channels
- **Implementation**: In-memory `ArrayDeque<Long>` on PlayerEntity storing timestamps of recent messages
- **On exceed**: ErrorResponse with code 429, "Rate limit exceeded" / "Nachrichtenlimit erreicht"
- **No Redis needed** — player state is ephemeral and per-connection

### PacketRouter Integration

New case in opcode dispatch for `OPCODE_CHAT_MESSAGE (0x0501)` → delegates to `ChatHandler.handle()`.

### Koin Module

Register `ChatService` (bind interface) and `ChatHandler` as singletons in the world-service Koin module.

## Client Architecture

### Proto Extensions

**ProtoEncoder**: `encode_chat_message(text: String, channel_type: int, target_name: String) -> PackedByteArray`

**ProtoDecoder**: `decode_chat_broadcast(payload: PackedByteArray) -> Dictionary` with keys: `sender_name`, `sender_entity_id`, `text`, `channel_type`, `timestamp`

### NetworkManager Extensions

- Signal: `chat_broadcast_received(data: Dictionary)`
- Method: `send_chat_message(text: String, channel_type: int, target_name: String)`
- Dispatch: New case in `_dispatch_world_frame()` for `OPCODE_CHAT_BROADCAST (0x0502)`

### Main Chat Window (`scenes/ui/ChatWindow.tscn`)

- Scrollable RichTextLabel for message display (BBCode for colors)
- LineEdit at bottom for input
- Color scheme: Say=white, Shout=yellow, System/Error=red
- Prefix parsing in input: no prefix=say, `/shout`=shout, `/say Name`=whisper
- Say/Shout history cleared on zone change
- Displays say, shout, and system messages (NOT whisper — those go to WhisperWindows)

### Input Behavior

- `Enter` focuses/opens chat input
- Second `Enter` sends message
- `Escape` closes/unfocuses input
- While chat input is focused: all gameplay inputs (WASD, skills, targeting) are blocked via input-consuming flag in GameState

### Whisper Windows (`scenes/ui/WhisperWindow.tscn`)

- Small window per conversation partner
- Own RichTextLabel (message history) + LineEdit (input)
- Opens automatically on incoming whisper (channel_type=2) or outgoing (channel_type=3)
- Closable via X button
- Survives zone changes, cleared on logout

### WhisperManager (`scripts/ui/WhisperManager.gd`)

- Manages open whisper windows as `Dictionary {player_name: WhisperWindow}`
- `open_or_get(player_name: String) -> WhisperWindow`: returns existing or creates new
- **Max 5 simultaneous windows**: when a 6th is needed, the least recently active window is replaced
- Autoload or child of HUD node

### Localization

All UI labels, placeholder texts, tooltips, and error messages localized in English (en) and German (de) via Godot's `TR()` system.

## Security & Validation

### Server-Side (ChatHandler)

- Max 200 characters — reject longer messages with ErrorResponse
- Strip null bytes (`\0`)
- Strip HTML/BBCode tags — plaintext only
- Trim leading/trailing whitespace
- Reject empty messages after sanitization
- Whisper to self → reject
- Target name case-insensitive lookup

### Rate Limiting

- 10 messages per 10 seconds, global budget across all channels
- In-memory ArrayDeque on PlayerEntity
- On exceed → ErrorResponse 429

### Client-Side

- LineEdit max_length = 200
- Empty input → do not send packet
- Validate target name is not empty for `/say` prefix

## Persistence

None. All chat is transient — in-memory only, no server-side logging, no database tables.

- Say/Shout history: client-side, cleared on zone change
- Whisper windows: client-side, survive zone change, cleared on logout

## Acceptance Criteria

- Say chat: only players in SpatialGrid neighborhood see the message
- Shout chat: all players in the zone see the message
- Whisper: server-wide, opens dedicated window at both sender and receiver
- Rate limiting works (no spam flooding)
- Special characters/HTML tags are handled correctly
- `/say OfflinePlayer` returns error response
- Max 5 whisper windows, oldest replaced when exceeded
- Enter focuses chat, Escape unfocuses, gameplay inputs blocked during chat
- All UI text localized in en + de
