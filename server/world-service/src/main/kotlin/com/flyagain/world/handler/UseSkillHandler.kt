package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.UseSkillRequest
import com.flyagain.common.proto.UseSkillResponse
import com.flyagain.world.combat.SkillSystem
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles skill usage requests from clients (opcode 0x0202).
 *
 * Delegates to [SkillSystem] for full server-authoritative validation:
 * skill existence, class/level requirements, MP cost, cooldown, target
 * validity, and range. On success, broadcasts a DamageEvent to nearby
 * players via [BroadcastService].
 */
class UseSkillHandler(
    private val skillSystem: SkillSystem,
    private val broadcastService: BroadcastService,
    private val zoneManager: ZoneManager
) {

    private val logger = LoggerFactory.getLogger(UseSkillHandler::class.java)

    fun handle(ctx: ChannelHandlerContext, player: PlayerEntity, request: UseSkillRequest) {
        val skillId = request.skillId
        val targetEntityId = request.targetEntityId

        // Validate player is alive
        if (player.hp <= 0) {
            sendError(ctx, skillId, "Cannot use skills while dead")
            return
        }

        val result = skillSystem.useSkill(player, skillId, targetEntityId)

        when (result) {
            is SkillSystem.SkillResult.Success -> {
                // Send success to the caster
                val response = UseSkillResponse.newBuilder()
                    .setSuccess(true)
                    .setSkillId(skillId)
                    .build()
                ctx.writeAndFlush(Packet(Opcode.USE_SKILL_VALUE, response.toByteArray()))

                // Broadcast damage event to nearby players
                val channel = zoneManager.getChannel(player.zoneId, player.channelId)
                if (channel != null) {
                    broadcastService.broadcastDamageEvent(channel, result.damageResult)
                }
            }

            is SkillSystem.SkillResult.Error -> {
                sendError(ctx, skillId, result.reason)
            }
        }
    }

    private fun sendError(ctx: ChannelHandlerContext, skillId: Int, reason: String) {
        val response = UseSkillResponse.newBuilder()
            .setSuccess(false)
            .setSkillId(skillId)
            .setErrorMessage(reason)
            .build()
        ctx.writeAndFlush(Packet(Opcode.USE_SKILL_VALUE, response.toByteArray()))
    }
}
