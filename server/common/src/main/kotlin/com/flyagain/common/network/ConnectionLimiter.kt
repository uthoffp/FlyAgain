package com.flyagain.common.network

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

    init {
        logger.info("ConnectionLimiter initialized: maxTotal={}, maxPerIp={}", maxConnections, maxConnectionsPerIp)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val ip = getIp(ctx)
        val currentTotal = totalConnections.incrementAndGet()

        if (currentTotal > maxConnections) {
            totalConnections.decrementAndGet()
            logger.warn("Max total connections ({}) exceeded, rejecting connection from {}", maxConnections, ip)
            ctx.close()
            return
        }

        val ipCount = connectionsByIp
            .computeIfAbsent(ip) { AtomicInteger(0) }
            .incrementAndGet()

        if (ipCount > maxConnectionsPerIp) {
            connectionsByIp[ip]?.decrementAndGet()
            totalConnections.decrementAndGet()
            logger.warn("Max connections per IP ({}) exceeded for {}, rejecting connection", maxConnectionsPerIp, ip)
            ctx.close()
            return
        }

        logger.debug("Connection accepted from {} (total={}, ipCount={})", ip, currentTotal, ipCount)
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val ip = getIp(ctx)
        totalConnections.decrementAndGet()

        val counter = connectionsByIp[ip]
        if (counter != null) {
            val remaining = counter.decrementAndGet()
            if (remaining <= 0) {
                connectionsByIp.remove(ip)
            }
        }

        logger.debug("Connection closed from {} (total={}, ipCount={})", ip, totalConnections.get(), counter?.get() ?: 0)
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("ConnectionLimiter error from {}: {}", getIp(ctx), cause.message)
        ctx.close()
    }

    /** Returns the current total connection count. Useful for monitoring. */
    fun getTotalConnections(): Int = totalConnections.get()

    /** Returns the connection count for a specific IP. */
    fun getConnectionsForIp(ip: String): Int = connectionsByIp[ip]?.get() ?: 0

    private fun getIp(ctx: ChannelHandlerContext): String {
        val socketAddress = ctx.channel().remoteAddress() as? InetSocketAddress
        return socketAddress?.address?.hostAddress ?: "unknown"
    }
}
