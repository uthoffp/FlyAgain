package com.flyagain.common

import com.flyagain.common.network.UdpFloodProtection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UdpFloodProtectionTest {

    @Test
    fun `allows packets within rate limit`() {
        val protection = UdpFloodProtection(maxPacketsPerSecond = 10)
        for (i in 1..10) {
            assertTrue(protection.allowPacket("192.168.1.1"), "Packet $i should be allowed")
        }
    }

    @Test
    fun `blocks packets exceeding rate limit`() {
        val protection = UdpFloodProtection(maxPacketsPerSecond = 5)
        for (i in 1..5) {
            assertTrue(protection.allowPacket("192.168.1.1"))
        }
        assertFalse(protection.allowPacket("192.168.1.1"), "6th packet should be blocked")
        assertFalse(protection.allowPacket("192.168.1.1"), "7th packet should be blocked")
    }

    @Test
    fun `different IPs have separate counters`() {
        val protection = UdpFloodProtection(maxPacketsPerSecond = 3)
        for (i in 1..3) {
            assertTrue(protection.allowPacket("10.0.0.1"))
        }
        assertFalse(protection.allowPacket("10.0.0.1"), "IP 1 should be blocked")

        // Different IP should still be allowed
        assertTrue(protection.allowPacket("10.0.0.2"), "IP 2 should still be allowed")
    }

    @Test
    fun `getPacketCount returns correct count`() {
        val protection = UdpFloodProtection(maxPacketsPerSecond = 100)
        assertEquals(0, protection.getPacketCount("10.0.0.1"))
        protection.allowPacket("10.0.0.1")
        protection.allowPacket("10.0.0.1")
        protection.allowPacket("10.0.0.1")
        assertEquals(3, protection.getPacketCount("10.0.0.1"))
    }

    @Test
    fun `getTrackedIpCount returns correct count`() {
        val protection = UdpFloodProtection(maxPacketsPerSecond = 100)
        assertEquals(0, protection.getTrackedIpCount())
        protection.allowPacket("10.0.0.1")
        assertEquals(1, protection.getTrackedIpCount())
        protection.allowPacket("10.0.0.2")
        assertEquals(2, protection.getTrackedIpCount())
        protection.allowPacket("10.0.0.1")
        assertEquals(2, protection.getTrackedIpCount()) // still 2, same IP
    }

    @Test
    fun `counter resets after window expires`() {
        val protection = UdpFloodProtection(maxPacketsPerSecond = 3)
        for (i in 1..3) {
            protection.allowPacket("10.0.0.1")
        }
        assertFalse(protection.allowPacket("10.0.0.1"), "Should be blocked at limit")

        // Simulate time passing by sleeping slightly over 1s
        Thread.sleep(1100)

        assertTrue(protection.allowPacket("10.0.0.1"), "Should be allowed after window reset")
    }

    @Test
    fun `cleanup removes stale entries`() {
        val protection = UdpFloodProtection(maxPacketsPerSecond = 100)
        protection.allowPacket("10.0.0.1")
        protection.allowPacket("10.0.0.2")
        assertEquals(2, protection.getTrackedIpCount())

        // Wait briefly so entries become stale, then cleanup
        Thread.sleep(10)
        protection.cleanup(staleThresholdMs = 1)
        assertEquals(0, protection.getTrackedIpCount())
    }

    @Test
    fun `default limit is 100 packets per second`() {
        val protection = UdpFloodProtection()
        for (i in 1..100) {
            assertTrue(protection.allowPacket("10.0.0.1"), "Packet $i should be allowed")
        }
        assertFalse(protection.allowPacket("10.0.0.1"), "101st packet should be blocked")
    }
}
