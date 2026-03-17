package com.flyagain.common.network

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketEncoderTest {

    private fun encodePacket(packet: Packet): ByteBuf {
        val channel = EmbeddedChannel(PacketEncoder())
        assertTrue(channel.writeOutbound(packet))
        return channel.readOutbound()!!
    }

    @Test
    fun `encodes opcode as 2-byte big-endian unsigned short`() {
        val buf = encodePacket(Packet(0x0102, byteArrayOf()))

        assertEquals(2, buf.readableBytes())
        assertEquals(0x0102, buf.readUnsignedShort())
        buf.release()
    }

    @Test
    fun `encodes opcode followed by payload bytes`() {
        val payload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val buf = encodePacket(Packet(0x0001, payload))

        assertEquals(5, buf.readableBytes())
        assertEquals(0x0001, buf.readUnsignedShort())
        val readPayload = ByteArray(3)
        buf.readBytes(readPayload)
        assertEquals(payload.toList(), readPayload.toList())
        buf.release()
    }

    @Test
    fun `encodes zero opcode`() {
        val buf = encodePacket(Packet(0x0000, byteArrayOf()))
        assertEquals(0x0000, buf.readUnsignedShort())
        buf.release()
    }

    @Test
    fun `encodes max opcode 0xFFFF`() {
        val buf = encodePacket(Packet(0xFFFF, byteArrayOf()))
        assertEquals(0xFFFF, buf.readUnsignedShort())
        buf.release()
    }

    @Test
    fun `encodes empty payload as opcode only`() {
        val buf = encodePacket(Packet(0x0001, byteArrayOf()))
        assertEquals(2, buf.readableBytes())
        buf.release()
    }

    @Test
    fun `encodes large payload without truncation`() {
        val payload = ByteArray(10_000) { it.toByte() }
        val buf = encodePacket(Packet(0x0001, payload))

        assertEquals(10_002, buf.readableBytes())
        buf.readUnsignedShort()
        val readPayload = ByteArray(10_000)
        buf.readBytes(readPayload)
        assertEquals(payload.toList(), readPayload.toList())
        buf.release()
    }

    @Test
    fun `preserves null bytes in payload`() {
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x02)
        val buf = encodePacket(Packet(0x0001, payload))

        buf.readUnsignedShort()
        val readPayload = ByteArray(4)
        buf.readBytes(readPayload)
        assertEquals(payload.toList(), readPayload.toList())
        buf.release()
    }
}
