package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.*
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.EntitySpawnBuilder
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.session.SessionLifecycleManager
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles zone change and channel switch requests.
 *
 * Zone change flow:
 * 1. Save character state to DB (force-flush)
 * 2. Remove player from old zone/channel
 * 3. Broadcast EntityDespawn to old neighbors
 * 4. Add player to new zone/channel
 * 5. Send ZoneData to the player
 * 6. Broadcast EntitySpawn to new neighbors
 */
class ZoneChangeHandler(
    private val entityManager: EntityManager,
    private val zoneManager: ZoneManager,
    private val broadcastService: BroadcastService,
    private val sessionLifecycleManager: SessionLifecycleManager
) {

    private val logger = LoggerFactory.getLogger(ZoneChangeHandler::class.java)

    /**
     * Handle a zone change request from a player.
     */
    suspend fun handleZoneChange(ctx: ChannelHandlerContext, player: PlayerEntity, targetZoneId: Int) {
        if (!zoneManager.zoneExists(targetZoneId)) {
            sendError(ctx, "Zone does not exist.")
            return
        }

        if (player.zoneId == targetZoneId) {
            sendError(ctx, "Already in this zone.")
            return
        }

        if (!zoneManager.isAdjacentZone(player.zoneId, targetZoneId)) {
            logger.warn("Player {} attempted illegal zone transition from {} to {}",
                player.name, player.zoneId, targetZoneId)
            sendError(ctx, "Cannot travel to that zone from here.")
            return
        }

        logger.info("Player {} changing zone from {} to {}",
            player.name, zoneManager.getZoneName(player.zoneId), zoneManager.getZoneName(targetZoneId))

        // 1. Force-flush character state to database
        try {
            sessionLifecycleManager.flushCharacterToDatabase(player)
        } catch (e: Exception) {
            logger.error("Failed to flush character {} during zone change", player.characterId, e)
        }

        // 2. Get old channel and broadcast despawn
        val oldChannel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (oldChannel != null) {
            broadcastService.broadcastEntityDespawn(oldChannel, player.entityId, player.x, player.z)
            // Remove departing player from nearby players' knownEntities
            for (entityId in oldChannel.getNearbyEntities(player.x, player.z)) {
                val otherPlayer = oldChannel.getPlayer(entityId) ?: continue
                otherPlayer.knownEntities.remove(player.entityId)
            }
            oldChannel.removePlayer(player.entityId)
        }

        // 3. Set default spawn position for target zone
        setDefaultSpawnPosition(player, targetZoneId)

        // 4. Add to new zone
        val newChannel = zoneManager.addPlayerToZone(player, targetZoneId)
        if (newChannel == null) {
            logger.error("Failed to add player {} to zone {}", player.name, targetZoneId)
            // Try to return to old zone
            if (oldChannel != null) {
                zoneManager.addPlayerToZone(player, player.zoneId)
            }
            sendError(ctx, "Failed to enter zone.")
            return
        }

        player.markDirty()

        // 5. Send ZoneData to the player
        sendZoneData(ctx, player, newChannel)

        // 6. Broadcast player spawn to new neighbors
        broadcastPlayerSpawn(player, newChannel)

        logger.info("Player {} successfully changed to zone {} channel {}",
            player.name, zoneManager.getZoneName(targetZoneId), newChannel.channelId)
    }

    /**
     * Handle a channel switch request within the same zone.
     */
    suspend fun handleChannelSwitch(ctx: ChannelHandlerContext, player: PlayerEntity, targetChannelId: Int) {
        val targetChannel = zoneManager.getChannel(player.zoneId, targetChannelId)
        if (targetChannel == null) {
            sendError(ctx, "Channel does not exist.")
            return
        }

        if (player.channelId == targetChannelId) {
            sendError(ctx, "Already in this channel.")
            return
        }

        if (!targetChannel.hasCapacity()) {
            sendError(ctx, "Channel is full.")
            return
        }

        // Remove from old channel
        val oldChannel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (oldChannel != null) {
            broadcastService.broadcastEntityDespawn(oldChannel, player.entityId, player.x, player.z)
            oldChannel.removePlayer(player.entityId)
        }

        // Add to new channel
        if (!targetChannel.addPlayer(player)) {
            // Rollback
            oldChannel?.addPlayer(player)
            sendError(ctx, "Failed to switch channel.")
            return
        }

        // Send ZoneData for new channel
        sendZoneData(ctx, player, targetChannel)

        // Broadcast spawn to new channel neighbors
        broadcastPlayerSpawn(player, targetChannel)

        logger.info("Player {} switched to channel {} in zone {}",
            player.name, targetChannelId, zoneManager.getZoneName(player.zoneId))
    }

    /**
     * Send channel list for the current zone to the player.
     */
    fun sendChannelList(ctx: ChannelHandlerContext, player: PlayerEntity) {
        val channels = zoneManager.getChannels(player.zoneId)
        val channelInfos = channels.map { ch ->
            ChannelInfo.newBuilder()
                .setChannelId(ch.channelId)
                .setPlayerCount(ch.getPlayerCount())
                .build()
        }

        val response = ChannelListResponse.newBuilder()
            .setZoneId(player.zoneId)
            .addAllChannels(channelInfos)
            .build()

        ctx.writeAndFlush(Packet(Opcode.CHANNEL_LIST_VALUE, response.toByteArray()))
    }

    private fun setDefaultSpawnPosition(player: PlayerEntity, zoneId: Int) {
        val (spawnX, spawnY, spawnZ) = zoneManager.getSpawnPosition(zoneId)
        player.x = spawnX
        player.y = spawnY
        player.z = spawnZ
        player.isFlying = false
    }

    private fun sendZoneData(
        ctx: ChannelHandlerContext,
        player: PlayerEntity,
        channel: ZoneChannel
    ) {
        val nearbyEntityIds = channel.getNearbyEntities(player.x, player.z)
        val spawnMessages = mutableListOf<EntitySpawnMessage>()

        // Clear and re-seed knownEntities for the new zone/channel
        player.knownEntities.clear()

        for (entityId in nearbyEntityIds) {
            if (entityId == player.entityId) continue

            val otherPlayer = entityManager.getPlayer(entityId)
            if (otherPlayer != null) {
                spawnMessages.add(EntitySpawnBuilder.buildPlayerSpawn(otherPlayer))
                player.knownEntities.add(entityId)
                continue
            }

            val monster = entityManager.getMonster(entityId)
            if (monster != null && monster.isAlive()) {
                spawnMessages.add(EntitySpawnBuilder.buildMonsterSpawn(monster))
                player.knownEntities.add(entityId)
            }
        }

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

    private fun broadcastPlayerSpawn(player: PlayerEntity, channel: ZoneChannel) {
        val spawnMsg = EntitySpawnBuilder.buildPlayerSpawn(player)
        val packet = Packet(Opcode.ENTITY_SPAWN_VALUE, spawnMsg.toByteArray())

        val nearbyEntityIds = channel.getNearbyEntities(player.x, player.z)
        for (entityId in nearbyEntityIds) {
            if (entityId == player.entityId) continue
            val otherPlayer = channel.getPlayer(entityId) ?: continue
            otherPlayer.tcpChannel?.writeAndFlush(packet)
            // Add new player to nearby players' knownEntities
            otherPlayer.knownEntities.add(player.entityId)
        }
    }

    private fun sendError(ctx: ChannelHandlerContext, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(Opcode.ZONE_DATA_VALUE)
            .setErrorCode(400)
            .setMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray()))
    }
}
