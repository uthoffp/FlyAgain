package com.flyagain.world.gameloop

import com.flyagain.world.ai.MonsterAI
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.handler.MovementHandler
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.session.SessionLifecycleManager
import com.flyagain.world.zone.ZoneManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The main server game loop running at 20 Hz (50ms per tick).
 *
 * Each tick processes:
 * 1. Incoming packets from the InputQueue
 * 2. Movement validation and application
 * 3. Monster AI updates
 * 4. State change broadcasts to nearby players
 * 5. Periodic character persistence (every 60s)
 *
 * The game loop runs on a dedicated thread. All game state mutations
 * happen within this single thread to avoid synchronization issues.
 */
class GameLoop(
    private val inputQueue: InputQueue,
    private val entityManager: EntityManager,
    private val zoneManager: ZoneManager,
    private val movementHandler: MovementHandler,
    private val monsterAI: MonsterAI,
    private val broadcastService: BroadcastService,
    private val sessionLifecycleManager: SessionLifecycleManager,
    private val asyncScope: CoroutineScope,
    private val tickRate: Int = 20
) {

    private val logger = LoggerFactory.getLogger(GameLoop::class.java)
    private val running = AtomicBoolean(false)
    private val tickDurationMs = 1000L / tickRate

    // Persistence: save dirty characters to Redis every 60 seconds
    private var lastPersistenceTime = System.currentTimeMillis()
    private val persistenceIntervalMs = 60_000L

    @Volatile
    private var gameThread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Game loop already running")
            return
        }

        val thread = Thread({
            logger.info("Game loop started at {} Hz ({}ms/tick)", tickRate, tickDurationMs)
            run()
        }, "game-loop")
        thread.isDaemon = true
        thread.start()
        gameThread = thread
    }

    fun stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Game loop stopping...")
            gameThread?.join(5000)
            gameThread = null
            logger.info("Game loop stopped")
        }
    }

    private fun run() {
        while (running.get()) {
            val tickStart = System.nanoTime()

            try {
                tick()
            } catch (e: Exception) {
                logger.error("Error in game loop tick", e)
            }

            // Sleep until next tick
            val elapsed = (System.nanoTime() - tickStart) / 1_000_000
            val sleepTime = tickDurationMs - elapsed
            if (sleepTime > 0) {
                Thread.sleep(sleepTime)
            } else if (sleepTime < -10) {
                logger.warn("Game loop tick took {}ms (budget: {}ms)", elapsed, tickDurationMs)
            }
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()

        // 1. Process incoming packets
        processInputQueue()

        // 2. Update movement for all players
        updateMovement(tickDurationMs)

        // 3. Update monster AI
        updateMonsterAI(tickDurationMs)

        // 4. Broadcast state changes
        broadcastStateChanges()

        // 5. Periodic persistence
        if (now - lastPersistenceTime >= persistenceIntervalMs) {
            lastPersistenceTime = now
            persistDirtyCharacters()
        }
    }

    private fun processInputQueue() {
        val packets = inputQueue.drainAll()
        for (packet in packets) {
            try {
                when (packet.opcode) {
                    0x0101 -> movementHandler.handleMovementInput(packet)
                    else -> logger.debug("Unknown game opcode 0x{} in input queue", packet.opcode.toString(16))
                }
            } catch (e: Exception) {
                logger.error("Error processing queued packet (opcode=0x{})", packet.opcode.toString(16), e)
            }
        }
    }

    private fun updateMovement(deltaMs: Long) {
        for (channel in zoneManager.getAllChannels()) {
            for (player in channel.getAllPlayers()) {
                if (player.isMoving) {
                    movementHandler.applyMovement(player, deltaMs, channel)
                }
            }
        }
    }

    private fun updateMonsterAI(deltaMs: Long) {
        for (channel in zoneManager.getAllChannels()) {
            val result = monsterAI.updateChannel(channel, deltaMs)

            // Broadcast damage events from monster attacks
            for (damageEvent in result.damageEvents) {
                broadcastService.broadcastDamageEvent(channel, damageEvent)
            }

            // Broadcast monster respawns
            for (monster in result.respawnedMonsters) {
                broadcastService.broadcastEntitySpawn(channel, monster)
            }
        }
    }

    private fun broadcastStateChanges() {
        broadcastService.flushPendingUpdates()
        // Batch-flush all TCP writes queued during this tick (one syscall per player)
        broadcastService.flushNetworkWrites()
    }

    private fun persistDirtyCharacters() {
        val dirtyPlayers = entityManager.getAllPlayers().filter { it.dirty }
        if (dirtyPlayers.isEmpty()) return

        // Snapshot values on the game thread; clear dirty flag immediately to avoid double-saves
        val snapshots = dirtyPlayers.map { p ->
            p.dirty = false
            sessionLifecycleManager.snapshotPlayer(p)
        }

        logger.debug("Persisting {} dirty characters to Redis", snapshots.size)
        asyncScope.launch {
            for ((characterId, fields) in snapshots) {
                try {
                    sessionLifecycleManager.saveSnapshotToRedis(characterId, fields)
                } catch (e: Exception) {
                    logger.error("Failed to persist character {} to Redis", characterId, e)
                }
            }
        }
    }
}
