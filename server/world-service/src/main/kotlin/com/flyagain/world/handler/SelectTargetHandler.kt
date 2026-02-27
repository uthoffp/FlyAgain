package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.SelectTargetRequest
import com.flyagain.common.proto.SelectTargetResponse
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.zone.ZoneManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles target selection requests from clients (opcode 0x0201).
 *
 * Validates that the target entity exists, is alive, and is in the
 * same zone/channel as the requesting player. On success, sets the
 * player's targetEntityId and returns target info (HP, name, level).
 * A target_entity_id of 0 clears the current target.
 */
class SelectTargetHandler(
    private val entityManager: EntityManager,
    private val zoneManager: ZoneManager
) {

    private val logger = LoggerFactory.getLogger(SelectTargetHandler::class.java)

    fun handle(ctx: ChannelHandlerContext, player: PlayerEntity, request: SelectTargetRequest) {
        val targetId = request.targetEntityId

        // Clear target if 0
        if (targetId == 0L) {
            player.targetEntityId = null
            player.autoAttacking = false
            val response = SelectTargetResponse.newBuilder()
                .setSuccess(true)
                .setTargetEntityId(0)
                .build()
            ctx.writeAndFlush(Packet(Opcode.SELECT_TARGET_VALUE, response.toByteArray()))
            logger.debug("Player {} cleared target", player.name)
            return
        }

        // Check if target is a monster in the same channel
        val channel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (channel == null) {
            sendError(ctx, targetId, "Not in a valid zone channel")
            return
        }

        val monster = channel.getMonster(targetId)
        if (monster != null) {
            if (!monster.isAlive()) {
                sendError(ctx, targetId, "Target is dead")
                return
            }
            player.targetEntityId = targetId
            val response = SelectTargetResponse.newBuilder()
                .setSuccess(true)
                .setTargetEntityId(targetId)
                .setTargetHp(monster.hp)
                .setTargetMaxHp(monster.maxHp)
                .setTargetName(monster.name)
                .setTargetLevel(monster.level)
                .build()
            ctx.writeAndFlush(Packet(Opcode.SELECT_TARGET_VALUE, response.toByteArray()))
            logger.debug("Player {} selected target: {} (entityId={})", player.name, monster.name, targetId)
            return
        }

        // Check if target is another player in the same channel
        val targetPlayer = channel.getPlayer(targetId)
        if (targetPlayer != null) {
            player.targetEntityId = targetId
            val response = SelectTargetResponse.newBuilder()
                .setSuccess(true)
                .setTargetEntityId(targetId)
                .setTargetHp(targetPlayer.hp)
                .setTargetMaxHp(targetPlayer.maxHp)
                .setTargetName(targetPlayer.name)
                .setTargetLevel(targetPlayer.level)
                .build()
            ctx.writeAndFlush(Packet(Opcode.SELECT_TARGET_VALUE, response.toByteArray()))
            logger.debug("Player {} selected player target: {} (entityId={})", player.name, targetPlayer.name, targetId)
            return
        }

        sendError(ctx, targetId, "Target not found")
    }

    private fun sendError(ctx: ChannelHandlerContext, targetId: Long, reason: String) {
        val response = SelectTargetResponse.newBuilder()
            .setSuccess(false)
            .setTargetEntityId(targetId)
            .build()
        ctx.writeAndFlush(Packet(Opcode.SELECT_TARGET_VALUE, response.toByteArray()))
    }
}
