package com.flyagain.account

import com.flyagain.common.network.Packet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PacketTest {

    @Test
    fun `packet stores opcode and payload`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val packet = Packet(opcode = 0x0001, payload = payload)
        assertEquals(0x0001, packet.opcode)
        assertTrue(payload.contentEquals(packet.payload))
    }

    @Test
    fun `packets with same opcode and payload are equal`() {
        val p1 = Packet(0x0001, byteArrayOf(10, 20))
        val p2 = Packet(0x0001, byteArrayOf(10, 20))
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    @Test
    fun `packets with different opcodes are not equal`() {
        val p1 = Packet(0x0001, byteArrayOf(10, 20))
        val p2 = Packet(0x0002, byteArrayOf(10, 20))
        assertNotEquals(p1, p2)
    }

    @Test
    fun `packets with different payloads are not equal`() {
        val p1 = Packet(0x0001, byteArrayOf(10, 20))
        val p2 = Packet(0x0001, byteArrayOf(30, 40))
        assertNotEquals(p1, p2)
    }

    @Test
    fun `toString formats opcode as hex`() {
        val packet = Packet(0x0601, byteArrayOf())
        val str = packet.toString()
        assertTrue(str.contains("0601"), "toString should contain hex opcode: $str")
    }

    @Test
    fun `toString includes payload size`() {
        val packet = Packet(0x0001, byteArrayOf(1, 2, 3))
        val str = packet.toString()
        assertTrue(str.contains("3"), "toString should include payload size: $str")
    }

    @Test
    fun `empty payload packet`() {
        val packet = Packet(0x0601, byteArrayOf())
        assertEquals(0, packet.payload.size)
    }
}
