# FlyAgain

A Flyff-inspired MMORPG focused on fair gameplay with no pay-to-win mechanics. Built with a Unity client, Kotlin server, and PostgreSQL + Redis persistence.

## Tech Stack

| Layer         | Technology                              |
|---------------|-----------------------------------------|
| Client        | Unity 2022 LTS (URP, C#)               |
| Server        | Kotlin + Netty (TCP/TLS 1.3 + UDP)     |
| Database      | PostgreSQL + Redis (Docker Compose)     |
| Serialization | Protocol Buffers                        |
| Build         | Gradle (server), Unity (client)         |

## Project Structure

```
server/          # Kotlin Netty game server
client/          # Unity client project
shared/proto/    # Shared Protocol Buffer definitions
```

## Architecture Highlights

- **Server-authoritative** game state with client-side prediction
- **Dual-stack networking:** TCP for reliable ops, UDP for real-time gameplay
- **20 Hz tick rate** with async I/O via Kotlin Coroutines
- **Write-back persistence:** RAM -> Redis (60s) -> PostgreSQL (5min)
- **Spatial interest management** to limit network fan-out
- **Target scale:** 5,000 CCU across zone/channel sharding

## Game Features

- Classic tab-target combat with a meditative grinding loop
- German-themed world and class naming (Krieger, Magier, Assassine, Kleriker)
- Flight system as core traversal mechanic
- Equipment enhancement, dungeons, and quest systems
- Guild wars, open-world PvP, and seasonal rankings (post-MVP)

## Status

Early development (pre-Phase 1). See [IMPLEMENTATION_PHASES.md](IMPLEMENTATION_PHASES.md) for the detailed build plan.

## Documentation

- [GDD.md](GDD.md) -- Game Design Document
- [ARCHITECTURE.md](ARCHITECTURE.md) -- Technical Architecture
- [IMPLEMENTATION_PHASES.md](IMPLEMENTATION_PHASES.md) -- Phased Build Plan
- [ROADMAP.md](ROADMAP.md) -- High-level Roadmap

## License

All rights reserved.
