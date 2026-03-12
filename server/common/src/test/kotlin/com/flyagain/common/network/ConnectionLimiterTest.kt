package com.flyagain.common.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionLimiterTest {

    private fun mockCtx(ip: String = "192.168.1.1", port: Int = 12345): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        every { channel.remoteAddress() } returns InetSocketAddress(ip, port)
        every { ctx.close() } returns mockk<ChannelFuture>()
        return ctx
    }

    @Test
    fun `accepts connections under total limit`() {
        val limiter = ConnectionLimiter(maxConnections = 3, maxConnectionsPerIp = 10)
        val ctx1 = mockCtx("10.0.0.1")
        val ctx2 = mockCtx("10.0.0.2")
        val ctx3 = mockCtx("10.0.0.3")

        limiter.channelActive(ctx1)
        limiter.channelActive(ctx2)
        limiter.channelActive(ctx3)

        assertEquals(3, limiter.getTotalConnections())
        verify(exactly = 0) { ctx1.close() }
        verify(exactly = 0) { ctx2.close() }
        verify(exactly = 0) { ctx3.close() }
    }

    @Test
    fun `rejects connection exceeding total limit`() {
        val limiter = ConnectionLimiter(maxConnections = 2, maxConnectionsPerIp = 10)
        limiter.channelActive(mockCtx("10.0.0.1"))
        limiter.channelActive(mockCtx("10.0.0.2"))

        val rejected = mockCtx("10.0.0.3")
        limiter.channelActive(rejected)

        verify(exactly = 1) { rejected.close() }
        assertEquals(2, limiter.getTotalConnections())
    }

    @Test
    fun `accepts connections under per-IP limit`() {
        val limiter = ConnectionLimiter(maxConnections = 100, maxConnectionsPerIp = 3)
        val ctx1 = mockCtx("10.0.0.1", 1001)
        val ctx2 = mockCtx("10.0.0.1", 1002)
        val ctx3 = mockCtx("10.0.0.1", 1003)

        limiter.channelActive(ctx1)
        limiter.channelActive(ctx2)
        limiter.channelActive(ctx3)

        assertEquals(3, limiter.getConnectionsForIp("10.0.0.1"))
    }

    @Test
    fun `rejects connection exceeding per-IP limit`() {
        val limiter = ConnectionLimiter(maxConnections = 100, maxConnectionsPerIp = 2)
        limiter.channelActive(mockCtx("10.0.0.1", 1001))
        limiter.channelActive(mockCtx("10.0.0.1", 1002))

        val rejected = mockCtx("10.0.0.1", 1003)
        limiter.channelActive(rejected)

        verify(exactly = 1) { rejected.close() }
        assertEquals(2, limiter.getConnectionsForIp("10.0.0.1"))
    }

    @Test
    fun `per-IP limits are independent between IPs`() {
        val limiter = ConnectionLimiter(maxConnections = 100, maxConnectionsPerIp = 1)
        val ctx1 = mockCtx("10.0.0.1")
        val ctx2 = mockCtx("10.0.0.2")

        limiter.channelActive(ctx1)
        limiter.channelActive(ctx2)

        assertEquals(1, limiter.getConnectionsForIp("10.0.0.1"))
        assertEquals(1, limiter.getConnectionsForIp("10.0.0.2"))
        verify(exactly = 0) { ctx1.close() }
        verify(exactly = 0) { ctx2.close() }
    }

    @Test
    fun `channelInactive decrements counters`() {
        val limiter = ConnectionLimiter(maxConnections = 10, maxConnectionsPerIp = 10)
        val ctx1 = mockCtx("10.0.0.1")

        limiter.channelActive(ctx1)
        assertEquals(1, limiter.getTotalConnections())

        limiter.channelInactive(ctx1)
        assertEquals(0, limiter.getTotalConnections())
        assertEquals(0, limiter.getConnectionsForIp("10.0.0.1"))
    }

    @Test
    fun `IP entry removed when last connection closes`() {
        val limiter = ConnectionLimiter(maxConnections = 10, maxConnectionsPerIp = 10)
        val ctx1 = mockCtx("10.0.0.1", 1001)
        val ctx2 = mockCtx("10.0.0.1", 1002)

        limiter.channelActive(ctx1)
        limiter.channelActive(ctx2)
        assertEquals(2, limiter.getConnectionsForIp("10.0.0.1"))

        limiter.channelInactive(ctx1)
        assertEquals(1, limiter.getConnectionsForIp("10.0.0.1"))

        limiter.channelInactive(ctx2)
        assertEquals(0, limiter.getConnectionsForIp("10.0.0.1"))
    }

    @Test
    fun `new connection accepted after previous one closed`() {
        val limiter = ConnectionLimiter(maxConnections = 1, maxConnectionsPerIp = 10)
        val ctx1 = mockCtx("10.0.0.1")

        limiter.channelActive(ctx1)
        limiter.channelInactive(ctx1)

        val ctx2 = mockCtx("10.0.0.2")
        limiter.channelActive(ctx2)

        assertEquals(1, limiter.getTotalConnections())
        verify(exactly = 0) { ctx2.close() }
    }

    @Test
    fun `exceptionCaught closes the channel`() {
        val limiter = ConnectionLimiter(maxConnections = 10, maxConnectionsPerIp = 10)
        val ctx = mockCtx("10.0.0.1")

        limiter.exceptionCaught(ctx, RuntimeException("test error"))

        verify(exactly = 1) { ctx.close() }
    }

    @Test
    fun `getConnectionsForIp returns 0 for unknown IP`() {
        val limiter = ConnectionLimiter(maxConnections = 10, maxConnectionsPerIp = 10)
        assertEquals(0, limiter.getConnectionsForIp("unknown-ip"))
    }
}
