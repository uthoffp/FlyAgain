package com.flyagain.world.chat

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ChatBroadcastMessage
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

class ChatServiceImpl(
    private val zoneManager: ZoneManager,
    private val entityManager: EntityManager,
    private val broadcastService: BroadcastService
) : ChatService {

    private val logger = LoggerFactory.getLogger(ChatServiceImpl::class.java)

    override fun handleSay(ctx: ChannelHandlerContext, player: PlayerEntity, text: String) {
        val channel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (channel == null) {
            logger.warn("Say chat failed: zone channel not found for player {}", player.name)
            return
        }
        val packet = buildChatBroadcast(player, text, CHANNEL_SAY)
        broadcastService.broadcastChatToNearby(channel, player.x, player.z, packet)
        logger.debug("Say chat from {} in zone {}", player.name, player.zoneId)
    }

    override fun handleShout(ctx: ChannelHandlerContext, player: PlayerEntity, text: String) {
        val channel = zoneManager.getChannel(player.zoneId, player.channelId)
        if (channel == null) {
            logger.warn("Shout chat failed: zone channel not found for player {}", player.name)
            return
        }
        val packet = buildChatBroadcast(player, text, CHANNEL_SHOUT)
        broadcastService.broadcastChatToChannel(channel, packet)
        logger.debug("Shout chat from {} in zone {}", player.name, player.zoneId)
    }

    override fun handleWhisper(ctx: ChannelHandlerContext, player: PlayerEntity, targetName: String, text: String) {
        val target = entityManager.getPlayerByName(targetName)
        if (target == null) {
            sendError(ctx, 404, "Player not found.")
            return
        }
        if (target.entityId == player.entityId) {
            sendError(ctx, 400, "Cannot whisper to yourself.")
            return
        }
        // Send whisper_in to target
        val inPacket = buildChatBroadcast(player, text, CHANNEL_WHISPER_IN)
        broadcastService.sendToPlayer(target, inPacket)
        // Send whisper_out echo to sender (include target name for client routing)
        val outMsg = ChatBroadcastMessage.newBuilder()
            .setSenderName(player.name)
            .setSenderEntityId(player.entityId)
            .setText(text)
            .setChannelType(CHANNEL_WHISPER_OUT)
            .setTimestamp(System.currentTimeMillis())
            .setTargetName(target.name)
            .build()
        ctx.writeAndFlush(Packet(Opcode.CHAT_BROADCAST_VALUE, outMsg.toByteArray()))
        logger.debug("Whisper from {} to {}", player.name, target.name)
    }

    private fun buildChatBroadcast(sender: PlayerEntity, text: String, channelType: Int): Packet {
        val msg = ChatBroadcastMessage.newBuilder()
            .setSenderName(sender.name)
            .setSenderEntityId(sender.entityId)
            .setText(text)
            .setChannelType(channelType)
            .setTimestamp(System.currentTimeMillis())
            .build()
        return Packet(Opcode.CHAT_BROADCAST_VALUE, msg.toByteArray())
    }

    private fun sendError(ctx: ChannelHandlerContext, errorCode: Int, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(Opcode.CHAT_MESSAGE_VALUE)
            .setErrorCode(errorCode)
            .setMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray()))
    }

    companion object {
        const val CHANNEL_SAY = 0
        const val CHANNEL_SHOUT = 1
        const val CHANNEL_WHISPER_IN = 2
        const val CHANNEL_WHISPER_OUT = 3
    }
}
