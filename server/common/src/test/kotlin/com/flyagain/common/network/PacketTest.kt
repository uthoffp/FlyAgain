package com.flyagain.common.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse

class PacketTest {

    @Test
    fun `equality with same opcode and payload`() {
        val a = Packet(0x0001, byteArrayOf(1, 2, 3))
        val b = Packet(0x0001, byteArrayOf(1, 2, 3))
        assertEquals(a, b)
    }

    @Test
    fun `inequality with different opcode`() {
        val a = Packet(0x0001, byteArrayOf(1))
        val b = Packet(0x0002, byteArrayOf(1))
        assertNotEquals(a, b)
    }

    @Test
    fun `inequality with different payload`() {
        val a = Packet(0x0001, byteArrayOf(1))
        val b = Packet(0x0001, byteArrayOf(2))
        assertNotEquals(a, b)
    }

    @Test
    fun `equality with empty payloads`() {
        val a = Packet(0, byteArrayOf())
        val b = Packet(0, byteArrayOf())
        assertEquals(a, b)
    }

    @Test
    fun `not equal to null`() {
        val packet = Packet(1, byteArrayOf())
        assertFalse(packet.equals(null))
    }

    @Test
    fun `not equal to different type`() {
        val packet = Packet(1, byteArrayOf())
        assertFalse(packet.equals("not a packet"))
    }

    @Test
    fun `hashCode is consistent for equal packets`() {
        val a = Packet(0x00AB, byteArrayOf(10, 20))
        val b = Packet(0x00AB, byteArrayOf(10, 20))
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `hashCode differs for different opcodes`() {
        val a = Packet(0x0001, byteArrayOf(1))
        val b = Packet(0x0002, byteArrayOf(1))
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `identical packets work as HashMap keys`() {
        val map = HashMap<Packet, String>()
        map[Packet(1, byteArrayOf(5))] = "first"
        map[Packet(1, byteArrayOf(5))] = "second"
        assertEquals(1, map.size)
        assertEquals("second", map[Packet(1, byteArrayOf(5))])
    }

    @Test
    fun `toString formats opcode as 4-digit hex`() {
        val packet = Packet(0x00AB, byteArrayOf(1, 2, 3))
        assertEquals("Packet(opcode=0x00ab, payloadSize=3)", packet.toString())
    }

    @Test
    fun `toString with zero opcode and empty payload`() {
        val packet = Packet(0, byteArrayOf())
        assertEquals("Packet(opcode=0x0000, payloadSize=0)", packet.toString())
    }

    @Test
    fun `toString with max unsigned short opcode`() {
        val packet = Packet(0xFFFF, byteArrayOf(1))
        assertEquals("Packet(opcode=0xffff, payloadSize=1)", packet.toString())
    }
}
