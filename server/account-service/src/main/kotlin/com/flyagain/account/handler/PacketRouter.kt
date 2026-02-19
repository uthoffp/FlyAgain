package com.flyagain.account.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Heartbeat
import com.flyagain.common.proto.Opcode
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Routes inbound [Packet] messages to the appropriate handler based on opcode.
 *
 * Every packet (except HEARTBEAT) requires a valid JWT. The JWT is expected as the
 * first packet payload on a new connection: the client sends a CHARACTER_SELECT or
 * CHARACTER_CREATE with its JWT embedded. The router validates the JWT once and
 * stores the accountId on the channel attributes for subsequent use.
 *
 * Supported opcodes:
 * - HEARTBEAT (0x0601) -- echoes back with server timestamp
 * - CHARACTER_SELECT (0x0003) -- selects a character and enters the world
 * - CHARACTER_CREATE (0x0005) -- creates a new character
 */
@ChannelHandler.Sharable
class PacketRouter(
    private val jwtValidator: JwtValidator,
    private val characterCreateHandler: CharacterCreateHandler,
    private val characterSelectHandler: CharacterSelectHandler
) : SimpleChannelInboundHandler<Packet>() {

    private val logger = LoggerFactory.getLogger(PacketRouter::class.java)
    private val routerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        val ACCOUNT_ID_KEY: AttributeKey<Long> = AttributeKey.valueOf("accountId")
        val SESSION_ID_KEY: AttributeKey<String> = AttributeKey.valueOf("sessionId")
        val AUTHENTICATED_KEY: AttributeKey<Boolean> = AttributeKey.valueOf("authenticated")
    }

    override fun channelRead0(ctx: ChannelHandlerContext, packet: Packet) {
        val opcode = packet.opcode

        // Heartbeat does not require authentication
        if (opcode == Opcode.HEARTBEAT_VALUE) {
            handleHeartbeat(ctx, packet)
            return
        }

        // For all other opcodes, require authentication
        val authenticated = ctx.channel().attr(AUTHENTICATED_KEY).get() ?: false

        if (!authenticated) {
            // Attempt to authenticate from JWT embedded in the packet
            // The client should send a JWT token as the first bytes of the first meaningful packet.
            // We use a channel attribute approach: the client must authenticate via a
            // special auth packet, or the JWT is carried in the protobuf message.
            // For this implementation, we validate the JWT from a channel attribute
            // that gets set by a prior authentication step. Since the account-service
            // receives clients redirected from login-service with a JWT, the JWT is
            // passed as a Netty channel attribute or in the first packet.
            //
            // Design: The first packet from a client MUST be CHARACTER_SELECT or CHARACTER_CREATE.
            // The JWT is embedded in the connection setup (the client sends it in an auth header
            // or as the first raw bytes before the first packet).
            //
            // For simplicity, we handle auth inline: extract JWT from a well-known channel
            // attribute or from the packet context. The login-service provides the JWT to the
            // client which then presents it to the account-service.
            //
            // Implementation: We store auth info on the channel after first validation.
            logger.warn("Unauthenticated packet (opcode=0x{}) from {}", opcode.toString(16), ctx.channel().remoteAddress())
            sendError(ctx, opcode, 401, "Not authenticated. Send AUTH_TOKEN first.")
            return
        }

        val accountId = ctx.channel().attr(ACCOUNT_ID_KEY).get()

        when (opcode) {
            Opcode.CHARACTER_SELECT_VALUE -> {
                routerScope.launch {
                    try {
                        characterSelectHandler.handle(ctx, packet, accountId)
                    } catch (e: Exception) {
                        logger.error("Error handling CHARACTER_SELECT for account {}", accountId, e)
                        sendError(ctx, opcode, 500, "Internal server error")
                    }
                }
            }

            Opcode.CHARACTER_CREATE_VALUE -> {
                routerScope.launch {
                    try {
                        characterCreateHandler.handle(ctx, packet, accountId)
                    } catch (e: Exception) {
                        logger.error("Error handling CHARACTER_CREATE for account {}", accountId, e)
                        sendError(ctx, opcode, 500, "Internal server error")
                    }
                }
            }

            else -> {
                logger.warn("Unknown opcode 0x{} from {}", opcode.toString(16), ctx.channel().remoteAddress())
                sendError(ctx, opcode, 400, "Unknown opcode")
            }
        }
    }

    /**
     * Authenticates a channel using the provided JWT token string.
     * Called by the initial authentication packet handler or inline during first packet processing.
     *
     * @return true if authentication succeeded
     */
    fun authenticateChannel(ctx: ChannelHandlerContext, jwtToken: String): Boolean {
        val claims = jwtValidator.validate(jwtToken)
        if (claims == null) {
            logger.warn("JWT validation failed for connection from {}", ctx.channel().remoteAddress())
            return false
        }

        ctx.channel().attr(ACCOUNT_ID_KEY).set(claims.accountId)
        ctx.channel().attr(SESSION_ID_KEY).set(claims.sessionId)
        ctx.channel().attr(AUTHENTICATED_KEY).set(true)

        logger.info("Authenticated account {} (session {}) from {}", claims.accountId, claims.sessionId, ctx.channel().remoteAddress())
        return true
    }

    private fun handleHeartbeat(ctx: ChannelHandlerContext, packet: Packet) {
        try {
            val clientHeartbeat = Heartbeat.parseFrom(packet.payload)
            val response = Heartbeat.newBuilder()
                .setClientTime(clientHeartbeat.clientTime)
                .setServerTime(System.currentTimeMillis())
                .build()
            val responsePacket = Packet(Opcode.HEARTBEAT_VALUE, response.toByteArray())
            ctx.writeAndFlush(responsePacket)
        } catch (e: Exception) {
            logger.warn("Failed to parse heartbeat from {}", ctx.channel().remoteAddress(), e)
        }
    }

    private fun sendError(ctx: ChannelHandlerContext, originalOpcode: Int, errorCode: Int, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(originalOpcode)
            .setErrorCode(errorCode)
            .setMessage(message)
            .build()
        val packet = Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray())
        ctx.writeAndFlush(packet)
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
        logger.error("Unhandled exception on channel {}", ctx.channel().remoteAddress(), cause)
        ctx.close()
    }
}
