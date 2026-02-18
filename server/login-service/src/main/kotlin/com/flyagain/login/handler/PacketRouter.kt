package com.flyagain.login.handler

import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Heartbeat
import com.flyagain.common.proto.LoginRequest
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.RegisterRequest
import com.flyagain.login.network.Packet
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.timeout.IdleStateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Routes decoded Packet objects to the appropriate business-logic handler
 * based on the packet's opcode.
 *
 * Supported opcodes:
 *   - LOGIN_REQUEST (0x0001) -> LoginHandler
 *   - REGISTER_REQUEST (0x0006) -> RegisterHandler
 *   - HEARTBEAT (0x0601) -> echo back with server timestamp
 *
 * Unknown opcodes receive an ERROR_RESPONSE.
 *
 * This handler is sharable and uses coroutines for async handler execution.
 */
@ChannelHandler.Sharable
class PacketRouter(
    private val loginHandler: LoginHandler,
    private val registerHandler: RegisterHandler,
    private val coroutineScope: CoroutineScope
) : SimpleChannelInboundHandler<Packet>() {

    private val logger = LoggerFactory.getLogger(PacketRouter::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Packet) {
        when (msg.opcode) {
            Opcode.LOGIN_REQUEST -> {
                val request = msg.message as LoginRequest
                coroutineScope.launch {
                    try {
                        loginHandler.handle(ctx, request)
                    } catch (e: Exception) {
                        logger.error("Unhandled exception in LoginHandler: {}", e.message, e)
                        sendError(ctx, Opcode.LOGIN_REQUEST.number, 500, "Internal server error.")
                    }
                }
            }

            Opcode.REGISTER_REQUEST -> {
                val request = msg.message as RegisterRequest
                coroutineScope.launch {
                    try {
                        registerHandler.handle(ctx, request)
                    } catch (e: Exception) {
                        logger.error("Unhandled exception in RegisterHandler: {}", e.message, e)
                        sendError(ctx, Opcode.REGISTER_REQUEST.number, 500, "Internal server error.")
                    }
                }
            }

            Opcode.HEARTBEAT -> {
                handleHeartbeat(ctx, msg.message as Heartbeat)
            }

            else -> {
                logger.warn("Unhandled opcode {} from {}", msg.opcode, ctx.channel().remoteAddress())
                sendError(ctx, msg.opcode.number, 400, "Unsupported operation for login service.")
            }
        }
    }

    /**
     * Handle heartbeat by echoing back with server timestamp.
     */
    private fun handleHeartbeat(ctx: ChannelHandlerContext, heartbeat: Heartbeat) {
        val response = Heartbeat.newBuilder()
            .setClientTime(heartbeat.clientTime)
            .setServerTime(System.currentTimeMillis())
            .build()
        ctx.writeAndFlush(Packet(Opcode.HEARTBEAT, response))
    }

    /**
     * Send an error response to the client.
     */
    private fun sendError(ctx: ChannelHandlerContext, originalOpcode: Int, errorCode: Int, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(originalOpcode)
            .setErrorCode(errorCode)
            .setMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE, error))
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            logger.info("Closing idle connection from {}", ctx.channel().remoteAddress())
            ctx.close()
        } else {
            super.userEventTriggered(ctx, evt)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("PacketRouter error from {}: {}", ctx.channel().remoteAddress(), cause.message)
        ctx.close()
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        logger.info("Client connected: {}", ctx.channel().remoteAddress())
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        logger.info("Client disconnected: {}", ctx.channel().remoteAddress())
        super.channelInactive(ctx)
    }
}
