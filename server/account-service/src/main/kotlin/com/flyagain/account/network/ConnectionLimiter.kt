package com.flyagain.account.network

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Netty [ChannelHandler] that enforces connection limits.
 *
 * - Limits total active connections across all IPs to [maxConnections].
 * - Limits connections from a single IP address to [maxConnectionsPerIp].
 *
 * When a limit is exceeded the channel is immediately closed before any
 * further handlers in the pipeline process data.
 *
 * This handler is marked [@ChannelHandler.Sharable] so a single instance
 * can be shared across all child channels.
 */
@ChannelHandler.Sharable
class ConnectionLimiter(
    private val maxConnections: Int,
    private val maxConnectionsPerIp: Int
) : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(ConnectionLimiter::class.java)

    /** Total number of currently active connections. */
    private val totalConnections = AtomicInteger(0)

    /** Per-IP connection counts. */
    private val connectionsByIp = ConcurrentHashMap<String, AtomicInteger>()

    override fun channelActive(ctx: ChannelHandlerContext) {
        val remoteAddress = ctx.channel().remoteAddress() as? InetSocketAddress
        val ip = remoteAddress?.address?.hostAddress ?: "unknown"

        val currentTotal = totalConnections.incrementAndGet()
        if (currentTotal > maxConnections) {
            totalConnections.decrementAndGet()
            logger.warn("Max total connections ({}) exceeded, rejecting connection from {}", maxConnections, ip)
            ctx.close()
            return
        }

        val ipCount = connectionsByIp.computeIfAbsent(ip) { AtomicInteger(0) }
        val currentIpCount = ipCount.incrementAndGet()
        if (currentIpCount > maxConnectionsPerIp) {
            ipCount.decrementAndGet()
            totalConnections.decrementAndGet()
            logger.warn("Max connections per IP ({}) exceeded for {}, rejecting connection", maxConnectionsPerIp, ip)
            ctx.close()
            return
        }

        logger.debug("Connection accepted from {} (total: {}, ip: {})", ip, currentTotal, currentIpCount)
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val remoteAddress = ctx.channel().remoteAddress() as? InetSocketAddress
        val ip = remoteAddress?.address?.hostAddress ?: "unknown"

        totalConnections.decrementAndGet()

        val ipCount = connectionsByIp[ip]
        if (ipCount != null) {
            val remaining = ipCount.decrementAndGet()
            if (remaining <= 0) {
                connectionsByIp.remove(ip)
            }
        }

        logger.debug("Connection closed from {} (total: {})", ip, totalConnections.get())
        super.channelInactive(ctx)
    }

    /** Returns the current total connection count. Useful for monitoring. */
    fun getTotalConnections(): Int = totalConnections.get()

    /** Returns the connection count for a specific IP. */
    fun getConnectionsForIp(ip: String): Int = connectionsByIp[ip]?.get() ?: 0
}
