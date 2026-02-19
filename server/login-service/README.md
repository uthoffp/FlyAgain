# login-service

TCP authentication gateway — the first service a client connects to.

## Responsibilities

- **Authentication** — Validates credentials via gRPC to database-service, verifies bcrypt password hashes.
- **Registration** — Validates username/email/password input, hashes password (bcrypt cost 12), creates account via gRPC.
- **JWT issuance** — Signs HMAC-SHA256 JWTs containing account ID, username, and session ID.
- **Session management** — Creates sessions in Redis with HMAC secrets for UDP signing; handles multi-login prevention by invalidating old sessions.
- **Rate limiting** — Redis-based per-IP rate limiting (5 login attempts/60s, 3 registrations/hour).
- **Connection limiting** — Enforces max total and per-IP connection caps.

## Architecture

```
Client ──TCP:7777──▶ Netty Pipeline
                      ├─ ConnectionLimiter
                      ├─ IdleStateHandler (60s)
                      ├─ LengthFieldBasedFrameDecoder (4-byte prefix)
                      ├─ PacketDecoder / PacketEncoder
                      └─ PacketRouter
                           ├─ LoginHandler  ──gRPC──▶ database-service
                           └─ RegisterHandler ──gRPC──▶ database-service
```

On successful login the client receives a JWT, character list, HMAC secret, and the account-service endpoint to connect to next.

## Wire Format

```
[4-byte length][2-byte opcode][protobuf payload]
```

## Configuration

See `src/main/resources/application.conf`. Key settings: `flyagain.network.tcp-port` (default 7777), `flyagain.auth.jwt-secret`, `flyagain.auth.bcrypt-cost`.

## Build & Run

```bash
cd server && ./gradlew :login-service:build
./gradlew :login-service:run
```
