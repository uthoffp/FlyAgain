# Chat System Implementation Plan — Phase 1.7

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a chat system with Say (nearby), Shout (zone-wide), and Whisper (server-wide private) channels.

**Architecture:** ChatService interface + ChatServiceImpl in world-service behind the existing PacketRouter. Client uses main ChatWindow for Say/Shout and separate WhisperWindows managed by WhisperManager. All messages are transient (no persistence).

**Tech Stack:** Kotlin/Netty (server), Protobuf (protocol), GDScript/Godot 4 (client)

**Spec:** `docs/superpowers/specs/2026-04-02-chat-system-design.md`

---

## File Structure

### Server (new files)
- `server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatService.kt` — Interface
- `server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatServiceImpl.kt` — Implementation
- `server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatMessageHandler.kt` — Packet handler
- `server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatMessageSanitizer.kt` — Input validation
- `server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatMessageSanitizerTest.kt` — Sanitizer tests
- `server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatServiceImplTest.kt` — Service tests
- `server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatMessageHandlerTest.kt` — Handler tests

### Server (modified files)
- `shared/proto/flyagain.proto` — Add ChatMessageRequest + ChatBroadcastMessage
- `server/world-service/src/main/kotlin/com/flyagain/world/entity/PlayerEntity.kt:68` — Add chat rate limit field
- `server/world-service/src/main/kotlin/com/flyagain/world/entity/EntityManager.kt:33` — Add playersByName map + getPlayerByName()
- `server/world-service/src/main/kotlin/com/flyagain/world/network/BroadcastService.kt:227` — Add broadcastChatToNearby()
- `server/world-service/src/main/kotlin/com/flyagain/world/handler/PacketRouter.kt:44-56,97` — Add ChatMessageHandler + dispatch
- `server/world-service/src/main/kotlin/com/flyagain/world/di/WorldServiceModule.kt:77-161` — Register chat beans
- `server/world-service/src/test/kotlin/com/flyagain/world/EntityManagerTest.kt` — Add name lookup tests

### Client (new files)
- `client/scenes/ui/game_hud/ChatWindow.gd` — Main chat window script
- `client/scenes/ui/game_hud/WhisperWindow.gd` — Individual whisper window script
- `client/scenes/ui/game_hud/WhisperManager.gd` — Manages whisper windows (max 5)

### Client (modified files)
- `client/scripts/proto/ProtoEncoder.gd` — Add encode_chat_message()
- `client/scripts/proto/ProtoDecoder.gd` — Add decode_chat_broadcast()
- `client/autoloads/NetworkManager.gd` — Add signal, dispatch, send method
- `client/autoloads/GameState.gd` — Add chat_input_active flag
- `client/scenes/game/GameWorld.gd` — Add chat windows to HUD setup
- `client/scenes/game/PlayerCharacter.gd` — Check chat_input_active before processing input
- `client/translations/translations.csv` — Add chat translation keys

---

## Task 1: Protobuf Message Definitions

**Files:**
- Modify: `shared/proto/flyagain.proto:53-55`

- [ ] **Step 1: Add ChatMessageRequest and ChatBroadcastMessage to proto**

Note: The opcodes `CHAT_MESSAGE (0x0501)` and `CHAT_BROADCAST (0x0502)` already exist in the Opcode enum at lines 53-55. Only the message definitions need to be added.

Add after line 455 (end of Inventory & Shop Messages section):

```proto
// ============================================================
// Chat Messages
// ============================================================

// Client -> Server: Send a chat message
message ChatMessageRequest {
    int32 channel_type = 1;    // 0=say, 1=shout, 2=whisper
    string text = 2;           // Message content, max 200 chars
    string target_name = 3;    // Whisper recipient (only for channel_type=2)
}

// Server -> Client: Chat message broadcast
message ChatBroadcastMessage {
    string sender_name = 1;
    int64 sender_entity_id = 2;
    string text = 3;
    int32 channel_type = 4;    // 0=say, 1=shout, 2=whisper_in, 3=whisper_out
    int64 timestamp = 5;       // Server time in milliseconds
    string target_name = 6;    // For whisper_out: name of the recipient (so client can route)
}
```

- [ ] **Step 2: Regenerate protobuf stubs**

Run: `cd server && ./gradlew :common:generateProto`

Expected: BUILD SUCCESS, new Java classes `ChatMessageRequest` and `ChatBroadcastMessage` generated.

- [ ] **Step 3: Verify build**

Run: `cd server && ./gradlew build -x test`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/proto/flyagain.proto
git commit -m "proto: add ChatMessageRequest and ChatBroadcastMessage for Phase 1.7"
```

---

## Task 2: Chat Message Sanitizer (Server — TDD)

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatMessageSanitizer.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatMessageSanitizerTest.kt`

- [ ] **Step 1: Write failing tests for sanitizer**

```kotlin
package com.flyagain.world.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatMessageSanitizerTest {

    private val sanitizer = ChatMessageSanitizer()

    @Test
    fun `normal text passes through unchanged`() {
        assertEquals("Hello world", sanitizer.sanitize("Hello world"))
    }

    @Test
    fun `strips null bytes`() {
        assertEquals("Hello", sanitizer.sanitize("Hel\u0000lo"))
    }

    @Test
    fun `strips unicode control characters`() {
        assertEquals("Hello", sanitizer.sanitize("He\u0001l\u001Flo"))
    }

    @Test
    fun `strips zero-width space`() {
        assertEquals("Hello", sanitizer.sanitize("Hel\u200Blo"))
    }

    @Test
    fun `strips RTL override`() {
        assertEquals("Hello", sanitizer.sanitize("Hel\u202Elo"))
    }

    @Test
    fun `strips HTML tags`() {
        assertEquals("bold text", sanitizer.sanitize("<b>bold</b> text"))
    }

    @Test
    fun `strips BBCode tags`() {
        assertEquals("colored", sanitizer.sanitize("[color=red]colored[/color]"))
    }

    @Test
    fun `trims whitespace`() {
        assertEquals("Hello", sanitizer.sanitize("  Hello  "))
    }

    @Test
    fun `returns null for empty string after sanitization`() {
        assertNull(sanitizer.sanitize("   "))
    }

    @Test
    fun `returns null for null-bytes-only string`() {
        assertNull(sanitizer.sanitize("\u0000\u0000"))
    }

    @Test
    fun `returns null for text exceeding max length`() {
        assertNull(sanitizer.sanitize("a".repeat(201)))
    }

    @Test
    fun `accepts exactly 200 characters`() {
        val text = "a".repeat(200)
        assertEquals(text, sanitizer.sanitize(text))
    }

    @Test
    fun `preserves normal unicode like umlauts`() {
        assertEquals("Hallo Welt äöü", sanitizer.sanitize("Hallo Welt äöü"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.chat.ChatMessageSanitizerTest" -i`

Expected: FAIL — class not found

- [ ] **Step 3: Implement ChatMessageSanitizer**

```kotlin
package com.flyagain.world.chat

/**
 * Sanitizes chat message input: strips control characters, HTML/BBCode tags,
 * trims whitespace, and enforces max length.
 */
class ChatMessageSanitizer {

    companion object {
        const val MAX_LENGTH = 200

        // Matches HTML tags like <b>, </div>, <img src="...">, etc.
        private val HTML_TAG_REGEX = Regex("<[^>]+>")

        // Matches BBCode tags like [b], [/color], [url=...], etc.
        private val BBCODE_TAG_REGEX = Regex("\\[[^]]+]")

        // Unicode control chars, zero-width, bidi overrides
        private val CONTROL_CHAR_REGEX = Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]")
    }

    /**
     * Sanitize a chat message. Returns null if the result is empty or exceeds max length.
     */
    fun sanitize(input: String): String? {
        if (input.length > MAX_LENGTH) return null

        var text = input
        text = CONTROL_CHAR_REGEX.replace(text, "")
        text = HTML_TAG_REGEX.replace(text, "")
        text = BBCODE_TAG_REGEX.replace(text, "")
        text = text.trim()

        return text.ifEmpty { null }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.chat.ChatMessageSanitizerTest" -i`

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatMessageSanitizer.kt \
       server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatMessageSanitizerTest.kt
git commit -m "feat(world): add ChatMessageSanitizer with TDD tests"
```

---

## Task 3: EntityManager Name Lookup (Server — TDD)

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/entity/EntityManager.kt:33,54,70,106`
- Modify: `server/world-service/src/test/kotlin/com/flyagain/world/EntityManagerTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `EntityManagerTest.kt`:

```kotlin
@Test
fun `getPlayerByName returns correct player`() {
    val player = makePlayer(entityId = 1L, accountId = "100", characterId = "200")
    manager.tryAddPlayer(player)
    val retrieved = manager.getPlayerByName("TestPlayer")
    assertNotNull(retrieved)
    assertEquals(1L, retrieved.entityId)
}

@Test
fun `getPlayerByName is case-insensitive`() {
    val player = makePlayer(entityId = 1L, accountId = "100", characterId = "200")
    manager.tryAddPlayer(player)
    assertNotNull(manager.getPlayerByName("testplayer"))
    assertNotNull(manager.getPlayerByName("TESTPLAYER"))
}

@Test
fun `getPlayerByName returns null for unknown name`() {
    assertNull(manager.getPlayerByName("Nobody"))
}

@Test
fun `removePlayer cleans up name lookup`() {
    val player = makePlayer(entityId = 5L, accountId = "10", characterId = "20")
    manager.tryAddPlayer(player)
    manager.removePlayer(5L)
    assertNull(manager.getPlayerByName("TestPlayer"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.EntityManagerTest" -i`

Expected: FAIL — `getPlayerByName` not found

- [ ] **Step 3: Implement name lookup in EntityManager**

In `EntityManager.kt`:

1. Add map after line 33:
```kotlin
// Lookup by character name (lowercase key for case-insensitive lookup)
private val playersByName = ConcurrentHashMap<String, PlayerEntity>()
```

2. Add to `tryAddPlayer()` after line 57 (after sessionToken put):
```kotlin
playersByName[player.name.lowercase()] = player
```

3. Add to `removePlayer()` after line 72 (after sessionToken remove):
```kotlin
playersByName.remove(player.name.lowercase())
```

4. Add method after line 111:
```kotlin
/**
 * Get a player by character name (case-insensitive).
 */
fun getPlayerByName(name: String): PlayerEntity? = playersByName[name.lowercase()]
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.EntityManagerTest" -i`

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/entity/EntityManager.kt \
       server/world-service/src/test/kotlin/com/flyagain/world/EntityManagerTest.kt
git commit -m "feat(world): add case-insensitive player name lookup to EntityManager"
```

---

## Task 4: PlayerEntity Chat Rate Limit Field

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/entity/PlayerEntity.kt:68`

- [ ] **Step 1: Add chatMessageTimestamps field to PlayerEntity**

After line 68 (`var lastInventoryOpTime: Long = 0L,`), add:

```kotlin
) {
```

Wait — `PlayerEntity` is a data class, so we add the field inside the constructor. Add after line 68:

```kotlin
    // Chat rate limiting — timestamps of recent messages (sliding window)
    var lastChatMessageTime: Long = 0L,
```

But the spec says ArrayDeque. Actually, a simpler approach matching the existing pattern (lastZoneChangeTime etc.) won't work for 10/10s. We need the ArrayDeque as a body property (like `knownEntities` on line 87).

Add after line 87 (`val knownEntities: MutableSet<Long> = HashSet(128)`):

```kotlin
    /** Timestamps of recent chat messages for rate limiting (10 per 10 seconds). */
    val chatMessageTimestamps: ArrayDeque<Long> = ArrayDeque(10)
```

- [ ] **Step 2: Verify build**

Run: `cd server && ./gradlew :world-service:test -i`

Expected: All existing tests still pass

- [ ] **Step 3: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/entity/PlayerEntity.kt
git commit -m "feat(world): add chat rate limit tracking to PlayerEntity"
```

---

## Task 5: BroadcastService Chat Methods

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/network/BroadcastService.kt:227`

- [ ] **Step 1: Add broadcastChatToNearby method**

Add after line 247 (`sendToPlayer` method), before `sendXpGain`:

```kotlin
    /**
     * Broadcast a chat message to all players near a position (including sender).
     * Uses immediate writeAndFlush since chat is not part of the game loop tick.
     */
    fun broadcastChatToNearby(channel: ZoneChannel, x: Float, z: Float, packet: Packet) {
        val nearbyEntityIds = channel.getNearbyEntities(x, z)
        for (entityId in nearbyEntityIds) {
            val player = channel.getPlayer(entityId) ?: continue
            player.tcpChannel?.writeAndFlush(packet)
        }
    }

    /**
     * Broadcast a chat message to all players in a zone channel.
     * Uses immediate writeAndFlush since chat is not part of the game loop tick.
     */
    fun broadcastChatToChannel(channel: ZoneChannel, packet: Packet) {
        for (player in channel.getAllPlayers()) {
            player.tcpChannel?.writeAndFlush(packet)
        }
    }
```

- [ ] **Step 2: Verify build**

Run: `cd server && ./gradlew :world-service:test -i`

Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/network/BroadcastService.kt
git commit -m "feat(world): add chat broadcast methods to BroadcastService"
```

---

## Task 6: ChatService Interface + Implementation (Server — TDD)

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatService.kt`
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatServiceImpl.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatServiceImplTest.kt`

- [ ] **Step 1: Create ChatService interface**

```kotlin
package com.flyagain.world.chat

import com.flyagain.world.entity.PlayerEntity
import io.netty.channel.ChannelHandlerContext

/**
 * Chat service interface for handling chat messages across channels.
 * Allows future replacement with a dedicated chat microservice.
 */
interface ChatService {
    fun handleSay(ctx: ChannelHandlerContext, player: PlayerEntity, text: String)
    fun handleShout(ctx: ChannelHandlerContext, player: PlayerEntity, text: String)
    fun handleWhisper(ctx: ChannelHandlerContext, player: PlayerEntity, targetName: String, text: String)
}
```

- [ ] **Step 2: Write failing tests for ChatServiceImpl**

```kotlin
package com.flyagain.world.chat

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ChatBroadcastMessage
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import io.mockk.*
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatServiceImplTest {

    private val zoneManager = mockk<ZoneManager>()
    private val entityManager = mockk<EntityManager>()
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val service = ChatServiceImpl(zoneManager, entityManager, broadcastService)

    private fun makePlayer(
        entityId: Long = 1L,
        name: String = "Sender",
        zoneId: Int = 1,
        channelId: Int = 0
    ): PlayerEntity {
        val player = PlayerEntity(
            entityId = entityId, characterId = "c1", accountId = "a1",
            name = name, characterClass = 1, x = 100f, y = 0f, z = 100f
        )
        player.zoneId = zoneId
        player.channelId = channelId
        return player
    }

    private fun mockCtx(): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        return ctx
    }

    private fun mockZoneChannel(): ZoneChannel {
        return mockk<ZoneChannel>(relaxed = true)
    }

    @Test
    fun `handleSay broadcasts to nearby players via BroadcastService`() {
        val player = makePlayer()
        val ctx = mockCtx()
        val channel = mockZoneChannel()
        every { zoneManager.getChannel(1, 0) } returns channel

        service.handleSay(ctx, player, "Hello nearby")

        verify(exactly = 1) {
            broadcastService.broadcastChatToNearby(channel, 100f, 100f, any())
        }
    }

    @Test
    fun `handleSay does nothing when zone channel not found`() {
        val player = makePlayer()
        val ctx = mockCtx()
        every { zoneManager.getChannel(1, 0) } returns null

        service.handleSay(ctx, player, "Hello")

        verify(exactly = 0) { broadcastService.broadcastChatToNearby(any(), any(), any(), any()) }
    }

    @Test
    fun `handleShout broadcasts to all players in channel`() {
        val player = makePlayer()
        val ctx = mockCtx()
        val channel = mockZoneChannel()
        every { zoneManager.getChannel(1, 0) } returns channel

        service.handleShout(ctx, player, "Hello zone!")

        verify(exactly = 1) {
            broadcastService.broadcastChatToChannel(channel, any())
        }
    }

    @Test
    fun `handleWhisper sends to target and echo to sender`() {
        val sender = makePlayer(entityId = 1L, name = "Sender")
        val target = makePlayer(entityId = 2L, name = "Target")
        target.tcpChannel = mockk(relaxed = true)
        val ctx = mockCtx()
        every { entityManager.getPlayerByName("Target") } returns target

        service.handleWhisper(ctx, sender, "Target", "Secret message")

        // whisper_in to target
        verify(exactly = 1) { broadcastService.sendToPlayer(target, any()) }
        // whisper_out echo to sender
        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }

    @Test
    fun `handleWhisper sends error when target not found`() {
        val sender = makePlayer(name = "Sender")
        val ctx = mockCtx()
        every { entityManager.getPlayerByName("Nobody") } returns null

        service.handleWhisper(ctx, sender, "Nobody", "Hello?")

        verify(exactly = 1) {
            ctx.writeAndFlush(match<Packet> { packet ->
                packet.opcode == Opcode.ERROR_RESPONSE_VALUE &&
                    ErrorResponse.parseFrom(packet.payload).errorCode == 404
            })
        }
    }

    @Test
    fun `handleWhisper sends error when whispering to self`() {
        val sender = makePlayer(entityId = 1L, name = "Sender")
        val ctx = mockCtx()
        every { entityManager.getPlayerByName("Sender") } returns sender

        service.handleWhisper(ctx, sender, "Sender", "Talking to myself")

        verify(exactly = 1) {
            ctx.writeAndFlush(match<Packet> { packet ->
                packet.opcode == Opcode.ERROR_RESPONSE_VALUE &&
                    ErrorResponse.parseFrom(packet.payload).errorCode == 400
            })
        }
    }

    @Test
    fun `handleSay broadcast contains correct channel_type 0`() {
        val player = makePlayer()
        val ctx = mockCtx()
        val channel = mockZoneChannel()
        every { zoneManager.getChannel(1, 0) } returns channel

        val packetSlot = slot<Packet>()
        every { broadcastService.broadcastChatToNearby(any(), any(), any(), capture(packetSlot)) } just Runs

        service.handleSay(ctx, player, "Test")

        val msg = ChatBroadcastMessage.parseFrom(packetSlot.captured.payload)
        assertEquals(0, msg.channelType)
        assertEquals("Sender", msg.senderName)
        assertEquals("Test", msg.text)
    }

    @Test
    fun `handleShout broadcast contains correct channel_type 1`() {
        val player = makePlayer()
        val ctx = mockCtx()
        val channel = mockZoneChannel()
        every { zoneManager.getChannel(1, 0) } returns channel

        val packetSlot = slot<Packet>()
        every { broadcastService.broadcastChatToChannel(any(), capture(packetSlot)) } just Runs

        service.handleShout(ctx, player, "Shout test")

        val msg = ChatBroadcastMessage.parseFrom(packetSlot.captured.payload)
        assertEquals(1, msg.channelType)
        assertEquals("Shout test", msg.text)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.chat.ChatServiceImplTest" -i`

Expected: FAIL — ChatServiceImpl not found

- [ ] **Step 4: Implement ChatServiceImpl**

```kotlin
package com.flyagain.world.chat

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ChatBroadcastMessage
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

class ChatServiceImpl(
    private val zoneManager: ZoneManager,
    private val entityManager: EntityManager,
    private val broadcastService: BroadcastService
) : ChatService {

    private val logger = LoggerFactory.getLogger(ChatServiceImpl::class.java)

    override fun handleSay(ctx: ChannelHandlerContext, player: PlayerEntity, text: String) {
        val channel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (channel == null) {
            logger.warn("Say chat failed: zone channel not found for player {}", player.name)
            return
        }

        val packet = buildChatBroadcast(player, text, CHANNEL_SAY)
        broadcastService.broadcastChatToNearby(channel, player.x, player.z, packet)
        logger.debug("Say chat from {} in zone {}", player.name, player.zoneId)
    }

    override fun handleShout(ctx: ChannelHandlerContext, player: PlayerEntity, text: String) {
        val channel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (channel == null) {
            logger.warn("Shout chat failed: zone channel not found for player {}", player.name)
            return
        }

        val packet = buildChatBroadcast(player, text, CHANNEL_SHOUT)
        broadcastService.broadcastChatToChannel(channel, packet)
        logger.debug("Shout chat from {} in zone {}", player.name, player.zoneId)
    }

    override fun handleWhisper(ctx: ChannelHandlerContext, player: PlayerEntity, targetName: String, text: String) {
        val target = entityManager.getPlayerByName(targetName)

        if (target == null) {
            sendError(ctx, 404, "Player not found.")
            return
        }

        if (target.entityId == player.entityId) {
            sendError(ctx, 400, "Cannot whisper to yourself.")
            return
        }

        // Send whisper_in to target
        val inPacket = buildChatBroadcast(player, text, CHANNEL_WHISPER_IN)
        broadcastService.sendToPlayer(target, inPacket)

        // Send whisper_out echo to sender (include target name for client routing)
        val outMsg = ChatBroadcastMessage.newBuilder()
            .setSenderName(player.name)
            .setSenderEntityId(player.entityId)
            .setText(text)
            .setChannelType(CHANNEL_WHISPER_OUT)
            .setTimestamp(System.currentTimeMillis())
            .setTargetName(target.name)
            .build()
        ctx.writeAndFlush(Packet(Opcode.CHAT_BROADCAST_VALUE, outMsg.toByteArray()))

        logger.debug("Whisper from {} to {}", player.name, target.name)
    }

    private fun buildChatBroadcast(sender: PlayerEntity, text: String, channelType: Int): Packet {
        val msg = ChatBroadcastMessage.newBuilder()
            .setSenderName(sender.name)
            .setSenderEntityId(sender.entityId)
            .setText(text)
            .setChannelType(channelType)
            .setTimestamp(System.currentTimeMillis())
            .build()
        return Packet(Opcode.CHAT_BROADCAST_VALUE, msg.toByteArray())
    }

    private fun sendError(ctx: ChannelHandlerContext, errorCode: Int, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(Opcode.CHAT_MESSAGE_VALUE)
            .setErrorCode(errorCode)
            .setMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray()))
    }

    companion object {
        const val CHANNEL_SAY = 0
        const val CHANNEL_SHOUT = 1
        const val CHANNEL_WHISPER_IN = 2
        const val CHANNEL_WHISPER_OUT = 3
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.chat.ChatServiceImplTest" -i`

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatService.kt \
       server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatServiceImpl.kt \
       server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatServiceImplTest.kt
git commit -m "feat(world): add ChatService interface and implementation with tests"
```

---

## Task 7: ChatMessageHandler + PacketRouter Integration (Server — TDD)

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatMessageHandler.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatMessageHandlerTest.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/handler/PacketRouter.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/di/WorldServiceModule.kt`

- [ ] **Step 1: Write failing tests for ChatMessageHandler**

```kotlin
package com.flyagain.world.chat

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ChatMessageRequest
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import io.mockk.*
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlin.test.Test

class ChatMessageHandlerTest {

    private val chatService = mockk<ChatService>(relaxed = true)
    private val sanitizer = ChatMessageSanitizer()
    private val handler = ChatMessageHandler(chatService, sanitizer)

    private fun makePlayer(name: String = "TestPlayer"): PlayerEntity {
        return PlayerEntity(
            entityId = 1L, characterId = "c1", accountId = "a1",
            name = name, characterClass = 1, x = 0f, y = 0f, z = 0f
        )
    }

    private fun mockCtx(): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        return ctx
    }

    private fun buildRequest(text: String, channelType: Int = 0, targetName: String = ""): Packet {
        val req = ChatMessageRequest.newBuilder()
            .setChannelType(channelType)
            .setText(text)
            .setTargetName(targetName)
            .build()
        return Packet(Opcode.CHAT_MESSAGE_VALUE, req.toByteArray())
    }

    @Test
    fun `routes say message to chatService handleSay`() {
        val ctx = mockCtx()
        val player = makePlayer()
        handler.handle(ctx, player, buildRequest("Hello", channelType = 0))

        verify(exactly = 1) { chatService.handleSay(ctx, player, "Hello") }
    }

    @Test
    fun `routes shout message to chatService handleShout`() {
        val ctx = mockCtx()
        val player = makePlayer()
        handler.handle(ctx, player, buildRequest("Hello zone!", channelType = 1))

        verify(exactly = 1) { chatService.handleShout(ctx, player, "Hello zone!") }
    }

    @Test
    fun `routes whisper message to chatService handleWhisper`() {
        val ctx = mockCtx()
        val player = makePlayer()
        handler.handle(ctx, player, buildRequest("Secret", channelType = 2, targetName = "Target"))

        verify(exactly = 1) { chatService.handleWhisper(ctx, player, "Target", "Secret") }
    }

    @Test
    fun `rejects empty message after sanitization`() {
        val ctx = mockCtx()
        val player = makePlayer()
        handler.handle(ctx, player, buildRequest("   ", channelType = 0))

        verify(exactly = 0) { chatService.handleSay(any(), any(), any()) }
        verify(exactly = 1) {
            ctx.writeAndFlush(match<Packet> { it.opcode == Opcode.ERROR_RESPONSE_VALUE })
        }
    }

    @Test
    fun `rejects message exceeding 200 characters`() {
        val ctx = mockCtx()
        val player = makePlayer()
        handler.handle(ctx, player, buildRequest("a".repeat(201), channelType = 0))

        verify(exactly = 0) { chatService.handleSay(any(), any(), any()) }
    }

    @Test
    fun `rate limits after 10 messages in 10 seconds`() {
        val ctx = mockCtx()
        val player = makePlayer()

        // Send 10 messages (should all succeed)
        repeat(10) {
            handler.handle(ctx, player, buildRequest("msg$it", channelType = 0))
        }

        verify(exactly = 10) { chatService.handleSay(any(), any(), any()) }

        // 11th message should be rate limited
        handler.handle(ctx, player, buildRequest("spam", channelType = 0))

        verify(exactly = 10) { chatService.handleSay(any(), any(), any()) }
        verify(atLeast = 1) {
            ctx.writeAndFlush(match<Packet> {
                it.opcode == Opcode.ERROR_RESPONSE_VALUE &&
                    ErrorResponse.parseFrom(it.payload).errorCode == 429
            })
        }
    }

    @Test
    fun `rejects invalid channel type`() {
        val ctx = mockCtx()
        val player = makePlayer()
        handler.handle(ctx, player, buildRequest("test", channelType = 5))

        verify(exactly = 0) { chatService.handleSay(any(), any(), any()) }
        verify(exactly = 0) { chatService.handleShout(any(), any(), any()) }
        verify(exactly = 0) { chatService.handleWhisper(any(), any(), any(), any()) }
    }

    @Test
    fun `rejects whisper with empty target name`() {
        val ctx = mockCtx()
        val player = makePlayer()
        handler.handle(ctx, player, buildRequest("Hello", channelType = 2, targetName = ""))

        verify(exactly = 0) { chatService.handleWhisper(any(), any(), any(), any()) }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.chat.ChatMessageHandlerTest" -i`

Expected: FAIL — ChatMessageHandler not found

- [ ] **Step 3: Implement ChatMessageHandler**

```kotlin
package com.flyagain.world.chat

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ChatMessageRequest
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

class ChatMessageHandler(
    private val chatService: ChatService,
    private val sanitizer: ChatMessageSanitizer
) {
    private val logger = LoggerFactory.getLogger(ChatMessageHandler::class.java)

    companion object {
        private const val RATE_LIMIT_WINDOW_MS = 10_000L
        private const val RATE_LIMIT_MAX_MESSAGES = 10
    }

    fun handle(ctx: ChannelHandlerContext, player: PlayerEntity, packet: Packet) {
        val request = try {
            ChatMessageRequest.parseFrom(packet.payload)
        } catch (e: Exception) {
            logger.warn("Failed to parse CHAT_MESSAGE from player {}: {}", player.name, e.message)
            sendError(ctx, 400, "Malformed request.")
            return
        }

        // Sanitize text
        val sanitizedText = sanitizer.sanitize(request.text)
        if (sanitizedText == null) {
            sendError(ctx, 400, "Invalid message.")
            return
        }

        // Rate limit
        if (!checkRateLimit(player)) {
            sendError(ctx, 429, "Rate limit exceeded.")
            return
        }

        // Route to appropriate channel handler
        when (request.channelType) {
            0 -> chatService.handleSay(ctx, player, sanitizedText)
            1 -> chatService.handleShout(ctx, player, sanitizedText)
            2 -> {
                if (request.targetName.isBlank()) {
                    sendError(ctx, 400, "Target name required for whisper.")
                    return
                }
                chatService.handleWhisper(ctx, player, request.targetName, sanitizedText)
            }
            else -> {
                sendError(ctx, 400, "Invalid channel type.")
            }
        }
    }

    private fun checkRateLimit(player: PlayerEntity): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = player.chatMessageTimestamps

        // Remove expired timestamps
        while (timestamps.isNotEmpty() && now - timestamps.first() > RATE_LIMIT_WINDOW_MS) {
            timestamps.removeFirst()
        }

        if (timestamps.size >= RATE_LIMIT_MAX_MESSAGES) {
            return false
        }

        timestamps.addLast(now)
        return true
    }

    private fun sendError(ctx: ChannelHandlerContext, errorCode: Int, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(Opcode.CHAT_MESSAGE_VALUE)
            .setErrorCode(errorCode)
            .setMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray()))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.chat.ChatMessageHandlerTest" -i`

Expected: All tests PASS

- [ ] **Step 5: Integrate into PacketRouter**

In `PacketRouter.kt`:

1. Add import at top: `import com.flyagain.world.chat.ChatMessageHandler`

2. Add constructor parameter after line 52 (`npcShopHandler`):
```kotlin
    private val chatMessageHandler: ChatMessageHandler,
```

3. Add to KDoc (line 41 area): `* - CHAT_MESSAGE (0x0501) - chat message`

4. Add new case in `when(opcode)` block, before the inventory group (before line 175):
```kotlin
            // Chat
            Opcode.CHAT_MESSAGE_VALUE -> {
                chatMessageHandler.handle(ctx, player, msg)
            }
```

- [ ] **Step 6: Update WorldServiceModule**

In `WorldServiceModule.kt`:

1. Add imports:
```kotlin
import com.flyagain.world.chat.ChatMessageHandler
import com.flyagain.world.chat.ChatMessageSanitizer
import com.flyagain.world.chat.ChatService
import com.flyagain.world.chat.ChatServiceImpl
```

2. Add after line 90 (after InventoryLockManager):
```kotlin
    single { ChatMessageSanitizer() }
    single<ChatService> { ChatServiceImpl(get(), get(), get()) }
    single { ChatMessageHandler(get(), get()) }
```

3. Add `chatMessageHandler = get(),` to PacketRouter construction (after line 155, `npcShopHandler = get(),`):
```kotlin
            chatMessageHandler = get(),
```

- [ ] **Step 7: Run full test suite**

Run: `cd server && ./gradlew :world-service:test -i`

Expected: All tests PASS (including existing PacketRouter tests — may need to add mockk for new constructor param in PacketRouterTest.kt)

Note: If PacketRouterTest fails due to missing constructor arg, add `private val chatMessageHandler = mockk<ChatMessageHandler>(relaxed = true)` and pass it in the PacketRouter constructor call.

- [ ] **Step 8: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/chat/ChatMessageHandler.kt \
       server/world-service/src/test/kotlin/com/flyagain/world/chat/ChatMessageHandlerTest.kt \
       server/world-service/src/main/kotlin/com/flyagain/world/handler/PacketRouter.kt \
       server/world-service/src/main/kotlin/com/flyagain/world/di/WorldServiceModule.kt
git commit -m "feat(world): add ChatMessageHandler with rate limiting and PacketRouter integration"
```

---

## Task 8: Client Proto Encoder/Decoder + NetworkManager

**Files:**
- Modify: `client/scripts/proto/ProtoEncoder.gd`
- Modify: `client/scripts/proto/ProtoDecoder.gd`
- Modify: `client/autoloads/NetworkManager.gd`

- [ ] **Step 1: Add encode_chat_message to ProtoEncoder.gd**

Add at end of file (before final closing, after the last static method):

```gdscript
## Encodes ChatMessageRequest { int32 channel_type = 1; string text = 2; string target_name = 3 }
static func encode_chat_message(channel_type: int, text: String, target_name: String = "") -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_int32_field(1, channel_type))
	buf.append_array(_string_field(2, text))
	if target_name != "":
		buf.append_array(_string_field(3, target_name))
	return buf
```

- [ ] **Step 2: Add decode_chat_broadcast to ProtoDecoder.gd**

Add before the private helper methods section (before `_has_bytes`, `_next_tag`, etc.):

```gdscript
## Decodes ChatBroadcastMessage { string sender_name=1; int64 sender_entity_id=2;
##   string text=3; int32 channel_type=4; int64 timestamp=5; string target_name=6 }
func decode_chat_broadcast() -> Dictionary:
	var result := {
		"sender_name": "",
		"sender_entity_id": 0,
		"text": "",
		"channel_type": 0,
		"timestamp": 0,
		"target_name": "",
	}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["sender_name"] = _read_string()
			2: result["sender_entity_id"] = _read_varint()
			3: result["text"] = _read_string()
			4: result["channel_type"] = _read_varint()
			5: result["timestamp"] = _read_varint()
			6: result["target_name"] = _read_string()
			_: _skip(wt)
	return result
```

- [ ] **Step 3: Add chat signal and methods to NetworkManager.gd**

1. Add signal after the existing world signals section (around line 60):
```gdscript
signal chat_broadcast_received(data: Dictionary)
```

2. Add send method after the existing send methods (around line 312):
```gdscript
func send_chat_message(channel_type: int, text: String, target_name: String = "") -> void:
	_send_world(PacketProtocol.OPCODE_CHAT_MESSAGE,
		ProtoEncoder.encode_chat_message(channel_type, text, target_name))
```

3. Add dispatch case in `_dispatch_world_frame()` before the `_:` default case (around line 664):
```gdscript
		PacketProtocol.OPCODE_CHAT_BROADCAST:
			var data := ProtoDecoder.new(payload).decode_chat_broadcast()
			chat_broadcast_received.emit(data)
```

- [ ] **Step 4: Verify client loads without errors**

Open the Godot project and check for parse errors in the Output panel.

- [ ] **Step 5: Commit**

```bash
git add client/scripts/proto/ProtoEncoder.gd \
       client/scripts/proto/ProtoDecoder.gd \
       client/autoloads/NetworkManager.gd
git commit -m "feat(client): add chat proto encoding/decoding and NetworkManager integration"
```

---

## Task 9: Translations + GameState Chat Flag

**Files:**
- Modify: `client/translations/translations.csv`
- Modify: `client/autoloads/GameState.gd`

- [ ] **Step 1: Add chat translations**

Append to `translations.csv`:

```csv
CHAT_TITLE,Chat,Chat
CHAT_SAY,Say,Sagen
CHAT_SHOUT,Shout,Rufen
CHAT_ALL,All,Alle
CHAT_WHISPER,Whisper,Flüstern
CHAT_PLACEHOLDER,Press Enter to chat...,Eingabe drücken zum Chatten...
CHAT_INPUT_PLACEHOLDER,Type a message...,Gib eine Nachricht ein...
CHAT_PLAYER_NOT_FOUND,Player not found.,Spieler nicht gefunden.
CHAT_CANNOT_WHISPER_SELF,Cannot whisper to yourself.,Du kannst dir nicht selbst schreiben.
CHAT_RATE_LIMIT,You are sending messages too fast.,Du sendest Nachrichten zu schnell.
CHAT_CLOSE,Close,Schließen
WHISPER_TITLE,Whisper: {name},Flüstern: {name}
```

- [ ] **Step 2: Add chat_input_active flag to GameState.gd**

Add a flag that PlayerCharacter can check to block gameplay input:

```gdscript
## True when the chat input field is focused — blocks gameplay input (WASD, skills, etc.)
var chat_input_active: bool = false
```

- [ ] **Step 3: Commit**

```bash
git add client/translations/translations.csv \
       client/autoloads/GameState.gd
git commit -m "feat(client): add chat translations and chat_input_active flag"
```

---

## Task 10: Main ChatWindow (Client)

**Files:**
- Create: `client/scenes/ui/game_hud/ChatWindow.gd`
- Modify: `client/scenes/game/GameWorld.gd`

- [ ] **Step 1: Implement ChatWindow.gd**

```gdscript
## ChatWindow.gd
## Main chat window showing Say and Shout messages with tab filtering and input.
extends PanelContainer

enum Channel { SAY = 0, SHOUT = 1 }
enum Filter { ALL, SAY, SHOUT }

const MAX_MESSAGES := 200
const COLORS := {
	"say": Color(1.0, 1.0, 1.0),        # white
	"shout": Color(1.0, 1.0, 0.3),      # yellow
	"system": Color(1.0, 0.3, 0.3),     # red
	"name": Color(0.5, 0.8, 1.0),       # light blue
}

var _current_filter: Filter = Filter.ALL
var _messages: Array[Dictionary] = []

var _tab_all: Button
var _tab_say: Button
var _tab_shout: Button
var _message_display: RichTextLabel
var _scroll_container: ScrollContainer
var _input_field: LineEdit
var _vbox: VBoxContainer


func _ready() -> void:
	_build_ui()
	NetworkManager.chat_broadcast_received.connect(_on_chat_broadcast)
	NetworkManager.zone_data_received.connect(_on_zone_changed)


func _build_ui() -> void:
	_vbox = VBoxContainer.new()
	_vbox.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(_vbox)

	# Tab bar
	var tabs := HBoxContainer.new()
	tabs.custom_minimum_size.y = 28
	_vbox.add_child(tabs)

	_tab_all = _make_tab(tr("CHAT_ALL"), Filter.ALL)
	_tab_say = _make_tab(tr("CHAT_SAY"), Filter.SAY)
	_tab_shout = _make_tab(tr("CHAT_SHOUT"), Filter.SHOUT)
	tabs.add_child(_tab_all)
	tabs.add_child(_tab_say)
	tabs.add_child(_tab_shout)
	_update_tab_highlight()

	# Message display
	_scroll_container = ScrollContainer.new()
	_scroll_container.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_scroll_container.follow_focus = false
	_vbox.add_child(_scroll_container)

	_message_display = RichTextLabel.new()
	_message_display.bbcode_enabled = true
	_message_display.scroll_following = true
	_message_display.selection_enabled = true
	_message_display.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_message_display.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_message_display.fit_content = true
	_scroll_container.add_child(_message_display)

	# Input field
	_input_field = LineEdit.new()
	_input_field.placeholder_text = tr("CHAT_PLACEHOLDER")
	_input_field.max_length = 200
	_input_field.custom_minimum_size.y = 32
	_input_field.text_submitted.connect(_on_text_submitted)
	_input_field.focus_entered.connect(_on_input_focused)
	_input_field.focus_exited.connect(_on_input_unfocused)
	_vbox.add_child(_input_field)


func _make_tab(label: String, filter: Filter) -> Button:
	var btn := Button.new()
	btn.text = label
	btn.pressed.connect(func(): _set_filter(filter))
	return btn


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_ENTER or event.keycode == KEY_KP_ENTER:
			if not _input_field.has_focus():
				_input_field.grab_focus()
				get_viewport().set_input_as_handled()
		elif event.keycode == KEY_ESCAPE:
			if _input_field.has_focus():
				_input_field.release_focus()
				get_viewport().set_input_as_handled()


func _on_input_focused() -> void:
	GameState.chat_input_active = true
	_input_field.placeholder_text = tr("CHAT_INPUT_PLACEHOLDER")


func _on_input_unfocused() -> void:
	GameState.chat_input_active = false
	_input_field.placeholder_text = tr("CHAT_PLACEHOLDER")


func _on_text_submitted(text: String) -> void:
	_input_field.clear()
	var trimmed := text.strip_edges()
	if trimmed.is_empty():
		return
	_parse_and_send(trimmed)


func _parse_and_send(text: String) -> void:
	if text.begins_with("/shout "):
		var msg := text.substr(7).strip_edges()
		if not msg.is_empty():
			NetworkManager.send_chat_message(Channel.SHOUT, msg)
	elif text.begins_with("/say "):
		var parts := text.substr(5).strip_edges()
		var space_idx := parts.find(" ")
		if space_idx > 0:
			var target := parts.substr(0, space_idx)
			var msg := parts.substr(space_idx + 1).strip_edges()
			if not target.is_empty() and not msg.is_empty():
				NetworkManager.send_chat_message(2, msg, target)  # 2 = whisper
		# No message after name — ignore
	else:
		NetworkManager.send_chat_message(Channel.SAY, text)


func _on_chat_broadcast(data: Dictionary) -> void:
	var channel_type: int = data.get("channel_type", 0)
	# Only show say (0) and shout (1) in main window
	# whisper_in (2) and whisper_out (3) go to WhisperManager
	if channel_type >= 2:
		return

	_add_message(data)


func _add_message(data: Dictionary) -> void:
	_messages.append(data)
	if _messages.size() > MAX_MESSAGES:
		_messages.pop_front()
	_refresh_display()


func _on_zone_changed(_data: Dictionary) -> void:
	_messages.clear()
	_message_display.clear()


func _set_filter(filter: Filter) -> void:
	_current_filter = filter
	_update_tab_highlight()
	_refresh_display()


func _update_tab_highlight() -> void:
	_tab_all.button_pressed = _current_filter == Filter.ALL
	_tab_say.button_pressed = _current_filter == Filter.SAY
	_tab_shout.button_pressed = _current_filter == Filter.SHOUT


func _refresh_display() -> void:
	_message_display.clear()
	for msg in _messages:
		var ct: int = msg.get("channel_type", 0)
		if _current_filter == Filter.SAY and ct != 0:
			continue
		if _current_filter == Filter.SHOUT and ct != 1:
			continue
		_append_formatted(msg)


func _append_formatted(data: Dictionary) -> void:
	var ct: int = data.get("channel_type", 0)
	var sender: String = data.get("sender_name", "")
	var text: String = data.get("text", "")

	var channel_label: String
	var channel_color: Color
	match ct:
		0:
			channel_label = "[Say]"
			channel_color = COLORS["say"]
		1:
			channel_label = "[Shout]"
			channel_color = COLORS["shout"]
		_:
			channel_label = "[System]"
			channel_color = COLORS["system"]

	var name_hex := COLORS["name"].to_html(false)
	var ch_hex := channel_color.to_html(false)
	_message_display.append_text(
		"[color=#%s]%s[/color] [color=#%s]%s[/color]: %s\n" % [
			ch_hex, channel_label, name_hex, sender, text
		])
```

- [ ] **Step 2: Add ChatWindow to GameWorld HUD setup**

In `GameWorld.gd`, in `_setup_hud()`, add after the notification stack section (around line 306):

First, add the preload constant at the top of GameWorld.gd (near the other const preloads, around line 14):
```gdscript
const ChatWindowScr := preload("res://scenes/ui/game_hud/ChatWindow.gd")
```

Then in `_setup_hud()`:
```gdscript
	# Chat window — bottom-left, draggable, resizable, not closable
	var chat_window := PanelContainer.new()
	chat_window.set_script(GameWindowScript)
	chat_window.call("setup", "chat", tr("CHAT_TITLE"), {
		"draggable": true, "resizable": true,
		"minimizable": true, "closable": false,
		"default_position": Vector2(10, 700),
		"default_size": Vector2(450, 280),
		"min_size": Vector2(300, 200),
		"max_size": Vector2(700, 500),
	})
	_hud_root.add_child(chat_window)
	var chat_content := PanelContainer.new()
	chat_content.set_script(ChatWindowScr)
	chat_window.call("set_content", chat_content)
```

- [ ] **Step 3: Block gameplay input when chat is active**

In `PlayerCharacter.gd`, add the guard to **both** input methods:

1. At the top of `_unhandled_input(event)` (handles mouse clicks, F key, Escape, Tab targeting):
```gdscript
	if GameState.chat_input_active:
		return
```

2. At the top of `_physics_process(delta)` (handles WASD movement):
```gdscript
	if GameState.chat_input_active:
		return
```

Both guards are needed — `_unhandled_input` handles discrete actions (targeting, skills), `_physics_process` handles continuous movement (WASD).

- [ ] **Step 4: Test in editor**

Open the Godot project, enter the game world, verify:
- Chat window appears at bottom-left
- Enter focuses the input field
- Escape unfocuses
- WASD is blocked while typing
- Tab buttons switch filter (visual only — no server messages yet)

- [ ] **Step 5: Commit**

```bash
git add client/scenes/ui/game_hud/ChatWindow.gd \
       client/scenes/game/GameWorld.gd \
       client/scenes/game/PlayerCharacter.gd
git commit -m "feat(client): add main ChatWindow with tab filtering and input blocking"
```

---

## Task 11: WhisperWindow + WhisperManager (Client)

**Files:**
- Create: `client/scenes/ui/game_hud/WhisperWindow.gd`
- Create: `client/scenes/ui/game_hud/WhisperManager.gd`
- Modify: `client/scenes/game/GameWorld.gd`

- [ ] **Step 1: Implement WhisperWindow.gd**

```gdscript
## WhisperWindow.gd
## Small chat window for a single whisper conversation.
extends PanelContainer

signal whisper_sent(target_name: String, text: String)
signal window_closed(target_name: String)

const COLORS := {
	"incoming": Color(1.0, 0.4, 1.0),   # magenta
	"outgoing": Color(0.8, 0.6, 1.0),   # light purple
	"name": Color(0.5, 0.8, 1.0),       # light blue
}

var target_name: String = ""
var last_activity: float = 0.0

var _message_display: RichTextLabel
var _input_field: LineEdit
var _vbox: VBoxContainer


func setup(p_target_name: String) -> void:
	target_name = p_target_name
	last_activity = Time.get_ticks_msec() / 1000.0


func _ready() -> void:
	_build_ui()


func _build_ui() -> void:
	_vbox = VBoxContainer.new()
	_vbox.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(_vbox)

	_message_display = RichTextLabel.new()
	_message_display.bbcode_enabled = true
	_message_display.scroll_following = true
	_message_display.selection_enabled = true
	_message_display.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_message_display.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_message_display.fit_content = true
	_vbox.add_child(_message_display)

	_input_field = LineEdit.new()
	_input_field.placeholder_text = tr("CHAT_INPUT_PLACEHOLDER")
	_input_field.max_length = 200
	_input_field.custom_minimum_size.y = 28
	_input_field.text_submitted.connect(_on_text_submitted)
	_input_field.focus_entered.connect(func(): GameState.chat_input_active = true)
	_input_field.focus_exited.connect(func(): GameState.chat_input_active = false)
	_vbox.add_child(_input_field)


func add_incoming(sender_name: String, text: String) -> void:
	last_activity = Time.get_ticks_msec() / 1000.0
	var name_hex := COLORS["name"].to_html(false)
	var color_hex := COLORS["incoming"].to_html(false)
	_message_display.append_text(
		"[color=#%s][color=#%s]%s[/color]: %s[/color]\n" % [
			color_hex, name_hex, sender_name, text])


func add_outgoing(text: String) -> void:
	last_activity = Time.get_ticks_msec() / 1000.0
	var name_hex := COLORS["name"].to_html(false)
	var color_hex := COLORS["outgoing"].to_html(false)
	var my_name: String = _get_my_name()
	_message_display.append_text(
		"[color=#%s][color=#%s]%s[/color]: %s[/color]\n" % [
			color_hex, name_hex, my_name, text])


func _get_my_name() -> String:
	for c in GameState.characters:
		if c.get("id", "") == GameState.selected_character_id:
			return c.get("name", "You")
	return "You"


func _on_text_submitted(text: String) -> void:
	_input_field.clear()
	var trimmed := text.strip_edges()
	if trimmed.is_empty():
		return
	whisper_sent.emit(target_name, trimmed)
```

- [ ] **Step 2: Implement WhisperManager.gd**

```gdscript
## WhisperManager.gd
## Manages whisper windows (max 5 simultaneous).
## Attaches to HUD root, creates GameWindow-wrapped WhisperWindows on demand.
extends Node

const MAX_WINDOWS := 5
const WhisperWindowScript := preload("res://scenes/ui/game_hud/WhisperWindow.gd")
const GameWindowScript := preload("res://scenes/ui/window_system/GameWindow.gd")

var _windows: Dictionary = {}  # { lowercase_name: { "game_window": GameWindow, "whisper": WhisperWindow } }
var _hud_root: Control = null
var _window_offset: int = 0


func initialize(hud_root: Control) -> void:
	_hud_root = hud_root
	NetworkManager.chat_broadcast_received.connect(_on_chat_broadcast)


func _on_chat_broadcast(data: Dictionary) -> void:
	var channel_type: int = data.get("channel_type", 0)
	if channel_type == 2:  # whisper_in
		var sender: String = data.get("sender_name", "")
		if sender.is_empty():
			return
		var win := _open_or_get(sender)
		win.add_incoming(sender, data.get("text", ""))
	elif channel_type == 3:  # whisper_out (server echo of our sent whisper)
		var target: String = data.get("target_name", "")
		if target.is_empty():
			return
		var win := _open_or_get(target)
		win.add_outgoing(data.get("text", ""))


func _open_or_get(player_name: String) -> WhisperWindow:
	var key := player_name.to_lower()
	if _windows.has(key):
		var entry: Dictionary = _windows[key]
		entry["game_window"].visible = true
		return entry["whisper"]

	# Evict oldest if at limit
	if _windows.size() >= MAX_WINDOWS:
		_evict_oldest()

	# Create new whisper window wrapped in GameWindow
	var game_win := PanelContainer.new()
	game_win.set_script(GameWindowScript)
	_window_offset = (_window_offset + 1) % 5
	var x_pos := 480 + _window_offset * 30
	var y_pos := 400 + _window_offset * 20
	game_win.call("setup", "whisper_" + key, tr("WHISPER_TITLE").replace("{name}", player_name), {
		"draggable": true, "resizable": true,
		"minimizable": true, "closable": true,
		"default_position": Vector2(x_pos, y_pos),
		"default_size": Vector2(320, 250),
		"min_size": Vector2(250, 180),
		"max_size": Vector2(500, 400),
	})
	_hud_root.add_child(game_win)

	var whisper_content := PanelContainer.new()
	whisper_content.set_script(WhisperWindowScript)
	game_win.call("set_content", whisper_content)
	whisper_content.call("setup", player_name)
	whisper_content.whisper_sent.connect(_on_whisper_sent)
	game_win.window_closed.connect(_on_window_closed)

	_windows[key] = {"game_window": game_win, "whisper": whisper_content}
	return whisper_content


func _evict_oldest() -> void:
	var oldest_key: String = ""
	var oldest_time: float = INF
	for key in _windows:
		var entry: Dictionary = _windows[key]
		var whisper: WhisperWindow = entry["whisper"]
		if whisper.last_activity < oldest_time:
			oldest_time = whisper.last_activity
			oldest_key = key
	if oldest_key != "":
		var entry: Dictionary = _windows[oldest_key]
		entry["game_window"].queue_free()
		_windows.erase(oldest_key)


func _on_whisper_sent(target_name: String, text: String) -> void:
	NetworkManager.send_chat_message(2, text, target_name)


func _on_window_closed(window_id: String) -> void:
	# window_id is "whisper_playername"
	var key := window_id.replace("whisper_", "")
	if _windows.has(key):
		_windows[key]["game_window"].queue_free()
		_windows.erase(key)
```

- [ ] **Step 3: Integrate WhisperManager into GameWorld**

In `GameWorld.gd`:

1. Add variable after existing HUD vars (around line 58):
```gdscript
var _whisper_manager = null  # WhisperManager
```

2. In `_setup_hud()`, after the chat window section, add:
```gdscript
	# Whisper manager
	var WhisperManagerScript := preload("res://scenes/ui/game_hud/WhisperManager.gd")
	_whisper_manager = Node.new()
	_whisper_manager.set_script(WhisperManagerScript)
	_hud_root.add_child(_whisper_manager)
	_whisper_manager.initialize(_hud_root)
```

3. No additional wiring needed for outgoing whispers — the server echoes `whisper_out` (channel_type=3) with `target_name` included, and WhisperManager handles it via `_on_chat_broadcast`.

- [ ] **Step 4: Test in editor**

Verify:
- Whisper windows appear when receiving whisper_in broadcasts
- Sending `/say Name msg` opens a whisper window with the outgoing message
- Max 5 windows, oldest is evicted
- Close button removes window
- Input blocking works in whisper windows too

- [ ] **Step 5: Commit**

```bash
git add client/scenes/ui/game_hud/WhisperWindow.gd \
       client/scenes/ui/game_hud/WhisperManager.gd \
       client/scenes/game/GameWorld.gd \
       client/scenes/ui/game_hud/ChatWindow.gd
git commit -m "feat(client): add WhisperWindow and WhisperManager with max 5 window limit"
```

---

## Task 12: Server Build Verification + Full Test Suite

**Files:** None (verification only)

- [ ] **Step 1: Run full server build with tests**

Run: `cd server && ./gradlew clean build`

Expected: BUILD SUCCESSFUL — all tests pass

- [ ] **Step 2: Fix any failures**

If `PacketRouterTest` fails due to the new constructor parameter, add the mock and pass it.

If `WorldServiceModuleTest` (Koin verification) fails, ensure all new beans are properly registered.

- [ ] **Step 3: Verify test count**

Run: `cd server && ./gradlew :world-service:test -i 2>&1 | tail -5`

Expected: Test count should have increased by ~30+ (sanitizer: ~14, service: ~8, handler: ~8, entity manager: +4)

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve test failures from chat system integration"
```

---

## Task 13: Update IMPLEMENTATION_PHASES.md

**Files:**
- Modify: `docs/IMPLEMENTATION_PHASES.md:343-361`

- [ ] **Step 1: Update Phase 1.7 checkboxes**

Mark all server and client items as `[x]` in the Phase 1.7 section, and update the "Naechster Schritt" section at the bottom.

- [ ] **Step 2: Commit**

```bash
git add docs/IMPLEMENTATION_PHASES.md
git commit -m "docs: update Phase 1.7 status in IMPLEMENTATION_PHASES.md"
```
