package com.flyagain.world.chat

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ChatMessageRequest
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

class ChatMessageHandler(
    private val chatService: ChatService,
    private val sanitizer: ChatMessageSanitizer
) {
    private val logger = LoggerFactory.getLogger(ChatMessageHandler::class.java)

    companion object {
        private const val RATE_LIMIT_WINDOW_MS = 10_000L
        private const val RATE_LIMIT_MAX_MESSAGES = 10
    }

    fun handle(ctx: ChannelHandlerContext, player: PlayerEntity, packet: Packet) {
        val request = try {
            ChatMessageRequest.parseFrom(packet.payload)
        } catch (e: Exception) {
            logger.warn("Failed to parse CHAT_MESSAGE from player {}: {}", player.name, e.message)
            sendError(ctx, 400, "Malformed request.")
            return
        }
        val sanitizedText = sanitizer.sanitize(request.text)
        if (sanitizedText == null) {
            sendError(ctx, 400, "Invalid message.")
            return
        }
        if (!checkRateLimit(player)) {
            sendError(ctx, 429, "Rate limit exceeded.")
            return
        }
        when (request.channelType) {
            0 -> chatService.handleSay(ctx, player, sanitizedText)
            1 -> chatService.handleShout(ctx, player, sanitizedText)
            2 -> {
                if (request.targetName.isBlank()) {
                    sendError(ctx, 400, "Target name required for whisper.")
                    return
                }
                chatService.handleWhisper(ctx, player, request.targetName, sanitizedText)
            }
            else -> sendError(ctx, 400, "Invalid channel type.")
        }
    }

    private fun checkRateLimit(player: PlayerEntity): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = player.chatMessageTimestamps
        while (timestamps.isNotEmpty() && now - timestamps.first() > RATE_LIMIT_WINDOW_MS) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= RATE_LIMIT_MAX_MESSAGES) return false
        timestamps.addLast(now)
        return true
    }

    private fun sendError(ctx: ChannelHandlerContext, errorCode: Int, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(Opcode.CHAT_MESSAGE_VALUE)
            .setErrorCode(errorCode)
            .setMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray()))
    }
}
