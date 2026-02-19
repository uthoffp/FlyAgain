package com.flyagain.database.writeback

import com.flyagain.database.repository.CharacterRepository
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * Periodically flushes dirty character data from Redis to PostgreSQL.
 *
 * The world service writes real-time character state (HP, position, XP, etc.)
 * into Redis hashes and marks them with a `character:<id>:dirty` key. This
 * scheduler scans for dirty keys on a configurable interval and persists
 * each character's state to PostgreSQL via [CharacterRepository.save],
 * then removes the dirty marker.
 *
 * This implements the **write-back persistence** pattern described in the
 * architecture: RAM -> Redis (60s from world-service) -> PostgreSQL
 * ([intervalSeconds] default 300s).
 *
 * @param characterRepo the character repository used to persist state to PostgreSQL
 * @param redisConnection the Lettuce Redis connection for reading dirty character data
 * @param intervalSeconds seconds between flush cycles (default: 300 = 5 minutes)
 */
class WriteBackScheduler(
    private val characterRepo: CharacterRepository,
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val intervalSeconds: Long = 300
) {
    private val logger = LoggerFactory.getLogger(WriteBackScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Starts the background flush loop.
     *
     * The loop runs on [Dispatchers.IO] and repeats every [intervalSeconds].
     * Errors during a flush cycle are logged but do not stop the scheduler.
     */
    fun start() {
        logger.info("WriteBackScheduler started (interval: {}s)", intervalSeconds)
        scope.launch {
            while (isActive) {
                delay(intervalSeconds * 1000)
                try {
                    flushDirtyCharacters()
                } catch (e: Exception) {
                    logger.error("Write-back failed", e)
                }
            }
        }
    }

    /**
     * Scans Redis for `character:*:dirty` keys and persists each dirty
     * character's hash data to PostgreSQL, then removes the dirty marker.
     */
    private suspend fun flushDirtyCharacters() {
        val redis = redisConnection.sync()
        val dirtyKeys = redis.keys("character:*:dirty")

        if (dirtyKeys.isEmpty()) return

        logger.info("Flushing {} dirty characters to PostgreSQL", dirtyKeys.size)

        for (key in dirtyKeys) {
            try {
                val charIdStr = key.removePrefix("character:").removeSuffix(":dirty")
                val charId = charIdStr.toLongOrNull() ?: continue

                val data = redis.hgetall("character:$charId")
                if (data.isNotEmpty()) {
                    val request = com.flyagain.common.grpc.SaveCharacterRequest.newBuilder()
                        .setCharacterId(charId)
                        .setHp(data["hp"]?.toIntOrNull() ?: 0)
                        .setMp(data["mp"]?.toIntOrNull() ?: 0)
                        .setXp(data["xp"]?.toLongOrNull() ?: 0)
                        .setLevel(data["level"]?.toIntOrNull() ?: 1)
                        .setMapId(data["map_id"]?.toIntOrNull() ?: 1)
                        .setPosX(data["pos_x"]?.toFloatOrNull() ?: 0f)
                        .setPosY(data["pos_y"]?.toFloatOrNull() ?: 0f)
                        .setPosZ(data["pos_z"]?.toFloatOrNull() ?: 0f)
                        .setGold(data["gold"]?.toLongOrNull() ?: 0)
                        .setPlayTime(data["play_time"]?.toLongOrNull() ?: 0)
                        .setStr(data["str"]?.toIntOrNull() ?: 0)
                        .setSta(data["sta"]?.toIntOrNull() ?: 0)
                        .setDex(data["dex"]?.toIntOrNull() ?: 0)
                        .setIntStat(data["int_stat"]?.toIntOrNull() ?: 0)
                        .setStatPoints(data["stat_points"]?.toIntOrNull() ?: 0)
                        .build()

                    characterRepo.save(request)
                    redis.del(key)
                    logger.debug("Flushed character {} to PostgreSQL", charId)
                }
            } catch (e: Exception) {
                logger.error("Failed to flush character from key: {}", key, e)
            }
        }
    }

    /**
     * Cancels the background flush loop.
     *
     * Should be called during service shutdown to ensure a clean stop.
     */
    fun stop() {
        logger.info("WriteBackScheduler stopping...")
        scope.cancel()
    }
}
