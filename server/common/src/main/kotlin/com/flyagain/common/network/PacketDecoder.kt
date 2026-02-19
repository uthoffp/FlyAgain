package com.flyagain.common.network

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.slf4j.LoggerFactory

/**
 * Decodes a framed TCP message into a [Packet].
 *
 * By the time this handler receives data, the [io.netty.handler.codec.LengthFieldBasedFrameDecoder]
 * has already stripped the 4-byte length prefix. The remaining bytes are:
 *
 *   [2-byte opcode (big-endian unsigned short)][protobuf payload]
 *
 * This decoder reads the opcode, extracts the remaining bytes as the payload,
 * and emits a [Packet] instance to the next handler in the pipeline.
 */
class PacketDecoder : ByteToMessageDecoder() {

    private val logger = LoggerFactory.getLogger(PacketDecoder::class.java)

    companion object {
        /** Minimum readable bytes: 2-byte opcode */
        private const val MIN_PACKET_SIZE = 2
    }

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        if (buf.readableBytes() < MIN_PACKET_SIZE) {
            logger.warn("Received packet with fewer than {} bytes from {}, discarding", MIN_PACKET_SIZE, ctx.channel().remoteAddress())
            return
        }

        val opcode = buf.readUnsignedShort()
        val payloadLength = buf.readableBytes()
        val payload = ByteArray(payloadLength)
        buf.readBytes(payload)

        val packet = Packet(opcode, payload)
        logger.debug("Decoded {} from {}", packet, ctx.channel().remoteAddress())
        out.add(packet)
    }
}
