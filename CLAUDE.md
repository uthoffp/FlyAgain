# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FlyAgain is an original MMORPG with a Godot 4 (GDScript) client, Kotlin (Netty) microservice server, and PostgreSQL + Redis persistence. The project is currently in early development (Phase 1). See docs/GDD.md for game design, docs/ARCHITECTURE.md for full technical spec, and docs/IMPLEMENTATION_PHASES.md for the phased build plan.

## General Rules

- **No "Flyff" references** — The name "Flyff" must NEVER appear in code, comments, variable names, UI texts, documentation, or any other project file. FlyAgain is an original game.
- **Localization:** All user-facing texts (UI labels, error messages, tooltips, etc.) must be localized in both **English (en)** and **German (de)**. No hardcoded strings in UI.
- **Best practices always** — Apply industry best practices in every decision: clean architecture, SOLID principles, security-first, performance-aware, and maintainable code.
- **Scalability & future-proofing** — Design every component to scale horizontally and to accommodate future requirements without major rewrites. Prefer extensible abstractions over quick fixes.

## Tech Stack

- **Client:** Godot 4 (GDScript)
- **Server:** Kotlin with Netty (TCP/TLS 1.3 + UDP), Gradle multi-project build
- **DI:** Koin 4.0 (module per service, verified via tests)
- **Inter-Service:** gRPC (protobuf-based)
- **Database:** PostgreSQL + Redis (via Docker Compose)
- **Serialization:** Protocol Buffers (shared `.proto` definitions)
- **DB Migrations:** Flyway

## Godot Client Guidelines

- **Engine:** Godot 4 (GDScript), project root at `client/`
- **Scenes:** UI scenes at `client/scenes/ui/`, game scenes at `client/scenes/game/`
- **Autoloads:** Global singletons at `client/autoloads/` (NetworkManager, GameState)
- **Scripts:** Logic scripts at `client/scripts/` (network/, proto/)
- **Themes:** UI themes and colors at `client/themes/`
- **Input:** Use Godot's `Input` singleton and `InputMap` for all input handling
  - `Input.is_action_pressed("ui_accept")` for held keys
  - `Input.is_action_just_pressed(...)` for one-shot actions
  - Actions are defined in Godot Project Settings (Input Map)
- **Protobuf:** No codegen — manual implementation via `ProtoEncoder.gd` / `ProtoDecoder.gd` at `client/scripts/proto/`
- **Network:** `NetworkManager.gd` manages TCP + UDP connections; `PacketProtocol.gd` defines opcode constants and serialization

## Architecture Essentials

- **Server-authoritative** — all game state validated server-side; client is a dumb renderer with prediction only for movement
- **Dual-stack networking:** TCP for reliable ops (auth, inventory, chat), UDP for real-time (movement, combat)
- **20 Hz server tick** — single-threaded game loop with async I/O (Kotlin Coroutines)
- **Write-back persistence:** RAM → Redis (60s) → PostgreSQL (5min + on logout/zone change)
- **Interest management:** SpatialGrid (50×50 units) to limit network fan-out
- **Zone/Channel system:** max 1,000 players per channel, auto channel creation
- **Monster AI:** State machine (IDLE → AGGRO → ATTACK → RETURN)
- **Target scale:** 5,000 CCU, 10,000 accounts

## MMO Development Best Practices

### Server-Side Authority
- **NEVER trust client input** — validate ALL actions server-side (movement, combat, inventory, trading)
- Client-side prediction for movement only (rollback on server correction)
- Anti-cheat: validate timestamps, rate-limit actions, detect impossible movements/actions

### Security
- **Input validation:** Sanitize and validate ALL client input (SQL injection, buffer overflows)
- **Rate limiting:** Implement per-connection rate limits for all packet types
- **Session management:** JWT tokens for auth, server-side session validation, detect multi-login
- **Encryption:** TLS 1.3 for TCP, HMAC-SHA256 for UDP packet authentication
- **Password security:** bcrypt with cost factor 12 minimum, never store plaintext
- **SQL injection prevention:** Use parameterized queries/prepared statements ONLY
- **Logging:** Log suspicious activities (failed logins, unusual packet patterns)
- **ID strategy:** Use `UUID` (not auto-increment) for primary keys on security-sensitive tables (accounts, characters, inventory, and any future player-owned data). Auto-increment (`SERIAL`) is acceptable only for server-controlled reference/config tables (item definitions, monsters, loot, etc.).

### Performance & Scalability
- **Interest management:** Only send updates for entities within player's area of interest
- **Network optimization:** Batch updates, compress data, use delta compression
- **Async I/O:** Use Kotlin Coroutines for all I/O operations (database, network, Redis)
- **Connection pooling:** Use HikariCP for database, maintain Redis connection pools
- **Lazy loading:** Load only necessary data on demand
- **Horizontal scaling:** Design services to be stateless where possible

### Code Quality
- **Dependency Injection:** Use Koin 4.0 for all service dependencies, verify via tests
- **Repository pattern:** Separate data access logic (interface + implementation)
- **Error handling:** Graceful degradation, structured logging, meaningful error messages to client
- **Testing:** Unit tests for business logic, integration tests for database/network
- **Clear separation of concerns:** network, business logic, persistence — each in its own layer

## Monorepo Structure

```
server/                    # Kotlin multi-project Gradle build
  common/                  # Shared: network layer (TcpServer, Packet, Codec, ConnectionLimiter),
                           #   Protobuf/gRPC stubs, Redis client, ConfigHelper
  database-service/        # gRPC :9090 — Repository interfaces + impls, Flyway, write-back
  login-service/           # TCP :7777 — auth, registration, JWT, sessions, rate-limiting
  account-service/         # TCP :7779 — character CRUD, JWT validation
  world-service/           # TCP :7780 + UDP :7781 — gameplay, 20Hz loop, zones, combat, AI
  gradle/libs.versions.toml # Central version catalog (incl. Koin 4.0)
client/                    # Godot 4 client (GDScript)
shared/proto/              # Shared Protocol Buffer definitions (.proto)
  flyagain.proto           # Client-facing messages and opcodes
  internal.proto           # gRPC service definitions for inter-service communication
docs/                      # Design docs, architecture, setup guides
docker-compose.yml         # PostgreSQL + Redis + all 4 services
```

Build all services: `cd server && ./gradlew build`. Single service: `./gradlew :login-service:build`.

## Key Design Principles

- **NO Pay-to-Win** — fair play, skill and time investment only
- **German-themed naming** — classes (Krieger, Magier, Assassine, Kleriker), zones (Aerheim, Grüne Ebene, Dunkler Wald)
- **Security-first** — bcrypt (cost 12), JWT + session tokens, HMAC-SHA256 for UDP, rate limiting, multi-login prevention
- **Original IP** — FlyAgain is its own game; no references to other titles in any project artifact
- **Bilingual** — English and German localization for all user-facing content
