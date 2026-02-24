# FlyAgain Client

Godot 4.6 (GDScript) client for the FlyAgain MMORPG. Currently implements Phase 1: authentication, registration, and character management.

## Requirements

- Godot 4.6+
- Running FlyAgain server services (login-service on port 7777, account-service on port 7779)

## Getting Started

1. Open the `client/` folder as a Godot project
2. Ensure the server services are running (see root `docker-compose.yml`)
3. Press F5 or click **Play** — the LoginScreen loads as the main scene

The client connects to `127.0.0.1:7777` (login-service) by default.

## Project Structure

```
client/
├── autoloads/                      # Global singletons (registered in project.godot)
│   ├── NetworkManager.gd           # TCP connection, packet I/O, reconnection
│   ├── GameState.gd                # Session data (JWT, characters, service endpoints)
│   └── UIManager.gd                # Screen navigation with fade transitions
├── scenes/
│   └── ui/
│       ├── components/             # Reusable UI components
│       │   ├── FlyButton.tscn/gd   # Styled button (PRIMARY / SECONDARY variants)
│       │   ├── FlyLineEdit.tscn/gd # Labeled text input
│       │   ├── StatusLabel.tscn/gd # Color-coded feedback messages
│       │   └── LoadingSpinner.tscn/gd  # Animated dot spinner
│       └── screens/                # Full-screen UI scenes
│           ├── LoginScreen.tscn/gd
│           ├── RegisterScreen.tscn/gd
│           ├── CharacterSelectScreen.tscn/gd
│           └── CharacterCreateScreen.tscn/gd
├── scripts/
│   ├── network/
│   │   └── PacketProtocol.gd       # Opcode constants, TCP frame building
│   └── proto/
│       ├── ProtoEncoder.gd         # Manual protobuf3 encoding
│       └── ProtoDecoder.gd         # Manual protobuf3 decoding
├── themes/
│   ├── Colors.gd                   # Central color palette
│   └── ThemeFactory.gd             # Builds the shared Theme resource
└── project.godot                   # Engine config (1280×720, Forward Plus)
```

## Architecture

### Autoloads

| Singleton | Purpose |
|-----------|---------|
| **NetworkManager** | Manages TCP connection to the server. Handles frame reassembly (4-byte length prefix + 2-byte opcode + payload), automatic reconnection (3 attempts, 2s delay), and heartbeat (5s interval). Emits signals for login/register/error/enter-world responses. |
| **GameState** | Stores session state across scene transitions: JWT token, HMAC secret, character list, and service endpoint redirects (account-service, world-service). |
| **UIManager** | Screen navigation with a stack-based history and 0.2s fade transitions. Screens are registered by name (`login`, `register`, `char_select`, `char_create`). |

### Network Protocol

The client uses a custom TCP binary protocol:

```
┌──────────────────┬────────────┬──────────────┐
│ 4 bytes: length  │ 2 bytes:   │ N bytes:     │
│ (big-endian)     │ opcode     │ protobuf     │
└──────────────────┴────────────┴──────────────┘
```

- **Length** = 2 (opcode) + payload size (excludes the 4-byte prefix itself)
- **Max frame:** 65,535 bytes
- **Encoding:** Manual protobuf3 via `ProtoEncoder.gd` / `ProtoDecoder.gd` (no codegen)

#### Opcodes (Phase 1)

| Code | Name | Direction |
|------|------|-----------|
| `0x0001` | LOGIN_REQUEST | Client → Server |
| `0x0002` | LOGIN_RESPONSE | Server → Client |
| `0x0003` | CHARACTER_SELECT | Client → Server |
| `0x0004` | ENTER_WORLD | Server → Client |
| `0x0005` | CHARACTER_CREATE | Client → Server |
| `0x0006` | REGISTER_REQUEST | Client → Server |
| `0x0007` | REGISTER_RESPONSE | Server → Client |
| `0x0601` | HEARTBEAT | Bidirectional |
| `0x0602` | SERVER_MESSAGE | Server → Client |
| `0x0603` | ERROR_RESPONSE | Server → Client |

Additional opcode ranges (movement, combat, entity, inventory, chat, zone) are reserved for later phases.

### Theme System

All UI styling is centralized:

- **Colors.gd** — HSL-based color palette (dark blue backgrounds, gold accents, light beige text)
- **ThemeFactory.gd** — Builds a single `Theme` resource applied to the root Control; all children inherit it

Call `ThemeFactory.create_main_theme()` once and assign it. Themed controls: `PanelContainer`, `Button`, `LineEdit`, `Label`, `RichTextLabel`, `CheckBox`.

### UI Components

| Component | Description |
|-----------|-------------|
| **FlyButton** | Gold-themed button with `PRIMARY` (filled) and `SECONDARY` (outlined) variants |
| **FlyLineEdit** | Label + LineEdit combo with password mode and max-length support |
| **StatusLabel** | Shows color-coded messages: `show_error()` (red), `show_success()` (green), `show_info()` (blue) |
| **LoadingSpinner** | Animated dot text (`Verbinde.` → `Verbinde..` → `Verbinde...`) |

## User Flows

### Login

1. Client connects to login-service (`127.0.0.1:7777`)
2. User enters username (min 3 chars) and password (min 8 chars)
3. On success: JWT, character list, and account-service endpoint are stored in GameState
4. Client reconnects to account-service and navigates to Character Select

### Registration

1. User fills username (3–16 chars, alphanumeric + hyphens), email, password (min 8), and confirm password
2. Client-side validation (regex for username/email, password match)
3. On success: message shown, auto-return to Login after 1.5s

### Character Select

- Displays up to 3 character slots from GameState
- Each slot shows name, class, level, and class description
- "Spielen" button sends CHARACTER_SELECT to account-service
- "Erstellen" button navigates to Character Create
- Logout resets state and returns to Login

### Character Create

- Name input (3–16 chars) + class picker grid (Warrior, Mage, Assassin, Cleric)
- Selected class shows description; 10-second server response timeout
- On success: returns to Character Select

## Localization

All user-facing strings are in German (de) by default, with English (en) support planned. No hardcoded strings in UI — text constants are defined in each screen script.

## Configuration

Key constants in `NetworkManager.gd`:

| Constant | Value | Description |
|----------|-------|-------------|
| `DEFAULT_HOST` | `127.0.0.1` | Login-service host |
| `DEFAULT_PORT` | `7777` | Login-service port |
| `MAX_RECONNECT_ATTEMPTS` | `3` | Reconnect retries |
| `RECONNECT_DELAY_SEC` | `2.0` | Delay between retries |
| `CONNECT_TIMEOUT_SEC` | `10.0` | Connection timeout |
| `HEARTBEAT_INTERVAL_SEC` | `5.0` | Heartbeat interval |
| `MAX_FRAME_BYTES` | `65535` | Max TCP frame size |

## Current Phase

**Phase 1** — Authentication and character management are complete. World loading (Phase 1.4), movement/entities (Phase 1.5), and gameplay systems (Phase 2.0+) are not yet implemented.
