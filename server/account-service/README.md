# account-service

Character management gateway — clients connect here after authenticating with login-service.

## Responsibilities

- **JWT validation** — Verifies tokens issued by login-service (HMAC-SHA256, issuer `flyagain-login`).
- **Character creation** — Validates name (2–16 chars, German-letter-friendly regex) and class (krieger, magier, assassine, kleriker), then delegates to database-service via gRPC.
- **Character selection** — Loads character data from database-service, caches it in Redis for world-service pickup, and responds with the world-service endpoint.

## Architecture

```
Client ──TCP:7779──▶ Netty Pipeline
                      ├─ ConnectionLimiter
                      ├─ IdleStateHandler (60s)
                      ├─ LengthFieldBasedFrameDecoder (4-byte prefix)
                      ├─ PacketDecoder / PacketEncoder
                      └─ PacketRouter (JWT-gated)
                           ├─ CharacterCreateHandler ──gRPC──▶ database-service
                           └─ CharacterSelectHandler ──gRPC──▶ database-service
                                                      ──Redis──▶ char:{id} cache
```

After character selection the client receives position, stats, and the world-service host/port to connect to next.

## Wire Format

```
[4-byte length][2-byte opcode][protobuf payload]
```

## Configuration

See `src/main/resources/application.conf`. Key settings: `flyagain.network.tcp-port` (default 7779), `flyagain.auth.jwt-secret`, `flyagain.service-endpoints.world-service-*`.

## Build & Run

```bash
cd server && ./gradlew :account-service:build
./gradlew :account-service:run
```
