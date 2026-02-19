package com.flyagain.common

import com.flyagain.common.network.UdpPacket
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UdpPacketTest {

    @Test
    fun `toString formats opcode as hex`() {
        val packet = UdpPacket(
            sessionToken = 12345L,
            sequence = 1,
            opcode = 0x0101,
            payload = byteArrayOf(1, 2, 3),
            sender = InetSocketAddress("127.0.0.1", 7781)
        )
        val str = packet.toString()
        assertTrue(str.contains("0101"), "Should contain hex opcode, got: $str")
        assertTrue(str.contains("payloadSize=3"), "Should contain payload size, got: $str")
    }

    @Test
    fun `equals compares all fields including payload content`() {
        val addr = InetSocketAddress("127.0.0.1", 7781)
        val p1 = UdpPacket(1L, 1, 0x0101, byteArrayOf(1, 2), addr)
        val p2 = UdpPacket(1L, 1, 0x0101, byteArrayOf(1, 2), addr)
        assertEquals(p1, p2)
    }

    @Test
    fun `different payload means not equal`() {
        val addr = InetSocketAddress("127.0.0.1", 7781)
        val p1 = UdpPacket(1L, 1, 0x0101, byteArrayOf(1, 2), addr)
        val p2 = UdpPacket(1L, 1, 0x0101, byteArrayOf(3, 4), addr)
        assertNotEquals(p1, p2)
    }

    @Test
    fun `different sequence means not equal`() {
        val addr = InetSocketAddress("127.0.0.1", 7781)
        val p1 = UdpPacket(1L, 1, 0x0101, byteArrayOf(1), addr)
        val p2 = UdpPacket(1L, 2, 0x0101, byteArrayOf(1), addr)
        assertNotEquals(p1, p2)
    }

    @Test
    fun `hashCode is consistent for equal packets`() {
        val addr = InetSocketAddress("127.0.0.1", 7781)
        val p1 = UdpPacket(1L, 1, 0x0101, byteArrayOf(1, 2), addr)
        val p2 = UdpPacket(1L, 1, 0x0101, byteArrayOf(1, 2), addr)
        assertEquals(p1.hashCode(), p2.hashCode())
    }
}
