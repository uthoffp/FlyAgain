package com.flyagain.common.network

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacketDecoderTest {

    private fun createChannel(): EmbeddedChannel = EmbeddedChannel(PacketDecoder())

    @Test
    fun `decodes opcode and payload from valid frame`() {
        val channel = createChannel()
        val buf = Unpooled.buffer()
        buf.writeShort(0x0102)
        buf.writeBytes(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))

        assertTrue(channel.writeInbound(buf))
        val packet = channel.readInbound<Packet>()

        assertEquals(0x0102, packet.opcode)
        assertEquals(listOf(0xAA.toByte(), 0xBB.toByte()), packet.payload.toList())
        assertNull(channel.readInbound<Any>())
    }

    @Test
    fun `decodes opcode-only frame as empty payload`() {
        val channel = createChannel()
        val buf = Unpooled.buffer()
        buf.writeShort(0x0001)

        assertTrue(channel.writeInbound(buf))
        val packet = channel.readInbound<Packet>()

        assertEquals(0x0001, packet.opcode)
        assertEquals(0, packet.payload.size)
    }

    @Test
    fun `emits nothing for undersized buffer with 1 byte`() {
        val channel = createChannel()
        val buf = Unpooled.buffer()
        buf.writeByte(0xFF)

        channel.writeInbound(buf)

        assertNull(channel.readInbound<Any>())
    }

    @Test
    fun `emits nothing for empty buffer`() {
        val channel = createChannel()
        val buf = Unpooled.buffer()

        channel.writeInbound(buf)

        assertNull(channel.readInbound<Any>())
    }

    @Test
    fun `decodes zero opcode`() {
        val channel = createChannel()
        val buf = Unpooled.buffer()
        buf.writeShort(0x0000)

        channel.writeInbound(buf)
        val packet = channel.readInbound<Packet>()

        assertEquals(0, packet.opcode)
    }

    @Test
    fun `decodes max opcode 0xFFFF`() {
        val channel = createChannel()
        val buf = Unpooled.buffer()
        buf.writeShort(0xFFFF.toInt())

        channel.writeInbound(buf)
        val packet = channel.readInbound<Packet>()

        assertEquals(0xFFFF, packet.opcode)
    }

    @Test
    fun `decodes large payload without truncation`() {
        val payload = ByteArray(5000) { it.toByte() }
        val channel = createChannel()
        val buf = Unpooled.buffer()
        buf.writeShort(0x0001)
        buf.writeBytes(payload)

        channel.writeInbound(buf)
        val packet = channel.readInbound<Packet>()

        assertEquals(5000, packet.payload.size)
        assertEquals(payload.toList(), packet.payload.toList())
    }

    @Test
    fun `consumes all readable bytes from buffer`() {
        val channel = createChannel()
        val buf = Unpooled.buffer()
        buf.writeShort(0x0001)
        buf.writeBytes(byteArrayOf(1, 2, 3))

        channel.writeInbound(buf)

        // Buffer should be fully consumed (refCnt drops to 0 after channel processes it)
        assertEquals(0, buf.refCnt())

        val packet = channel.readInbound<Packet>()
        assertEquals(0x0001, packet.opcode)
        assertEquals(3, packet.payload.size)
    }
}
