# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FlyAgain is a Flyff-inspired MMORPG with a Unity (C#) client, Kotlin (Netty) microservice server, and PostgreSQL + Redis persistence. The project is currently in early development (Phase 1). See docs/GDD.md for game design, docs/ARCHITECTURE.md for full technical spec, and docs/IMPLEMENTATION_PHASES.md for the phased build plan.

## Tech Stack

- **Client:** Unity 2022 LTS with URP (C#)
- **Server:** Kotlin with Netty (TCP/TLS 1.3 + UDP), Gradle multi-project build
- **Inter-Service:** gRPC (protobuf-based)
- **Database:** PostgreSQL + Redis (via Docker Compose)
- **Serialization:** Protocol Buffers (shared `.proto` definitions)
- **DB Migrations:** Flyway

## Monorepo Structure

```
server/                    # Kotlin multi-project Gradle build
  common/                  # Shared library: Protobuf/gRPC stubs, Redis client, config
  database-service/        # gRPC :9090 — sole PostgreSQL access, Flyway, write-back
  login-service/           # TCP :7777 — auth, registration, JWT, sessions, rate-limiting
  account-service/         # TCP :7779 — character CRUD, JWT validation
  world-service/           # TCP :7780 + UDP :7781 — gameplay, 20Hz loop, zones, combat, AI
  gradle/libs.versions.toml # Central version catalog
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
