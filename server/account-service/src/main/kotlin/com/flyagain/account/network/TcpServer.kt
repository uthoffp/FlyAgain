package com.flyagain.account.network

import com.flyagain.account.handler.PacketRouter
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
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
 * Netty-based TCP server for the Account Service.
 *
 * Wire format (each frame):
 *   [4-byte length prefix][2-byte opcode][protobuf payload]
 *
 * The length prefix covers opcode + payload (i.e., does NOT include itself).
 * LengthFieldBasedFrameDecoder strips the 4-byte prefix, leaving [opcode][payload]
 * for PacketDecoder to handle.
 */
class TcpServer(
    private val port: Int,
    private val maxConnections: Int,
    private val maxConnectionsPerIp: Int,
    private val packetRouter: PacketRouter
) {

    private val logger = LoggerFactory.getLogger(TcpServer::class.java)

    private lateinit var bossGroup: EventLoopGroup
    private lateinit var workerGroup: EventLoopGroup
    private var serverChannel: Channel? = null

    private val connectionLimiter = ConnectionLimiter(maxConnections, maxConnectionsPerIp)

    fun start() {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()

                    // Connection limiting (first in pipeline, rejects before any processing)
                    pipeline.addLast("connectionLimiter", connectionLimiter)

                    // Idle state detection: close connections idle for 60 seconds
                    pipeline.addLast("idleStateHandler", IdleStateHandler(60, 0, 0, TimeUnit.SECONDS))

                    // Inbound: frame decoder strips the 4-byte length prefix
                    // maxFrameLength = 65535, lengthFieldOffset = 0, lengthFieldLength = 4,
                    // lengthAdjustment = 0, initialBytesToStrip = 4
                    pipeline.addLast(
                        "frameDecoder",
                        LengthFieldBasedFrameDecoder(65535, 0, 4, 0, 4)
                    )

                    // Outbound: prepend 4-byte length field
                    pipeline.addLast("framePrepender", LengthFieldPrepender(4))

                    // Custom codec: decode [opcode][payload] into a Packet object
                    pipeline.addLast("packetDecoder", PacketDecoder())
                    pipeline.addLast("packetEncoder", PacketEncoder())

                    // Business logic router
                    pipeline.addLast("packetRouter", packetRouter)
                }
            })

        val channelFuture = bootstrap.bind(port).sync()
        serverChannel = channelFuture.channel()
        logger.info("Account Service TCP server listening on port {}", port)
    }

    fun stop() {
        logger.info("Stopping Account Service TCP server...")
        serverChannel?.close()?.sync()
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
        logger.info("Account Service TCP server stopped")
    }
}
