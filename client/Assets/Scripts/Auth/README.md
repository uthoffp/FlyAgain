# Authentication System

## Overview

The authentication system connects the Unity client to the Kotlin login server via TCP on port 7777.

## Architecture

### Components

1. **LoginScreen** ([LoginScreen.cs](../UI/Screens/LoginScreen.cs))
   - UI with username/password fields
   - Fires `OnLoginRequested` event when user submits
   - Shows success/error messages using `ShowSuccess()` and `ShowError()`
   - Supports Tab navigation and Enter to submit

2. **AuthController** ([AuthController.cs](Auth/AuthController.cs))
   - Subscribes to LoginScreen events
   - Manages network connection via NetworkManager
   - Sends LoginRequest protobuf message to server
   - Handles LoginResponse and displays results
   - Also handles registration flow

3. **NetworkManager** ([NetworkManager.cs](../Network/NetworkManager.cs))
   - Singleton that manages TCP/UDP connections
   - Default login server: `127.0.0.1:7777`
   - Handles packet serialization, heartbeat, reconnection
   - Stores session data (JWT, HMAC secret)

4. **TcpConnection** ([TcpConnection.cs](../Network/TcpConnection.cs))
   - Length-prefixed framing: `[4-byte length][2-byte opcode][protobuf payload]`
   - Background thread for reading packets
   - Thread-safe packet queue for main thread processing

## Flow

```
User enters credentials
    ↓
LoginScreen.OnLoginRequested event fires
    ↓
AuthController.HandleLoginRequest()
    ↓
NetworkManager.ConnectToLogin() → TCP connect to 127.0.0.1:7777
    ↓
NetworkManager.SendTcp(LOGIN_REQUEST, LoginRequest protobuf)
    ↓
Server processes → sends LoginResponse
    ↓
NetworkManager receives packet → queues for main thread
    ↓
AuthController.HandleLoginResponse()
    ↓
If success: Store JWT/HMAC, show success message
If failure: Show error message from server
```

## Protocol

### LoginRequest (Opcode 0x0001)
```protobuf
message LoginRequest {
    string username = 1;
    string password = 2;
}
```

### LoginResponse (Opcode 0x0002)
```protobuf
message LoginResponse {
    bool success = 1;
    string jwt = 2;
    repeated CharacterInfo characters = 3;
    string error_message = 4;
    string hmac_secret = 5;
    string account_service_host = 6;
    int32 account_service_port = 7;
}
```

## Testing

### Prerequisites
1. Start the Kotlin login-service on port 7777:
   ```bash
   cd server
   ./gradlew :login-service:run
   ```

2. Ensure PostgreSQL and Redis are running:
   ```bash
   docker-compose up -d postgres redis
   ```

### Test in Unity
1. Open Unity and enter Play mode
2. Login screen appears automatically (UIBootstrap)
3. Enter valid credentials
4. Click Login or press Enter
5. Success message appears in green if login succeeds
6. Error message appears in red if login fails

### Expected Logs
```
[AuthController] Login requested for user: testuser
[TCP] Connected to 127.0.0.1:7777
[AuthController] Login request sent
[AuthController] Login response received. Success: True
[AuthController] Login successful! JWT: eyJhbGciOiJIUzI1NiI...
[AuthController] Characters: 0
[AuthController] Account service: 127.0.0.1:7779
```

## Network Configuration

Default settings can be changed in NetworkManager inspector:
- **Login Host**: `127.0.0.1` (localhost)
- **Login Port**: `7777`
- **Connect Timeout**: `5000ms`
- **Heartbeat Interval**: `5 seconds`
- **Max Reconnect Attempts**: `3`
- **Reconnect Delay**: `2 seconds`

## Security

- Passwords are sent over TCP (should use TLS 1.3 in production)
- Server validates credentials and returns JWT token
- JWT and HMAC secret stored in NetworkManager for subsequent requests
- Session tokens used for world service connection

## Next Steps

- [ ] Implement character selection screen
- [ ] Handle multiple characters returned from server
- [ ] Connect to account-service with JWT
- [ ] Navigate to world service after character selection
