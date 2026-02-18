package com.flyagain.login.network

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Netty ChannelHandler that enforces connection limits:
 * - Maximum total connections across all IPs
 * - Maximum connections per individual IP address (default 5)
 *
 * Connections that exceed either limit are immediately closed.
 * This handler is sharable and thread-safe.
 */
@ChannelHandler.Sharable
class ConnectionLimiter(
    private val maxTotalConnections: Int,
    private val maxConnectionsPerIp: Int
) : ChannelInboundHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(ConnectionLimiter::class.java)

    private val totalConnections = AtomicInteger(0)
    private val connectionsPerIp = ConcurrentHashMap<String, AtomicInteger>()

    init {
        logger.info("ConnectionLimiter initialized: maxTotal={}, maxPerIp={}",
            maxTotalConnections, maxConnectionsPerIp)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val ip = getIp(ctx)
        val currentTotal = totalConnections.incrementAndGet()

        // Check total connection limit
        if (currentTotal > maxTotalConnections) {
            totalConnections.decrementAndGet()
            logger.warn("Total connection limit reached ({}/{}), rejecting connection from {}",
                currentTotal, maxTotalConnections, ip)
            ctx.close()
            return
        }

        // Check per-IP connection limit
        val ipCount = connectionsPerIp
            .computeIfAbsent(ip) { AtomicInteger(0) }
            .incrementAndGet()

        if (ipCount > maxConnectionsPerIp) {
            connectionsPerIp[ip]?.decrementAndGet()
            totalConnections.decrementAndGet()
            logger.warn("Per-IP connection limit reached for {} ({}/{}), rejecting connection",
                ip, ipCount, maxConnectionsPerIp)
            ctx.close()
            return
        }

        logger.debug("Connection accepted from {} (total={}, ipCount={})", ip, currentTotal, ipCount)
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val ip = getIp(ctx)
        totalConnections.decrementAndGet()

        val counter = connectionsPerIp[ip]
        if (counter != null) {
            val remaining = counter.decrementAndGet()
            if (remaining <= 0) {
                connectionsPerIp.remove(ip)
            }
        }

        logger.debug("Connection closed from {} (total={}, ipCount={})",
            ip, totalConnections.get(), counter?.get() ?: 0)
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("ConnectionLimiter error from {}: {}", getIp(ctx), cause.message)
        ctx.close()
    }

    /**
     * Get the current number of total active connections.
     */
    fun getTotalConnections(): Int = totalConnections.get()

    /**
     * Get the current number of connections from a specific IP.
     */
    fun getConnectionsForIp(ip: String): Int = connectionsPerIp[ip]?.get() ?: 0

    private fun getIp(ctx: ChannelHandlerContext): String {
        val socketAddress = ctx.channel().remoteAddress() as? InetSocketAddress
        return socketAddress?.address?.hostAddress ?: "unknown"
    }
}
