package com.flyagain.world.network

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.*
import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.zone.ZoneChannel
import io.netty.channel.Channel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages broadcasting game state updates to nearby players.
 *
 * Position updates are queued during the game loop tick and flushed
 * at the end of each tick to batch network I/O.
 */
class BroadcastService(
    private val entityManager: EntityManager
) {

    private val logger = LoggerFactory.getLogger(BroadcastService::class.java)

    private data class PendingPositionUpdate(
        val player: PlayerEntity,
        val channel: ZoneChannel
    )

    private val pendingPositionUpdates = ConcurrentLinkedQueue<PendingPositionUpdate>()

    /**
     * Queue a position update for broadcast at the end of the tick.
     */
    fun queuePositionUpdate(player: PlayerEntity, channel: ZoneChannel) {
        pendingPositionUpdates.add(PendingPositionUpdate(player, channel))
    }

    /**
     * Flush all pending position updates as broadcasts.
     * Called at the end of each game loop tick.
     */
    fun flushPendingUpdates() {
        var update = pendingPositionUpdates.poll()
        while (update != null) {
            broadcastPositionUpdate(update.player, update.channel)
            update = pendingPositionUpdates.poll()
        }
    }

    /**
     * Broadcast a position update for an entity to all nearby players.
     */
    private fun broadcastPositionUpdate(player: PlayerEntity, channel: ZoneChannel) {
        val positionUpdate = EntityPositionUpdate.newBuilder()
            .setEntityId(player.entityId)
            .setPosition(Position.newBuilder()
                .setX(player.x)
                .setY(player.y)
                .setZ(player.z)
                .build())
            .setRotation(player.rotation)
            .setIsMoving(player.isMoving)
            .setIsFlying(player.isFlying)
            .build()

        val packet = Packet(Opcode.ENTITY_POSITION_VALUE, positionUpdate.toByteArray())
        sendToNearby(channel, player.x, player.z, player.entityId, packet)
    }

    /**
     * Broadcast an entity spawn to all nearby players in the channel.
     */
    fun broadcastEntitySpawn(channel: ZoneChannel, monster: MonsterEntity) {
        val spawnMsg = EntitySpawnMessage.newBuilder()
            .setEntityId(monster.entityId)
            .setEntityType(1) // monster
            .setName(monster.name)
            .setPosition(Position.newBuilder()
                .setX(monster.x)
                .setY(monster.y)
                .setZ(monster.z)
                .build())
            .setLevel(monster.level)
            .setHp(monster.hp)
            .setMaxHp(monster.maxHp)
            .build()

        val packet = Packet(Opcode.ENTITY_SPAWN_VALUE, spawnMsg.toByteArray())
        sendToNearby(channel, monster.x, monster.z, -1, packet)
    }

    /**
     * Broadcast an entity despawn to specific entity IDs.
     */
    fun broadcastEntityDespawn(channel: ZoneChannel, entityId: Long, x: Float, z: Float) {
        val despawnMsg = EntityDespawnMessage.newBuilder()
            .setEntityId(entityId)
            .build()

        val packet = Packet(Opcode.ENTITY_DESPAWN_VALUE, despawnMsg.toByteArray())
        sendToNearby(channel, x, z, entityId, packet)
    }

    /**
     * Broadcast a damage event to all nearby players.
     */
    fun broadcastDamageEvent(channel: ZoneChannel, damageResult: CombatEngine.DamageResult) {
        // Find position to broadcast from (use target position)
        val targetPlayer = entityManager.getPlayer(damageResult.targetEntityId)
        val targetMonster = entityManager.getMonster(damageResult.targetEntityId)

        val (x, z) = when {
            targetPlayer != null -> targetPlayer.x to targetPlayer.z
            targetMonster != null -> targetMonster.x to targetMonster.z
            else -> return
        }

        val damageEvent = DamageEvent.newBuilder()
            .setAttackerEntityId(damageResult.attackerEntityId)
            .setTargetEntityId(damageResult.targetEntityId)
            .setDamage(damageResult.damage)
            .setIsCritical(damageResult.isCritical)
            .build()

        val packet = Packet(Opcode.DAMAGE_EVENT_VALUE, damageEvent.toByteArray())
        sendToNearby(channel, x, z, -1, packet)

        // If target was killed, broadcast death
        if (damageResult.targetKilled) {
            broadcastEntityDeath(channel, damageResult.targetEntityId, damageResult.attackerEntityId, x, z)
        }
    }

    /**
     * Broadcast entity death to nearby players.
     */
    private fun broadcastEntityDeath(
        channel: ZoneChannel,
        entityId: Long,
        killerEntityId: Long,
        x: Float,
        z: Float
    ) {
        val deathMsg = EntityDeath.newBuilder()
            .setEntityId(entityId)
            .setKillerEntityId(killerEntityId)
            .build()

        val packet = Packet(Opcode.ENTITY_DEATH_VALUE, deathMsg.toByteArray())
        sendToNearby(channel, x, z, -1, packet)
    }

    // TCP channels that had write() calls during this tick and need a final flush().
    // Using IdentityHashMap avoids hashCode/equals overhead on Netty Channel objects.
    private val channelsToFlush = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<Channel, Boolean>()
    )

    /**
     * Send a packet to all players near a position, excluding one entity.
     * Uses write() (no syscall) per recipient; the actual flush is deferred
     * to [flushNetworkWrites] at the end of the tick, batching syscalls.
     */
    private fun sendToNearby(
        channel: ZoneChannel,
        x: Float,
        z: Float,
        excludeEntityId: Long,
        packet: Packet
    ) {
        val nearbyEntityIds = channel.getNearbyEntities(x, z)
        for (entityId in nearbyEntityIds) {
            if (entityId == excludeEntityId) continue
            val player = channel.getPlayer(entityId) ?: continue
            val tcp = player.tcpChannel ?: continue
            tcp.write(packet)
            channelsToFlush.add(tcp)
        }
    }

    /**
     * Flush all TCP channels that had writes queued during this tick.
     * Must be called once at the very end of each game loop tick.
     * Batches syscalls: one flush per player per tick instead of one per packet.
     */
    fun flushNetworkWrites() {
        for (ch in channelsToFlush) {
            ch.flush()
        }
        channelsToFlush.clear()
    }

    /**
     * Send a packet to a specific player (immediate flush for one-off sends
     * outside the normal tick cycle, e.g. disconnect notification).
     */
    fun sendToPlayer(player: PlayerEntity, packet: Packet) {
        player.tcpChannel?.writeAndFlush(packet)
    }
}
