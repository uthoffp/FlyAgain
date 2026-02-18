package com.flyagain.login.network

import com.flyagain.login.handler.PacketRouter
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.timeout.IdleStateHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Netty TCP server for the Login Service.
 *
 * Pipeline per connection:
 *   1. ConnectionLimiter (shared) - enforces connection limits
 *   2. IdleStateHandler - disconnects idle clients after 60 seconds
 *   3. LengthFieldBasedFrameDecoder - splits stream into frames using 4-byte length prefix
 *   4. PacketDecoder - parses opcode + protobuf from each frame
 *   5. PacketEncoder - encodes outbound Packets to wire format
 *   6. PacketRouter - routes decoded Packets to business-logic handlers
 *
 * TLS support is prepared but not enabled by default in development.
 * In production, an SslHandler would be inserted at position 0 in the pipeline.
 */
class TcpServer(
    private val port: Int,
    private val connectionLimiter: ConnectionLimiter,
    private val packetRouter: PacketRouter
) {

    private val logger = LoggerFactory.getLogger(TcpServer::class.java)

    private lateinit var bossGroup: EventLoopGroup
    private lateinit var workerGroup: EventLoopGroup
    private var serverChannel: Channel? = null

    companion object {
        // Maximum frame size: 64 KB should be more than enough for auth packets
        private const val MAX_FRAME_LENGTH = 65536
        // Length field: 4 bytes at offset 0, strip the length field from the frame body
        private const val LENGTH_FIELD_OFFSET = 0
        private const val LENGTH_FIELD_LENGTH = 4
        private const val LENGTH_ADJUSTMENT = 0
        private const val INITIAL_BYTES_TO_STRIP = 4
        // Idle timeout in seconds
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

                    // 1. Connection limiting (shared handler)
                    pipeline.addLast("connectionLimiter", connectionLimiter)

                    // 2. Idle state detection -- close connections after IDLE_TIMEOUT_SECONDS of inactivity
                    pipeline.addLast("idleStateHandler",
                        IdleStateHandler(IDLE_TIMEOUT_SECONDS, 0, 0, TimeUnit.SECONDS))

                    // 3. Frame decoder: splits TCP stream into individual packets based on length prefix
                    //    Wire format: [4-byte length][2-byte opcode][protobuf payload]
                    //    The length field value = 2 + payload_size (everything after the length field)
                    pipeline.addLast("frameDecoder",
                        LengthFieldBasedFrameDecoder(
                            MAX_FRAME_LENGTH,
                            LENGTH_FIELD_OFFSET,
                            LENGTH_FIELD_LENGTH,
                            LENGTH_ADJUSTMENT,
                            INITIAL_BYTES_TO_STRIP
                        ))

                    // 4. Packet decoder: opcode + protobuf deserialization
                    pipeline.addLast("packetDecoder", PacketDecoder())

                    // 5. Packet encoder: serializes outbound Packet objects
                    pipeline.addLast("packetEncoder", PacketEncoder())

                    // 6. Business logic router
                    pipeline.addLast("packetRouter", packetRouter)
                }
            })

        val channelFuture = bootstrap.bind(port).sync()
        serverChannel = channelFuture.channel()
        logger.info("Login Service TCP server started on port {}", port)
    }

    /**
     * Gracefully shut down the TCP server and release all resources.
     */
    fun stop() {
        logger.info("Shutting down TCP server...")
        try {
            serverChannel?.close()?.sync()
        } catch (e: Exception) {
            logger.warn("Error closing server channel: {}", e.message)
        }
        workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync()
        bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync()
        logger.info("TCP server shut down complete")
    }
}
