package com.flyagain.world.handler

import com.flyagain.common.network.HeartbeatTracker
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.*
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.session.SessionLifecycleManager
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Routes inbound TCP packets to the appropriate handler in the world service.
 *
 * The first packet from a client must be ENTER_WORLD (0x0004) which authenticates
 * the connection and creates a PlayerEntity. Subsequent packets are routed based
 * on opcode.
 *
 * Supported opcodes:
 * - ENTER_WORLD (0x0004) - initial world entry (must be first)
 * - HEARTBEAT (0x0601) - keep-alive
 * - ZONE_DATA (0x0701) - zone change request
 * - CHANNEL_SWITCH (0x0702) - channel switch
 * - CHANNEL_LIST (0x0703) - query available channels
 */
@ChannelHandler.Sharable
class PacketRouter(
    private val enterWorldHandler: EnterWorldHandler,
    private val zoneChangeHandler: ZoneChangeHandler,
    private val entityManager: EntityManager,
    private val sessionLifecycleManager: SessionLifecycleManager,
    private val heartbeatTracker: HeartbeatTracker,
    private val coroutineScope: CoroutineScope
) : SimpleChannelInboundHandler<Packet>() {

    private val logger = LoggerFactory.getLogger(PacketRouter::class.java)

    companion object {
        val PLAYER_ENTITY_KEY: AttributeKey<PlayerEntity> = AttributeKey.valueOf("playerEntity")
        val AUTHENTICATED_KEY: AttributeKey<Boolean> = AttributeKey.valueOf("worldAuthenticated")

        private const val ZONE_CHANGE_COOLDOWN_MS = 5000L
        private const val CHANNEL_SWITCH_COOLDOWN_MS = 3000L
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: Packet) {
        val opcode = msg.opcode

        // Heartbeat does not require world authentication
        if (opcode == Opcode.HEARTBEAT_VALUE) {
            handleHeartbeat(ctx, msg)
            return
        }

        // First packet must be ENTER_WORLD
        if (opcode == Opcode.ENTER_WORLD_VALUE) {
            handleEnterWorld(ctx, msg)
            return
        }

        // All other opcodes require an authenticated player entity
        val player = ctx.channel().attr(PLAYER_ENTITY_KEY).get()
        if (player == null) {
            logger.warn("Unauthenticated packet (opcode=0x{}) from {}", opcode.toString(16), ctx.channel().remoteAddress())
            sendError(ctx, opcode, 401, "Not authenticated. Send ENTER_WORLD first.")
            return
        }

        when (opcode) {
            Opcode.ZONE_DATA_VALUE -> {
                // Rate limit zone changes
                val now = System.currentTimeMillis()
                if (now - player.lastZoneChangeTime < ZONE_CHANGE_COOLDOWN_MS) {
                    sendError(ctx, opcode, 429, "Zone change on cooldown.")
                    return
                }
                player.lastZoneChangeTime = now

                coroutineScope.launch {
                    try {
                        val request = ZoneChangeRequest.parseFrom(msg.payload)
                        zoneChangeHandler.handleZoneChange(ctx, player, request.targetZoneId)
                    } catch (e: Exception) {
                        logger.error("Error handling zone change for player {}", player.name, e)
                        sendError(ctx, opcode, 500, "Internal server error.")
                    }
                }
            }

            Opcode.CHANNEL_SWITCH_VALUE -> {
                // Rate limit channel switches
                val now = System.currentTimeMillis()
                if (now - player.lastChannelSwitchTime < CHANNEL_SWITCH_COOLDOWN_MS) {
                    sendError(ctx, opcode, 429, "Channel switch on cooldown.")
                    return
                }
                player.lastChannelSwitchTime = now

                coroutineScope.launch {
                    try {
                        val request = ChannelSwitchRequest.parseFrom(msg.payload)
                        zoneChangeHandler.handleChannelSwitch(ctx, player, request.targetChannelId)
                    } catch (e: Exception) {
                        logger.error("Error handling channel switch for player {}", player.name, e)
                        sendError(ctx, opcode, 500, "Internal server error.")
                    }
                }
            }

            Opcode.CHANNEL_LIST_VALUE -> {
                zoneChangeHandler.sendChannelList(ctx, player)
            }

            else -> {
                logger.debug("Unhandled opcode 0x{} from player {}", opcode.toString(16), player.name)
                sendError(ctx, opcode, 400, "Unsupported operation.")
            }
        }
    }

    private fun handleEnterWorld(ctx: ChannelHandlerContext, msg: Packet) {
        val alreadyAuthenticated = ctx.channel().attr(AUTHENTICATED_KEY).get() ?: false
        if (alreadyAuthenticated) {
            sendError(ctx, msg.opcode, 400, "Already in world.")
            return
        }

        val request = try {
            EnterWorldRequest.parseFrom(msg.payload)
        } catch (e: Exception) {
            logger.warn("Failed to parse ENTER_WORLD from {}: {}", ctx.channel().remoteAddress(), e.message)
            sendError(ctx, msg.opcode, 400, "Malformed request.")
            return
        }

        coroutineScope.launch {
            try {
                val player = enterWorldHandler.handle(ctx, request)
                if (player != null) {
                    ctx.channel().attr(PLAYER_ENTITY_KEY).set(player)
                    ctx.channel().attr(AUTHENTICATED_KEY).set(true)
                    logger.info("Player {} authenticated on world service", player.name)
                }
            } catch (e: Exception) {
                logger.error("Unhandled exception in EnterWorldHandler", e)
                sendError(ctx, msg.opcode, 500, "Internal server error.")
            }
        }
    }

    private fun handleHeartbeat(ctx: ChannelHandlerContext, packet: Packet) {
        try {
            heartbeatTracker.recordHeartbeat(ctx.channel())
            val heartbeat = Heartbeat.parseFrom(packet.payload)
            val response = Heartbeat.newBuilder()
                .setClientTime(heartbeat.clientTime)
                .setServerTime(System.currentTimeMillis())
                .build()
            ctx.writeAndFlush(Packet(Opcode.HEARTBEAT_VALUE, response.toByteArray()))

            // Also update player heartbeat timestamp
            val player = ctx.channel().attr(PLAYER_ENTITY_KEY).get()
            player?.lastHeartbeat = System.currentTimeMillis()
        } catch (e: Exception) {
            logger.warn("Failed to parse heartbeat from {}", ctx.channel().remoteAddress())
        }
    }

    private fun sendError(ctx: ChannelHandlerContext, originalOpcode: Int, errorCode: Int, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(originalOpcode)
            .setErrorCode(errorCode)
            .setMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray()))
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        logger.debug("World client connected: {}", ctx.channel().remoteAddress())
        heartbeatTracker.register(ctx.channel())
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        logger.info("World client disconnected: {}", ctx.channel().remoteAddress())
        heartbeatTracker.unregister(ctx.channel())

        // Handle player disconnect
        val player = ctx.channel().attr(PLAYER_ENTITY_KEY).get()
        if (player != null) {
            coroutineScope.launch {
                try {
                    sessionLifecycleManager.handleDisconnect(player)
                } catch (e: Exception) {
                    logger.error("Error during disconnect handling for player {}", player.name, e)
                }
            }
        }

        super.channelInactive(ctx)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            logger.info("Closing idle world connection from {}", ctx.channel().remoteAddress())
            ctx.close()
        } else {
            super.userEventTriggered(ctx, evt)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("PacketRouter error from {}: {}", ctx.channel().remoteAddress(), cause.message)
        ctx.close()
    }
}
