package com.flyagain.common

import com.flyagain.common.network.HeartbeatTracker
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeartbeatTrackerTest {

    private fun mockChannel(address: String = "127.0.0.1", port: Int = 12345): Channel {
        val socketAddress = InetSocketAddress(address, port)
        return object : io.netty.channel.AbstractChannel(null) {
            override fun config() = throw UnsupportedOperationException()
            override fun isOpen() = true
            override fun isActive() = true
            override fun metadata() = throw UnsupportedOperationException()
            override fun localAddress0() = socketAddress
            override fun remoteAddress0() = socketAddress
            override fun doBind(localAddress: java.net.SocketAddress?) {}
            override fun doDisconnect() {}
            override fun doClose() {}
            override fun doBeginRead() {}
            override fun doWrite(buf: io.netty.channel.ChannelOutboundBuffer?) {}
            override fun newUnsafe(): AbstractUnsafe = object : AbstractUnsafe() {
                override fun connect(
                    remoteAddress: java.net.SocketAddress?,
                    localAddress: java.net.SocketAddress?,
                    promise: io.netty.channel.ChannelPromise?
                ) {}
            }
            override fun isCompatible(loop: io.netty.channel.EventLoop?) = true
        }
    }

    @Test
    fun `register increases tracked count`() {
        val tracker = HeartbeatTracker()
        assertEquals(0, tracker.getTrackedCount())
        tracker.register(mockChannel(port = 1))
        assertEquals(1, tracker.getTrackedCount())
        tracker.register(mockChannel(port = 2))
        assertEquals(2, tracker.getTrackedCount())
    }

    @Test
    fun `unregister decreases tracked count`() {
        val tracker = HeartbeatTracker()
        val ch = mockChannel()
        tracker.register(ch)
        assertEquals(1, tracker.getTrackedCount())
        tracker.unregister(ch)
        assertEquals(0, tracker.getTrackedCount())
    }

    @Test
    fun `unregister for non-tracked channel is a no-op`() {
        val tracker = HeartbeatTracker()
        tracker.unregister(mockChannel()) // should not throw
        assertEquals(0, tracker.getTrackedCount())
    }

    @Test
    fun `recordHeartbeat updates timestamp`() {
        val tracker = HeartbeatTracker()
        val ch = mockChannel()
        tracker.register(ch)

        Thread.sleep(50)
        tracker.recordHeartbeat(ch)

        val timeSince = tracker.getTimeSinceLastHeartbeat(ch)
        assertTrue(timeSince < 50, "Time since heartbeat should be very recent, was $timeSince ms")
    }

    @Test
    fun `getTimeSinceLastHeartbeat returns -1 for untracked channel`() {
        val tracker = HeartbeatTracker()
        assertEquals(-1, tracker.getTimeSinceLastHeartbeat(mockChannel()))
    }

    @Test
    fun `getTimeSinceLastHeartbeat increases over time`() {
        val tracker = HeartbeatTracker()
        val ch = mockChannel()
        tracker.register(ch)
        Thread.sleep(100)
        val timeSince = tracker.getTimeSinceLastHeartbeat(ch)
        assertTrue(timeSince >= 90, "Expected at least 90ms, got $timeSince ms")
    }

    @Test
    fun `stop clears all tracked channels`() {
        val tracker = HeartbeatTracker()
        tracker.register(mockChannel(port = 1))
        tracker.register(mockChannel(port = 2))
        assertEquals(2, tracker.getTrackedCount())
        tracker.stop()
        assertEquals(0, tracker.getTrackedCount())
    }
}
