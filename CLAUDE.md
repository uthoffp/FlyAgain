# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FlyAgain is a Flyff-inspired MMORPG with a Unity (C#) client, Kotlin (Netty) microservice server, and PostgreSQL + Redis persistence. The project is currently in early development (Phase 1). See docs/GDD.md for game design, docs/ARCHITECTURE.md for full technical spec, and docs/IMPLEMENTATION_PHASES.md for the phased build plan.

## Tech Stack

- **Client:** Unity 2022 LTS with URP (C#)
- **Server:** Kotlin with Netty (TCP/TLS 1.3 + UDP), Gradle multi-project build
- **DI:** Koin 4.0 (module per service, verified via tests)
- **Inter-Service:** gRPC (protobuf-based)
- **Database:** PostgreSQL + Redis (via Docker Compose)
- **Serialization:** Protocol Buffers (shared `.proto` definitions)
- **DB Migrations:** Flyway

## Unity Client Guidelines

- **Input System:** ALWAYS use Unity's new Input System (`UnityEngine.InputSystem`), NOT the legacy Input Manager (`UnityEngine.Input`)
  - Use `Keyboard.current` to access keyboard input (e.g., `Keyboard.current.tabKey.wasPressedThisFrame`)
  - Use `Mouse.current` for mouse input
  - Never use `Input.GetKeyDown()`, `Input.GetKey()`, etc. - these will throw `InvalidOperationException`
  - Input actions are defined in `client/Assets/Settings/FlyAgainInputActions.inputactions`
  - Generated C# class: `FlyAgain.Input.FlyAgainInputActions`

## MMO Development Best Practices

### Server-Side Authority
- **NEVER trust client input** — validate ALL actions server-side (movement, combat, inventory, trading)
- **Client is a dumb renderer** — all game logic and state management happens on the server
- Client-side prediction for movement only (rollback on server correction)
- Anti-cheat: validate timestamps, rate-limit actions, detect impossible movements/actions

### Performance & Scalability
- **Interest management:** Only send updates for entities within player's area of interest (use SpatialGrid)
- **Network optimization:** Batch updates, compress data, use delta compression for state changes
- **Async I/O:** Use Kotlin Coroutines for all I/O operations (database, network, Redis)
- **Connection pooling:** Reuse database connections, maintain connection pools
- **Lazy loading:** Load only necessary data (don't load entire character inventory on login)
- **Horizontal scaling:** Design services to be stateless where possible for easy scaling

### Security
- **Input validation:** Sanitize and validate ALL client input (SQL injection, XSS, buffer overflows)
- **Rate limiting:** Implement per-connection rate limits for all packet types
- **Session management:** JWT tokens for auth, server-side session validation, detect multi-login
- **Encryption:** TLS 1.3 for TCP, HMAC-SHA256 for UDP packet authentication
- **Password security:** bcrypt with cost 12 minimum, never store plaintext passwords
- **SQL injection prevention:** Use parameterized queries/prepared statements ONLY
- **Logging:** Log suspicious activities (failed logins, unusual packet patterns, exploit attempts)

### Network Protocol Design
- **Opcodes:** Use clear, typed opcodes for all messages (see `shared/proto/flyagain.proto`)
- **Protocol Buffers:** Use protobuf for serialization (compact, typed, versioned)
- **TCP vs UDP:** TCP for reliable operations (auth, inventory, chat), UDP for real-time (movement, combat)
- **Packet size:** Keep packets small (<1400 bytes for UDP to avoid fragmentation)
- **Heartbeats:** Implement periodic heartbeats to detect disconnections

### Database & Persistence
- **Write-back caching:** RAM → Redis (60s) → PostgreSQL (5min + on logout/zone change)
- **Batch writes:** Group database writes to reduce I/O load
- **Transactions:** Use database transactions for multi-step operations (e.g., trading)
- **Indexes:** Index frequently queried columns (player_id, zone_id, account_id)
- **Connection pooling:** Use HikariCP or similar for connection management
- **Migrations:** Use Flyway for versioned database schema changes

### Code Quality
- **Dependency Injection:** Use Koin 4.0 for all service dependencies, verify via tests
- **Repository pattern:** Separate data access logic (interface + implementation)
- **Error handling:** Graceful degradation, log errors, send meaningful error messages to client
- **Testing:** Unit tests for business logic, integration tests for database/network operations
- **Code organization:** Clear separation of concerns (network, business logic, persistence)

### Game Loop & Timing
- **Fixed tick rate:** 20 Hz server tick for consistent game state updates
- **Single-threaded game loop:** Avoid concurrency issues in core game logic
- **Delta time:** Use fixed delta time for game logic, variable for rendering (client)
- **Tick budget:** Monitor tick execution time, warn if exceeding budget (50ms for 20 Hz)

## Monorepo Structure

```
server/                    # Kotlin multi-project Gradle build
  common/                  # Shared: network layer (TcpServer, Packet, Codec, ConnectionLimiter),
                           #   Protobuf/gRPC stubs, Redis client, ConfigHelper
  database-service/        # gRPC :9090 — Repository interfaces + impls, Flyway, write-back
    src/.../di/            #   Koin DI module
    src/.../repository/    #   Interface/Impl pattern + BaseRepository
  login-service/           # TCP :7777 — auth, registration, JWT, sessions, rate-limiting
    src/.../di/            #   Koin DI module
  account-service/         # TCP :7779 — character CRUD, JWT validation
    src/.../di/            #   Koin DI module
  world-service/           # TCP :7780 + UDP :7781 — gameplay, 20Hz loop, zones, combat, AI
    src/.../di/            #   Koin DI module
  gradle/libs.versions.toml # Central version catalog (incl. Koin 4.0)
client/                    # Unity client project (URP)
shared/proto/              # Shared Protocol Buffer definitions (.proto)
  flyagain.proto           # Client-facing messages and opcodes
  internal.proto           # gRPC service definitions for inter-service communication
scripts/                   # Build and codegen scripts
docs/                      # Design docs, architecture, setup guides
docker-compose.yml         # PostgreSQL + Redis + all 4 services
```

The server is a Gradle multi-project build. All Gradle files live inside `server/`. Build all services: `cd server && ./gradlew build`. Build a single service: `./gradlew :login-service:build`.

## Architecture Essentials

- **Server-authoritative** — all game state validated server-side; client is a dumb renderer with prediction
- **Dual-stack networking:** TCP for reliable ops (auth, inventory, chat), UDP for real-time (movement, combat)
- **20 Hz server tick** — single-threaded game loop with async I/O (Kotlin Coroutines)
- **Write-back persistence:** RAM → Redis (60s) → PostgreSQL (5min + on logout/zone change)
- **Interest management:** SpatialGrid (50×50 units) to limit network fan-out
- **Zone/Channel system:** max 1,000 players per channel, auto channel creation
- **Monster AI:** State machine (IDLE → AGGRO → ATTACK → RETURN)
- **Target scale:** 5,000 CCU, 10,000 accounts

## Development Phases (Roadmap)

1. **Phase 1 — Minimal MVP:** Movement, basic combat, one class, multiplayer
2. **Phase 2 — Core Gameplay:** 4 classes, equipment, dungeons, quests
3. **Phase 3 — PvP & Social:** Guilds, arena, ranking, trading
4. **Phase 4 — Expansion:** Job specializations, more zones, crafting
5. **Phase 5 — Polish & Launch:** Balancing, anti-cheat hardening, beta

Phase 1 is broken into 8 sub-phases in docs/IMPLEMENTATION_PHASES.md with detailed acceptance criteria.

## Key Design Principles

- **NO Pay-to-Win** — fair play, skill and time investment only
- **German-themed naming** — classes (Krieger, Magier, Assassine, Kleriker), zones (Aerheim, Grüne Ebene, Dunkler Wald)
- **Security-first** — bcrypt (cost 12), JWT + session tokens, HMAC-SHA256 for UDP, rate limiting, multi-login prevention, comprehensive server-side input validation
