package com.flyagain.common.network

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.timeout.IdleStateHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Reusable Netty TCP server for all FlyAgain services.
 *
 * Wire format (each frame):
 *   [4-byte length prefix][2-byte opcode][protobuf payload]
 *
 * The length prefix covers opcode + payload (i.e., does NOT include itself).
 *
 * Pipeline per connection:
 *   1. [ConnectionLimiter] (shared) — enforces connection limits
 *   2. [IdleStateHandler] — disconnects idle clients after [IDLE_TIMEOUT_SECONDS]
 *   3. [LengthFieldBasedFrameDecoder] — strips the 4-byte length prefix
 *   4. [LengthFieldPrepender] — prepends 4-byte length on outbound
 *   5. [PacketDecoder] — parses opcode + payload from each frame
 *   6. [PacketEncoder] — encodes outbound [Packet] objects
 *   7. packetRouter — service-specific business logic handler
 *
 * @param port TCP port to bind to.
 * @param maxConnections Maximum total concurrent connections.
 * @param maxConnectionsPerIp Maximum concurrent connections from a single IP.
 * @param packetRouter The service-specific handler that routes decoded [Packet]s to business logic.
 * @param serviceName Human-readable service name for log messages.
 */
class TcpServer(
    private val port: Int,
    private val maxConnections: Int,
    private val maxConnectionsPerIp: Int,
    private val packetRouter: ChannelHandler,
    private val serviceName: String = "TCP"
) {

    private val logger = LoggerFactory.getLogger(TcpServer::class.java)

    private lateinit var bossGroup: EventLoopGroup
    private lateinit var workerGroup: EventLoopGroup
    private var serverChannel: Channel? = null

    private val connectionLimiter = ConnectionLimiter(maxConnections, maxConnectionsPerIp)

    companion object {
        private const val MAX_FRAME_LENGTH = 65535
        private const val LENGTH_FIELD_OFFSET = 0
        private const val LENGTH_FIELD_LENGTH = 4
        private const val LENGTH_ADJUSTMENT = 0
        private const val INITIAL_BYTES_TO_STRIP = 4
        private const val IDLE_TIMEOUT_SECONDS = 60L
    }

    /**
     * Start the TCP server and bind to the configured port.
     * This method blocks until the server channel is bound.
     */
    fun start() {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()

                    // 1. Connection limiting (shared handler, rejects before any processing)
                    pipeline.addLast("connectionLimiter", connectionLimiter)

                    // 2. Idle state detection — close connections after timeout
                    pipeline.addLast("idleStateHandler",
                        IdleStateHandler(IDLE_TIMEOUT_SECONDS, 0, 0, TimeUnit.SECONDS))

                    // 3. Inbound: frame decoder strips the 4-byte length prefix
                    pipeline.addLast("frameDecoder",
                        LengthFieldBasedFrameDecoder(
                            MAX_FRAME_LENGTH,
                            LENGTH_FIELD_OFFSET,
                            LENGTH_FIELD_LENGTH,
                            LENGTH_ADJUSTMENT,
                            INITIAL_BYTES_TO_STRIP
                        ))

                    // 4. Outbound: prepend 4-byte length field
                    pipeline.addLast("framePrepender", LengthFieldPrepender(4))

                    // 5. Custom codec: decode [opcode][payload] into a Packet object
                    pipeline.addLast("packetDecoder", PacketDecoder())
                    pipeline.addLast("packetEncoder", PacketEncoder())

                    // 6. Business logic router (service-specific)
                    pipeline.addLast("packetRouter", packetRouter)
                }
            })

        val channelFuture = bootstrap.bind(port).sync()
        serverChannel = channelFuture.channel()
        logger.info("{} TCP server listening on port {}", serviceName, port)
    }

    /**
     * Gracefully shut down the TCP server and release all resources.
     */
    fun stop() {
        logger.info("Stopping {} TCP server...", serviceName)
        try {
            serverChannel?.close()?.sync()
        } catch (e: Exception) {
            logger.warn("Error closing server channel: {}", e.message)
        }
        workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync()
        bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync()
        logger.info("{} TCP server stopped", serviceName)
    }

    /** Returns the [ConnectionLimiter] for monitoring purposes. */
    fun getConnectionLimiter(): ConnectionLimiter = connectionLimiter
}
