# FlyAgain

An original MMORPG focused on fair gameplay with no pay-to-win mechanics. Built with a Godot 4 client, Kotlin server, and PostgreSQL + Redis persistence.

## Tech Stack

| Layer         | Technology                              |
|---------------|-----------------------------------------|
| Client        | Godot 4 (GDScript)                     |
| Server        | Kotlin + Netty (TCP/TLS 1.3 + UDP)     |
| Database      | PostgreSQL + Redis (Docker Compose)     |
| Serialization | Protocol Buffers                        |
| Build         | Gradle (server), Godot Editor (client)  |

## Project Structure

```
server/              # Kotlin Netty game server (standalone Gradle project)
client/              # Godot 4 client (GDScript)
shared/proto/        # Shared Protocol Buffer definitions
docs/                # Design docs, architecture, setup guides
docker-compose.yml   # PostgreSQL + Redis dev services
```

## Quick Start

**New to the project?** See [SETUP.md](SETUP.md) for complete setup instructions (Windows & macOS).

### Server

**Windows:**
```powershell
cd server
.\gradlew.bat build       # compile + test
.\gradlew.bat run         # start the server
```

**macOS/Linux:**
```bash
cd server
./gradlew build       # compile + test
./gradlew run         # start the server
```

### Infrastructure

```bash
docker compose up -d  # start PostgreSQL + Redis
```

### Client

See [docs/godot-setup.md](docs/godot-setup.md) for Godot project setup instructions.

## Architecture Highlights

- **Server-authoritative** game state with client-side prediction
- **Dual-stack networking:** TCP for reliable ops, UDP for real-time gameplay
- **20 Hz tick rate** with async I/O via Kotlin Coroutines
- **Write-back persistence:** RAM -> Redis (60s) -> PostgreSQL (5min)
- **Spatial interest management** to limit network fan-out
- **Target scale:** 5,000 CCU across zone/channel sharding

## Game Features

- Classic tab-target combat with a meditative grinding loop
- Four distinct classes: Warrior, Mage, Assassin, Cleric
- Flight system as core traversal mechanic
- Equipment enhancement, dungeons, and quest systems
- Guild wars, open-world PvP, and seasonal rankings (post-MVP)

## Status

Early development (Phase 1.1 — project setup complete). See [docs/IMPLEMENTATION_PHASES.md](docs/IMPLEMENTATION_PHASES.md) for the detailed build plan.

## Documentation

- **[SETUP.md](SETUP.md) -- Complete Setup Guide (Windows & macOS)**
- [docs/GDD.md](docs/GDD.md) -- Game Design Document
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) -- Technical Architecture
- [docs/IMPLEMENTATION_PHASES.md](docs/IMPLEMENTATION_PHASES.md) -- Phased Build Plan
- [docs/ROADMAP.md](docs/ROADMAP.md) -- High-level Roadmap
- [docs/godot-setup.md](docs/godot-setup.md) -- Godot Client Setup Guide
- [server/README.md](server/README.md) -- Server Documentation

## License

All rights reserved.
