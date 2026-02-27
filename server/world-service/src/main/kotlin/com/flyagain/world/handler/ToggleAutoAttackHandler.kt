package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.ToggleAutoAttackRequest
import com.flyagain.common.proto.ToggleAutoAttackResponse
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.zone.ZoneManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles auto-attack toggle requests from clients (opcode 0x0206).
 *
 * When enabled, the server's game loop will automatically execute
 * basic attacks against the player's current target at the configured
 * attack speed. Validates that the target exists and is attackable.
 */
class ToggleAutoAttackHandler(
    private val entityManager: EntityManager,
    private val zoneManager: ZoneManager
) {

    private val logger = LoggerFactory.getLogger(ToggleAutoAttackHandler::class.java)

    fun handle(ctx: ChannelHandlerContext, player: PlayerEntity, request: ToggleAutoAttackRequest) {
        val enable = request.enable
        val targetId = request.targetEntityId

        if (!enable) {
            player.autoAttacking = false
            val response = ToggleAutoAttackResponse.newBuilder()
                .setAutoAttacking(false)
                .setTargetEntityId(player.targetEntityId ?: 0L)
                .build()
            ctx.writeAndFlush(Packet(Opcode.TOGGLE_AUTO_ATTACK_VALUE, response.toByteArray()))
            logger.debug("Player {} disabled auto-attack", player.name)
            return
        }

        // If a target ID was provided, set it
        if (targetId != 0L) {
            player.targetEntityId = targetId
        }

        val currentTarget = player.targetEntityId
        if (currentTarget == null) {
            val response = ToggleAutoAttackResponse.newBuilder()
                .setAutoAttacking(false)
                .setTargetEntityId(0L)
                .build()
            ctx.writeAndFlush(Packet(Opcode.TOGGLE_AUTO_ATTACK_VALUE, response.toByteArray()))
            logger.debug("Player {} tried to auto-attack without target", player.name)
            return
        }

        // Validate target exists and is alive
        val channel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (channel == null) {
            player.autoAttacking = false
            return
        }

        val monster = channel.getMonster(currentTarget)
        if (monster != null && monster.isAlive()) {
            player.autoAttacking = true
            val response = ToggleAutoAttackResponse.newBuilder()
                .setAutoAttacking(true)
                .setTargetEntityId(currentTarget)
                .build()
            ctx.writeAndFlush(Packet(Opcode.TOGGLE_AUTO_ATTACK_VALUE, response.toByteArray()))
            logger.debug("Player {} enabled auto-attack on {} (entityId={})", player.name, monster.name, currentTarget)
            return
        }

        // Target not valid
        player.autoAttacking = false
        player.targetEntityId = null
        val response = ToggleAutoAttackResponse.newBuilder()
            .setAutoAttacking(false)
            .setTargetEntityId(0L)
            .build()
        ctx.writeAndFlush(Packet(Opcode.TOGGLE_AUTO_ATTACK_VALUE, response.toByteArray()))
    }
}
