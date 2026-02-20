package com.flyagain.world.session

import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.SaveCharacterRequest
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneManager
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory

/**
 * Manages the full session lifecycle for players in the world service.
 *
 * Handles:
 * - Force-flush of character state to Redis + PostgreSQL on disconnect
 * - Redis session key cleanup
 * - Entity removal from zone/channel/spatial grid
 * - Despawn broadcast to nearby players
 */
class SessionLifecycleManager(
    private val entityManager: EntityManager,
    private val zoneManager: ZoneManager,
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val characterDataStub: CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub,
    private val broadcastService: BroadcastService
) {

    private val logger = LoggerFactory.getLogger(SessionLifecycleManager::class.java)

    /**
     * Handles a player disconnect: saves state, cleans up Redis, removes from zone.
     *
     * @param player the disconnecting player entity
     * @return list of nearby entity IDs that should receive a despawn notification
     */
    suspend fun handleDisconnect(player: PlayerEntity) {
        logger.info("Handling disconnect for player {} (characterId={}, accountId={})",
            player.name, player.characterId, player.accountId)

        // 1. Broadcast despawn to nearby players before removal
        val channel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (channel != null) {
            broadcastService.broadcastEntityDespawn(channel, player.entityId, player.x, player.z)
        }

        // 2. Force-flush character state to PostgreSQL via gRPC
        try {
            flushCharacterToDatabase(player)
        } catch (e: Exception) {
            logger.error("Failed to flush character {} to database on disconnect", player.characterId, e)
        }

        // 3. Clean up Redis character cache and dirty marker
        try {
            cleanupRedisState(player)
        } catch (e: Exception) {
            logger.error("Failed to clean up Redis state for character {}", player.characterId, e)
        }

        // 4. Remove player from zone/channel/spatial grid
        zoneManager.removePlayerFromZone(player)
        entityManager.removePlayer(player.entityId)

        // 5. Close the TCP channel if still open
        player.tcpChannel?.let { ch ->
            if (ch.isActive) {
                ch.close()
            }
        }

        logger.info("Disconnect complete for player {} (characterId={})", player.name, player.characterId)
    }

    /**
     * Force-flush character state directly to PostgreSQL via gRPC.
     * Called on disconnect and zone change for immediate persistence.
     */
    suspend fun flushCharacterToDatabase(player: PlayerEntity) {
        val request = SaveCharacterRequest.newBuilder()
            .setCharacterId(player.characterId)
            .setHp(player.hp)
            .setMp(player.mp)
            .setXp(player.xp)
            .setLevel(player.level)
            .setMapId(player.zoneId)
            .setPosX(player.x)
            .setPosY(player.y)
            .setPosZ(player.z)
            .setGold(player.gold)
            .setPlayTime(calculatePlayTime(player))
            .setStr(player.str)
            .setSta(player.sta)
            .setDex(player.dex)
            .setIntStat(player.int)
            .setStatPoints(player.statPoints)
            .build()

        characterDataStub.saveCharacter(request)
        player.dirty = false
        player.lastSaveTime = System.currentTimeMillis()
        logger.debug("Character {} flushed to database", player.characterId)
    }

    /**
     * Saves character state to Redis for write-back persistence.
     * Called periodically (every 60s) by the game loop.
     */
    suspend fun saveCharacterToRedis(player: PlayerEntity) {
        if (!player.dirty) return

        val async = redisConnection.async()
        val charKey = "character:${player.characterId}"
        val dirtyKey = "character:${player.characterId}:dirty"

        val fields = mapOf(
            "account_id" to player.accountId.toString(),
            "name" to player.name,
            "class" to player.characterClass.toString(),
            "level" to player.level.toString(),
            "xp" to player.xp.toString(),
            "hp" to player.hp.toString(),
            "mp" to player.mp.toString(),
            "max_hp" to player.maxHp.toString(),
            "max_mp" to player.maxMp.toString(),
            "str" to player.str.toString(),
            "sta" to player.sta.toString(),
            "dex" to player.dex.toString(),
            "int_stat" to player.int.toString(),
            "stat_points" to player.statPoints.toString(),
            "map_id" to player.zoneId.toString(),
            "pos_x" to player.x.toString(),
            "pos_y" to player.y.toString(),
            "pos_z" to player.z.toString(),
            "gold" to player.gold.toString(),
            "play_time" to calculatePlayTime(player).toString()
        )

        async.hset(charKey, fields).await()
        async.expire(charKey, 3600).await()
        async.set(dirtyKey, "1").await()
        async.expire(dirtyKey, 3600).await()

        player.dirty = false
        logger.trace("Character {} saved to Redis", player.characterId)
    }

    /**
     * Clean up all Redis state associated with a player session.
     * Pipelines independent commands to reduce round-trips.
     */
    private suspend fun cleanupRedisState(player: PlayerEntity) {
        val async = redisConnection.async()

        // Batch 1: fire all independent deletes/removes in parallel
        redisConnection.setAutoFlushCommands(false)
        try {
            val f1 = async.del("character:${player.characterId}")
            val f2 = async.del("character:${player.characterId}:dirty")
            val f3 = async.srem("online_players", player.characterId.toString())
            val f4 = async.srem(
                "zone:${player.zoneId}:channel:${player.channelId}",
                player.characterId.toString()
            )
            redisConnection.flushCommands()
            f1.await(); f2.await(); f3.await(); f4.await()
        } finally {
            redisConnection.setAutoFlushCommands(true)
        }

        // Batch 2: session cleanup (needs hget result before conditional del)
        if (player.sessionId.isNotEmpty()) {
            val sessionKey = "session:${player.sessionId}"
            val accountId = async.hget(sessionKey, "accountId").await()

            async.del(sessionKey).await()
            if (accountId != null) {
                val accountKey = "session:account:$accountId"
                val currentSession = async.get(accountKey).await()
                if (currentSession == player.sessionId) {
                    async.del(accountKey).await()
                }
            }
        }

        logger.debug("Redis state cleaned up for character {}", player.characterId)
    }

    /**
     * Create a thread-safe snapshot of player fields for async Redis persistence.
     * Must be called from the game loop thread.
     */
    fun snapshotPlayer(player: PlayerEntity): Pair<Long, Map<String, String>> {
        return player.characterId to mapOf(
            "account_id" to player.accountId.toString(),
            "name" to player.name,
            "class" to player.characterClass.toString(),
            "level" to player.level.toString(),
            "xp" to player.xp.toString(),
            "hp" to player.hp.toString(),
            "mp" to player.mp.toString(),
            "max_hp" to player.maxHp.toString(),
            "max_mp" to player.maxMp.toString(),
            "str" to player.str.toString(),
            "sta" to player.sta.toString(),
            "dex" to player.dex.toString(),
            "int_stat" to player.int.toString(),
            "stat_points" to player.statPoints.toString(),
            "map_id" to player.zoneId.toString(),
            "pos_x" to player.x.toString(),
            "pos_y" to player.y.toString(),
            "pos_z" to player.z.toString(),
            "gold" to player.gold.toString(),
            "play_time" to calculatePlayTime(player).toString()
        )
    }

    /**
     * Save a pre-computed snapshot of character fields to Redis.
     * Safe to call from any thread since the data is already captured.
     *
     * Uses pipelining: all 4 commands are dispatched in a single batch
     * and awaited together (1 round-trip instead of 4 sequential ones).
     */
    suspend fun saveSnapshotToRedis(characterId: Long, fields: Map<String, String>) {
        val async = redisConnection.async()
        val charKey = "character:$characterId"
        val dirtyKey = "character:$characterId:dirty"

        // Disable auto-flush so all commands go in one pipeline batch
        redisConnection.setAutoFlushCommands(false)
        try {
            val f1 = async.hset(charKey, fields)
            val f2 = async.expire(charKey, 3600)
            val f3 = async.set(dirtyKey, "1")
            val f4 = async.expire(dirtyKey, 3600)
            redisConnection.flushCommands()

            f1.await()
            f2.await()
            f3.await()
            f4.await()
        } finally {
            redisConnection.setAutoFlushCommands(true)
        }

        logger.trace("Character {} snapshot saved to Redis", characterId)
    }

    private fun calculatePlayTime(player: PlayerEntity): Long {
        // Total play time = previously accumulated + current session duration (in seconds)
        val currentSessionSeconds = (System.currentTimeMillis() - player.sessionStartTime) / 1000
        return player.playTimeAccumulated + currentSessionSeconds
    }
}
