package com.flyagain.login.handler

import com.flyagain.common.network.HeartbeatTracker
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Heartbeat
import com.flyagain.common.proto.LoginRequest
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.RegisterRequest
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
    private val coroutineScope: CoroutineScope,
    private val heartbeatTracker: HeartbeatTracker
) : SimpleChannelInboundHandler<Packet>() {

    private val logger = LoggerFactory.getLogger(PacketRouter::class.java)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Packet) {
        when (msg.opcode) {
            Opcode.LOGIN_REQUEST_VALUE -> {
                val request = try {
                    LoginRequest.parseFrom(msg.payload)
                } catch (e: Exception) {
                    logger.warn("Failed to parse LOGIN_REQUEST from {}: {}", ctx.channel().remoteAddress(), e.message)
                    sendError(ctx, msg.opcode, 400, "Malformed request.")
                    return
                }
                coroutineScope.launch {
                    try {
                        loginHandler.handle(ctx, request)
                    } catch (e: Exception) {
                        logger.error("Unhandled exception in LoginHandler: {}", e.message, e)
                        sendError(ctx, msg.opcode, 500, "Internal server error.")
                    }
                }
            }

            Opcode.REGISTER_REQUEST_VALUE -> {
                val request = try {
                    RegisterRequest.parseFrom(msg.payload)
                } catch (e: Exception) {
                    logger.warn("Failed to parse REGISTER_REQUEST from {}: {}", ctx.channel().remoteAddress(), e.message)
                    sendError(ctx, msg.opcode, 400, "Malformed request.")
                    return
                }
                coroutineScope.launch {
                    try {
                        registerHandler.handle(ctx, request)
                    } catch (e: Exception) {
                        logger.error("Unhandled exception in RegisterHandler: {}", e.message, e)
                        sendError(ctx, msg.opcode, 500, "Internal server error.")
                    }
                }
            }

            Opcode.HEARTBEAT_VALUE -> {
                handleHeartbeat(ctx, msg)
            }

            else -> {
                logger.warn("Unhandled opcode 0x{} from {}", msg.opcode.toString(16), ctx.channel().remoteAddress())
                sendError(ctx, msg.opcode, 400, "Unsupported operation for login service.")
            }
        }
    }

    /**
     * Handle heartbeat by recording it in the tracker and echoing back with server timestamp.
     */
    private fun handleHeartbeat(ctx: ChannelHandlerContext, packet: Packet) {
        try {
            heartbeatTracker.recordHeartbeat(ctx.channel())
            val heartbeat = Heartbeat.parseFrom(packet.payload)
            val response = Heartbeat.newBuilder()
                .setClientTime(heartbeat.clientTime)
                .setServerTime(System.currentTimeMillis())
                .build()
            ctx.writeAndFlush(Packet(Opcode.HEARTBEAT_VALUE, response.toByteArray()))
        } catch (e: Exception) {
            logger.warn("Failed to parse heartbeat from {}", ctx.channel().remoteAddress(), e)
        }
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
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray()))
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
        heartbeatTracker.register(ctx.channel())
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        logger.info("Client disconnected: {}", ctx.channel().remoteAddress())
        heartbeatTracker.unregister(ctx.channel())
        super.channelInactive(ctx)
    }
}
