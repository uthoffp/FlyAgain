# world-service

Real-time gameplay server — handles movement, combat, AI, and entity synchronization.

## Responsibilities

- **20 Hz game loop** — Single-threaded tick processing: drain input queue, update movement, run monster AI, process combat, broadcast state.
- **Entity management** — Tracks all players (IDs 1+) and monsters (IDs 1,000,000+) with lookups by entity/account/character ID.
- **Zone & channel system** — Three zones (Aerheim, Grüne Ebene, Dunkler Wald) with auto-scaling channels (max 1,000 players each).
- **Spatial interest management** — 50×50 unit grid cells; only entities in the 3×3 neighborhood are broadcast to each other.
- **Combat engine** — Server-authoritative damage formula (`atk - def ± 2`, 10% crit at 1.5×, min damage 1), auto-attack processing, and skill system with cooldowns/MP costs.
- **Monster AI** — State machine per monster: IDLE → AGGRO → ATTACK → RETURN → DEAD, with leash distance and respawn timers.
- **Input queue** — Lock-free `ConcurrentLinkedQueue` bridges network threads to the game loop thread.

## Architecture

```
Client ──TCP:7780──▶ Netty TCP Pipeline ──▶ InputQueue ──▶ Game Loop (20 Hz)
Client ──UDP:7781──▶ Netty UDP Pipeline ──▶ InputQueue ─┘       │
                                                                 ▼
                                                         ZoneManager
                                                          ├─ ZoneChannel (per channel)
                                                          │   ├─ SpatialGrid
                                                          │   ├─ Players
                                                          │   └─ Monsters
                                                          ├─ CombatEngine
                                                          ├─ MonsterAI
                                                          └─ EntityManager
```

## Configuration

See `src/main/resources/application.conf`. Key settings: `flyagain.network.tcp-port` (7780), `flyagain.network.udp-port` (7781), `flyagain.gameloop.tick-rate` (20).

## Build & Run

```bash
cd server && ./gradlew :world-service:build
./gradlew :world-service:run
```
