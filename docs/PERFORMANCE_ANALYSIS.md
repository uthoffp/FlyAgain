# FlyAgain - Performance Bottleneck Analysis

> Comprehensive analysis of client and server code for scalability under high concurrency.
> Target: 5,000 CCU across zone/channel sharding.

---

## Executive Summary

The architecture is well-designed for an MMO — single-threaded game loop, spatial grid interest
management, write-back caching, and dual-stack networking are all correct patterns. However, the
implementation has numerous bottlenecks that would cause cascading failures around 2,000-3,000
concurrent users without fixes. All bottlenecks are implementation-level and fixable without
architectural changes.

---

## CRITICAL — Must Fix Before Load Testing

### 1. SpatialGrid Lock Contention

**File:** `server/world-service/.../zone/ZoneChannel.kt`
**Component:** Server — World Service

`synchronized(gridLock)` on every movement update, entity spawn, and AI query. With 1,000 players
and 100 monsters in one channel: ~10,200 lock acquisitions per tick. A single slow reader blocks
all writers.

**Fix:** Replace with `ReentrantReadWriteLock`. Remove defensive `HashSet()` copy in
`getNearbyEntities()` — safe since the game loop is single-threaded.

---

### 2. Monster AI Distance Checks — O(n*m) with sqrt()

**File:** `server/world-service/.../ai/MonsterAI.kt`
**Component:** Server — World Service

IDLE monsters scan 9 spatial cells and call `sqrt()` per nearby entity. At 100 monsters x 100
nearby entities = 10,000 sqrt() calls per tick. Scaled across 100 channels: **~150ms/tick**
(3x the 50ms budget).

**Fix:**
- Use squared-distance comparisons (eliminate sqrt entirely)
- Stagger IDLE scans: `entityId % 3 == tick % 3` reduces checks by 66%
- Batch AI processing by state (IDLE, AGGRO, ATTACK, RETURN)

---

### 3. UDP Packet Processing — Triple Heap Allocation

**File:** `server/common/.../network/UdpServer.kt`
**Component:** Server — Network Layer

Every incoming UDP packet allocates 4 `ByteArray` objects (rawBytes, payload, receivedHmac,
dataToSign). At 500k packets/sec this produces **~2MB/sec GC churn**.

**Fix:**
- Use `ByteBuf` slices instead of heap array copies
- Defer payload allocation until after HMAC validation succeeds
- Pre-allocate HMAC buffer via `ThreadLocal` (pattern already used for `Mac` instance)

---

### 4. TCP SO_BACKLOG Hardcoded to 128

**File:** `server/common/.../network/TcpServer.kt` (line 77)
**Component:** Server — Network Layer

SYN queue limited to 128. Login spikes (50-100 new connections/sec) will overflow this, causing
client timeouts and cascading retries.

**Fix:** Increase to 1,024+ (make configurable via application.conf).

---

### 5. Single Redis Connection — No Pool

**File:** `server/common/.../redis/RedisClientFactory.kt`
**Component:** Server — Persistence

One `StatefulRedisConnection` shared across all services. All Redis operations serialize through
a single connection.

**Fix:** Implement Lettuce connection pool (`min=5, max=20`).

---

### 6. Write-Back Race Condition (Dirty Flag)

**File:** `server/database-service/.../writeback/WriteBackScheduler.kt`
**Component:** Server — Persistence

No locking between the game loop writing character data to Redis and the scheduler reading/flushing
to PostgreSQL. The game loop can overwrite character data between the scheduler's read and DB
persist, causing **stale data to be persisted**.

**Fix:** Use Redis `WATCH`/`RENAME` pattern or Lua scripts for atomic read-and-clear of dirty flags.

---

### 7. Client PackedByteArray.slice() — GC Pressure

**File:** `client/autoloads/NetworkManager.gd` (lines 377-394)
**Component:** Client — Network

2-3 new `PackedByteArray` allocations per packet via `slice()`. At 20 Hz with 50 entity updates:
continuous GC pressure causing frame hitches.

**Fix:** Use index-pointer-based parsing (`_pos += n`) instead of creating new arrays via slicing.

---

### 8. Client ProtoDecoder Float Allocations

**File:** `client/scripts/proto/ProtoDecoder.gd` (line 417)
**Component:** Client — Network

`_read_float32()` calls `_data.slice(_pos, _pos+4)` for every float field. 50 entities x 6 floats
x 20 Hz = **6,000 slice allocations/sec**.

**Fix:** Use `_data.decode_float(_pos)` directly (zero-copy read).

---

## HIGH — Fix Before Alpha

### 9. Position Broadcast — No Delta Compression

**File:** `server/world-service/.../network/BroadcastService.kt`
**Component:** Server — World Service

Full position serialization via protobuf on every update (~24 bytes per entity). 1,000 players x
20 Hz = ~480 KB/s outbound per channel. Serialization alone consumes ~40% of the tick budget.

**Fix:**
- Movement culling: skip broadcasts for idle/standing players
- Delta compression: send only changed fields
- Compact binary format: 8 bytes (3x i16 position + i16 rotation) vs 24 bytes protobuf

---

### 10. Blocking Redis on UDP Event Loop

**File:** `server/world-service/.../network/WorldUdpHandler.kt`
**Component:** Server — Network Layer

`redisConnection.sync().hget()` on session cache miss blocks the entire UDP event loop thread.
Attackers can trigger this with random session tokens.

**Fix:** Return null on cache miss immediately; defer Redis lookup to a background coroutine.

---

### 11. HikariCP Pool Too Small

**File:** `server/database-service/src/main/resources/application.conf`
**Component:** Server — Persistence

`max-pool-size = 20` shared across all services via gRPC. At 5,000 CCU with concurrent calls from
login, account, and world services: connection pool exhaustion is guaranteed.

**Fix:** Increase to 40-50. Add `minimumIdle`, `maxLifetime`, and connection timeout configuration.

---

### 12. Account Service Coroutine Dispatcher

**File:** `server/account-service/.../handler/PacketRouter.kt`
**Component:** Server — Account Service

Uses `Dispatchers.Default` (CPU-bound, limited to core count) for I/O-bound handlers making gRPC
calls. Queue grows unbounded under load.

**Fix:** Switch to `Dispatchers.IO` with bounded queue depth.

---

### 13. Movement Validation sqrt()

**File:** `server/world-service/.../handler/MovementHandler.kt`
**Component:** Server — World Service

Speed validation uses `sqrt()` per moving player per tick. 1,000 players x 2 sqrt() calls per
tick = 12% of the 50ms tick budget.

**Fix:** Squared-distance comparison instead of sqrt().

---

### 14. Client Label3D Billboard Rendering

**File:** `client/scenes/game/RemoteEntity.tscn`
**Component:** Client — Rendering

Each remote entity has a `Label3D` with billboard mode, requiring text mesh regeneration every
frame. 50 entities = 50 Label3D updates per frame.

**Fix:** Replace with CanvasLayer 2D labels or pre-rendered texture quads.

---

### 15. Client Linear Interpolation Search

**File:** `client/scripts/movement/EntityInterpolator.gd` (line 72)
**Component:** Client — Rendering

Linear search through snapshot buffer on every `_physics_process()`. 50 entities x 60 FPS =
3,000 linear searches/sec.

**Fix:** Cache current interpolation pair; use typed struct instead of Dictionary.

---

## MEDIUM — Fix Before Beta

### 16. Inventory N+1 Queries

**File:** `server/database-service/.../repository/InventoryRepositoryImpl.kt` (lines 65-99)
**Component:** Server — Persistence

Item move/swap requires 5 separate DB round-trips.

**Fix:** Consolidate into a single SQL statement with CASE expressions.

---

### 17. Missing Database Indexes

**Component:** Server — Database Schema

Migration files lack indexes on `characters(map_id)`, `characters(account_id, is_deleted)`, and
FK columns. Zone loading causes full table scans.

**Fix:** Add composite indexes:
```sql
CREATE INDEX idx_characters_account_deleted ON characters(account_id, is_deleted);
CREATE INDEX idx_characters_map_id ON characters(map_id);
CREATE INDEX idx_characters_deleted_at_map ON characters(map_id) WHERE is_deleted = FALSE;
```

---

### 18. UDP Flood Protection Locking

**File:** `server/common/.../network/UdpFloodProtection.kt` (line 45)
**Component:** Server — Network Layer

Per-IP `synchronized` block in the UDP hot path.

**Fix:** Replace with `AtomicInteger` + epoch-based window reset (lock-free).

---

### 19. WriteBackScheduler Sequential Flush

**File:** `server/database-service/.../writeback/WriteBackScheduler.kt`
**Component:** Server — Persistence

Iterates dirty characters sequentially. 500 dirty characters x 50-100ms each = 25-50 seconds,
missing the next flush cycle.

**Fix:** Batch-parallelize with `coroutineScope.launch` (process 50 characters concurrently).

---

### 20. Client Synchronous Zone Spawning

**File:** `client/scenes/game/GameWorld.gd` (lines 99-105)
**Component:** Client — Rendering

All entities spawned in a single-frame loop. 100+ entities = massive frame spike on zone entry.

**Fix:** Batch spawns across frames (e.g., 10 entities per frame).

---

### 21. Client Per-Entity Material Allocation

**File:** `client/scenes/game/RemoteEntity.gd` (line 90)
**Component:** Client — Rendering

`StandardMaterial3D.new()` per entity with no pooling or reuse.

**Fix:** Pre-create shared materials per entity class. Use shader-based tinting for variation.

---

### 22. No Game Loop Phase Metrics

**File:** `server/world-service/.../gameloop/GameLoop.kt`
**Component:** Server — World Service

No per-phase timing instrumentation (movement vs AI vs broadcast). Cannot identify which subsystem
causes tick overruns.

**Fix:** Add per-phase timing. Log warnings if any single phase exceeds 15ms (30% of budget).

---

### 23. PostgreSQL/Redis Not Tuned

**File:** `docker-compose.yml`
**Component:** Infrastructure

Default PostgreSQL config (`shared_buffers=128MB`, `max_connections=100`). Redis has no persistence
configured, no max memory limit.

**Fix:**
```yaml
# PostgreSQL
POSTGRES_INITDB_ARGS: >-
  -c shared_buffers=512MB
  -c max_connections=200
  -c effective_cache_size=2GB
  -c work_mem=4MB

# Redis
command: "redis-server --appendonly yes --maxmemory 512mb --maxmemory-policy allkeys-lru"
```

---

## Additional Findings

### Netty Event Loop Group Sizing

**File:** `server/common/.../network/TcpServer.kt` (lines 71-72)

Worker group uses default sizing (= CPU core count). For 5,000 CCU on an 8-core machine, each
thread handles ~625 connections. Consider `worker = 2 * cores` for I/O-heavy workloads.

### Connection Limiter Contention

**File:** `server/common/.../network/ConnectionLimiter.kt` (lines 41-66)

ConcurrentHashMap segment locking + double lookup on rejection path. Use `LongAdder` or
lock-striped counters for higher concurrency.

### Idle Timeout / Heartbeat Mismatch

**File:** `server/common/.../network/TcpServer.kt`

IdleStateHandler (60s) and HeartbeatTracker (15s) operate independently. Align timeouts and
coordinate disconnect handling to prevent race conditions.

### ByteBuf Pooling Not Explicitly Enabled

**Files:** `TcpServer.kt`, `UdpServer.kt`

Netty ByteBuf pooling is not explicitly configured. Enable `PooledByteBufAllocator` in the server
bootstrap for production workloads.

### Full Table Loads in GameDataRepository

**File:** `server/database-service/.../repository/GameDataRepositoryImpl.kt`

`getAllItemDefinitions()` and similar methods load entire tables into memory on every call with no
caching. Cache these read-only tables at world-service startup.

---

## Priority Roadmap

| Phase | Fixes | Impact |
|-------|-------|--------|
| **Now** (pre-load-test) | #1-8 — locks, sqrt, allocations, connection pool, write-back race | Unblocks 3,000-5,000 CCU |
| **Alpha** | #9-15 — delta compression, blocking Redis, pool sizing, client rendering | Stable at 5,000 CCU |
| **Beta** | #16-23 — queries, indexes, metrics, infrastructure tuning | Production-ready at 5,000+ CCU |

---

## Cross-Reference

These findings directly inform Phase 5.3 (Performance-Optimierung) in
[IMPLEMENTATION_PHASES.md](IMPLEMENTATION_PHASES.md). Many items identified here should be
addressed earlier than Phase 5 to ensure stable development and testing throughout Phase 1-2.
