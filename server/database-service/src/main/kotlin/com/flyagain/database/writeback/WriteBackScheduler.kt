package com.flyagain.database.writeback

import com.flyagain.database.repository.CharacterRepository
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

class WriteBackScheduler(
    private val characterRepo: CharacterRepository,
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val intervalSeconds: Long = 300
) {
    private val logger = LoggerFactory.getLogger(WriteBackScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    fun stop() {
        logger.info("WriteBackScheduler stopping...")
        scope.cancel()
    }
}
