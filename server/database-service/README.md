# database-service

Sole PostgreSQL access point — all other services read and write data through this service's gRPC API.

## Responsibilities

- **gRPC server (port 9090)** — Exposes four service APIs: AccountData, CharacterData, InventoryData, GameData.
- **Database access** — HikariCP connection pool to PostgreSQL with coroutine-based repository layer.
- **Schema migrations** — Flyway runs on startup to apply SQL migrations from the classpath.
- **Write-back persistence** — Scheduled flush from Redis dirty keys to PostgreSQL (default every 300s), ensuring RAM-first performance with durable persistence.
- **Static game data** — Serves item definitions, monster definitions, monster spawns, skill definitions, and loot tables.

## Architecture

```
login-service ──gRPC──▶ AccountDataService ──▶ AccountRepository ──▶ PostgreSQL
account-service ──gRPC──▶ CharacterDataService ──▶ CharacterRepository ──▶ PostgreSQL
world-service ──gRPC──▶ InventoryDataService ──▶ InventoryRepository ──▶ PostgreSQL
                       GameDataService ──▶ GameDataRepository ──▶ PostgreSQL

WriteBackScheduler ──reads──▶ Redis (dirty keys) ──saves──▶ CharacterRepository ──▶ PostgreSQL
```

## gRPC Services

| Service | Operations |
|---|---|
| AccountDataService | GetAccountByUsername, GetAccountById, CreateAccount, UpdateLastLogin, CheckBan |
| CharacterDataService | GetCharactersByAccount, GetCharacter, CreateCharacter, SaveCharacter, DeleteCharacter, GetCharacterSkills |
| InventoryDataService | GetInventory, GetEquipment, MoveItem, AddItem, RemoveItem, EquipItem, UnequipItem |
| GameDataService | GetAllItemDefinitions, GetAllMonsterDefinitions, GetAllMonsterSpawns, GetAllSkillDefinitions, GetAllLootTables |

## Configuration

See `src/main/resources/application.conf`. Key settings: `flyagain.database.*` (JDBC URL, credentials, pool size), `flyagain.grpc.port` (9090), `flyagain.writeback.*` (flush intervals).

## Build & Run

```bash
cd server && ./gradlew :database-service:build
./gradlew :database-service:run
```

Requires PostgreSQL and Redis running (see root `docker-compose.yml`).
