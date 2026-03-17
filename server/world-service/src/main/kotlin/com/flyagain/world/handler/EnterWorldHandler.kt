package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.*
import com.flyagain.world.combat.XpSystem
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.EntitySpawnBuilder
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.RedisSessionSecretProvider
import com.flyagain.world.zone.ZoneManager
import io.lettuce.core.api.StatefulRedisConnection
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles ENTER_WORLD (0x0004) requests from the client.
 *
 * When a player connects to the world service after character selection,
 * this handler:
 * 1. Validates the JWT and extracts account ID
 * 2. Loads character data from Redis cache (placed there by account-service)
 * 3. Creates a PlayerEntity in the EntityManager
 * 4. Adds the player to the appropriate zone/channel
 * 5. Sends ZoneData with all visible entities to the new player
 * 6. Broadcasts EntitySpawn to nearby players
 */
class EnterWorldHandler(
    private val entityManager: EntityManager,
    private val zoneManager: ZoneManager,
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val jwtSecret: String,
    private val sessionSecretProvider: RedisSessionSecretProvider
) {

    private val logger = LoggerFactory.getLogger(EnterWorldHandler::class.java)

    suspend fun handle(ctx: ChannelHandlerContext, request: EnterWorldRequest): PlayerEntity? {
        val characterId = request.characterId
        val sessionId = request.sessionId

        // Validate JWT
        val accountId = validateJwt(request.jwt)
        if (accountId == null) {
            sendError(ctx, "Invalid or expired authentication token.")
            return null
        }

        // Load character data from Redis cache
        val charData = loadCharacterFromRedis(characterId)
        if (charData == null) {
            logger.warn("Character {} not found in Redis cache for account {}", characterId, accountId)
            sendError(ctx, "Character data not found. Please re-select your character.")
            return null
        }

        // Verify ownership
        val cachedAccountId = charData["account_id"]
        if (cachedAccountId != accountId) {
            logger.warn("Character {} does not belong to account {} (cached account: {})",
                characterId, accountId, cachedAccountId)
            sendError(ctx, "Character not found.")
            return null
        }

        // Load session data from Redis to get session token and HMAC secret
        val sessionData = loadSessionFromRedis(sessionId)
        if (sessionData == null) {
            logger.warn("Session {} not found in Redis for account {}", sessionId, accountId)
            sendError(ctx, "Session expired. Please re-login.")
            return null
        }

        val sessionTokenLong = sessionData["sessionToken"]?.toLongOrNull() ?: 0L
        val hmacSecret = sessionData["hmacSecret"] ?: ""

        if (sessionTokenLong == 0L || hmacSecret.isEmpty()) {
            logger.warn("Session {} missing token or HMAC secret for account {}", sessionId, accountId)
            sendError(ctx, "Session invalid. Please re-login.")
            return null
        }

        // Create PlayerEntity
        val entityId = entityManager.nextPlayerId()
        val mapId = charData["map_id"]?.toIntOrNull() ?: ZoneManager.ZONE_AERHEIM

        // Treat (0, 0, 0) as uninitialized — fall back to the zone's default spawn point
        val rawX = charData["pos_x"]?.toFloatOrNull() ?: 0f
        val rawY = charData["pos_y"]?.toFloatOrNull() ?: 0f
        val rawZ = charData["pos_z"]?.toFloatOrNull() ?: 0f
        val (defaultX, defaultY, defaultZ) = zoneManager.getSpawnPosition(mapId)
        val spawnX = if (rawX == 0f && rawZ == 0f) defaultX else rawX
        val spawnY = if (rawX == 0f && rawZ == 0f) defaultY else rawY
        val spawnZ = if (rawX == 0f && rawZ == 0f) defaultZ else rawZ

        val player = PlayerEntity(
            entityId = entityId,
            characterId = characterId,
            accountId = accountId,
            name = charData["name"] ?: "Unknown",
            characterClass = charData["class"]?.toIntOrNull() ?: 0,
            x = spawnX,
            y = spawnY,
            z = spawnZ,
            rotation = charData["rotation"]?.toFloatOrNull() ?: 0f,
            level = charData["level"]?.toIntOrNull() ?: 1,
            hp = charData["hp"]?.toIntOrNull() ?: 100,
            maxHp = charData["max_hp"]?.toIntOrNull() ?: 100,
            mp = charData["mp"]?.toIntOrNull() ?: 50,
            maxMp = charData["max_mp"]?.toIntOrNull() ?: 50,
            str = charData["str"]?.toIntOrNull() ?: 10,
            sta = charData["sta"]?.toIntOrNull() ?: 10,
            dex = charData["dex"]?.toIntOrNull() ?: 10,
            int = charData["int"]?.toIntOrNull() ?: 10,
            statPoints = charData["stat_points"]?.toIntOrNull() ?: 0,
            xp = charData["xp"]?.toLongOrNull() ?: 0L,
            gold = charData["gold"]?.toLongOrNull() ?: 0L,
            tcpChannel = ctx.channel(),
            sessionId = sessionId,
            sessionTokenLong = sessionTokenLong,
            hmacSecret = hmacSecret
        )

        // Recalculate xpToNextLevel from the loaded level (not stored in DB/Redis)
        if (player.level < XpSystem.MAX_LEVEL) {
            player.xpToNextLevel = XpSystem.xpToNextLevel(player.level + 1)
        } else {
            player.xpToNextLevel = 0L
        }

        // Atomically add to entity manager (rejects if account already has a player in world)
        if (!entityManager.tryAddPlayer(player)) {
            logger.warn("Account {} already has a player in world, rejecting duplicate login", accountId)
            sendError(ctx, "Character already in world. Please wait and try again.")
            return null
        }

        // Add to zone (find best channel)
        val targetZone = if (zoneManager.zoneExists(mapId)) mapId else ZoneManager.ZONE_AERHEIM
        val channel = zoneManager.addPlayerToZone(player, targetZone)
        if (channel == null) {
            logger.error("Failed to add player {} to zone {}", player.name, targetZone)
            entityManager.removePlayer(entityId)
            sendError(ctx, "Failed to enter zone. Please try again.")
            return null
        }

        // Register HMAC secret for UDP packet validation
        sessionSecretProvider.registerSecret(sessionTokenLong, hmacSecret.toByteArray(Charsets.UTF_8))

        // Add to online players in Redis
        try {
            val redis = redisConnection.async()
            redis.sadd("online_players", characterId.toString())
            redis.sadd("zone:${player.zoneId}:channel:${player.channelId}", characterId.toString())
        } catch (e: Exception) {
            logger.warn("Failed to update Redis online status for character {}", characterId, e)
        }

        // Build and send ZoneData to the new player
        sendZoneData(ctx, player, channel)

        // Build EntitySpawn for the new player and broadcast to nearby
        broadcastPlayerSpawn(player, channel)

        logger.info("Player {} (characterId={}, entityId={}) entered world in zone {} channel {}",
            player.name, characterId, entityId, zoneManager.getZoneName(targetZone), channel.channelId)

        return player
    }

    private fun validateJwt(jwt: String): String? {
        return try {
            val verifier = com.auth0.jwt.JWT.require(
                com.auth0.jwt.algorithms.Algorithm.HMAC256(jwtSecret)
            ).build()
            val decoded = verifier.verify(jwt)
            decoded.subject?.ifBlank { null }
        } catch (e: Exception) {
            logger.debug("JWT validation failed: {}", e.message)
            null
        }
    }

    private fun loadSessionFromRedis(sessionId: String): Map<String, String>? {
        val redis = redisConnection.sync()
        val key = "session:$sessionId"
        val data = redis.hgetall(key)
        return if (data.isNullOrEmpty()) null else data
    }

    private suspend fun loadCharacterFromRedis(characterId: String): Map<String, String>? {
        val redis = redisConnection.sync()
        val key = "character:$characterId"
        val data = redis.hgetall(key)
        return if (data.isNullOrEmpty()) null else data
    }

    private fun sendZoneData(
        ctx: ChannelHandlerContext,
        player: PlayerEntity,
        channel: com.flyagain.world.zone.ZoneChannel
    ) {
        val nearbyEntityIds = channel.getNearbyEntities(player.x, player.z)
        logger.info("sendZoneData for {} (entityId={}): nearby={} entities at ({}, {})",
            player.name, player.entityId, nearbyEntityIds.size, player.x, player.z)
        val spawnMessages = mutableListOf<EntitySpawnMessage>()

        // Seed knownEntities so the game loop visibility check won't re-send these
        player.knownEntities.clear()

        for (entityId in nearbyEntityIds) {
            if (entityId == player.entityId) continue

            // Check if it's a player
            val otherPlayer = entityManager.getPlayer(entityId)
            if (otherPlayer != null) {
                logger.info("  Including player {} (entityId={}) in zone data", otherPlayer.name, entityId)
                spawnMessages.add(EntitySpawnBuilder.buildPlayerSpawn(otherPlayer))
                player.knownEntities.add(entityId)
                continue
            }

            // Check if it's a monster
            val monster = entityManager.getMonster(entityId)
            if (monster != null && monster.isAlive()) {
                spawnMessages.add(EntitySpawnBuilder.buildMonsterSpawn(monster))
                player.knownEntities.add(entityId)
            }
        }

        logger.info("Sending ZoneData to {} with {} entities (myEntityId={})",
            player.name, spawnMessages.size, player.entityId)

        val zoneData = ZoneDataMessage.newBuilder()
            .setZoneId(player.zoneId)
            .setChannelId(player.channelId)
            .setZoneName(zoneManager.getZoneName(player.zoneId))
            .addAllEntities(spawnMessages)
            .setMyEntityId(player.entityId)
            .setPlayerPosition(Position.newBuilder()
                .setX(player.x).setY(player.y).setZ(player.z).build())
            .build()

        ctx.writeAndFlush(Packet(Opcode.ZONE_DATA_VALUE, zoneData.toByteArray()))
    }

    private fun broadcastPlayerSpawn(
        player: PlayerEntity,
        channel: com.flyagain.world.zone.ZoneChannel
    ) {
        val spawnMsg = EntitySpawnBuilder.buildPlayerSpawn(player)
        val packet = Packet(Opcode.ENTITY_SPAWN_VALUE, spawnMsg.toByteArray())

        val nearbyEntityIds = channel.getNearbyEntities(player.x, player.z)
        var broadcastCount = 0
        for (entityId in nearbyEntityIds) {
            if (entityId == player.entityId) continue
            val otherPlayer = channel.getPlayer(entityId) ?: continue
            logger.info("Broadcasting EntitySpawn of {} to player {} (entityId={})",
                player.name, otherPlayer.name, entityId)
            otherPlayer.tcpChannel?.writeAndFlush(packet)
            // Add new player to nearby players' knownEntities
            otherPlayer.knownEntities.add(player.entityId)
            broadcastCount++
        }
        logger.info("broadcastPlayerSpawn for {}: sent to {} nearby players", player.name, broadcastCount)
    }

    private fun sendError(ctx: ChannelHandlerContext, message: String) {
        val response = EnterWorldResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ENTER_WORLD_VALUE, response.toByteArray()))
    }
}
