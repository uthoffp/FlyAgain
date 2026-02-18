package com.flyagain.login.network

import com.flyagain.common.proto.Opcode
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.slf4j.LoggerFactory

/**
 * Encodes outbound Packet objects into the wire format:
 *   [4-byte length][2-byte opcode][protobuf payload]
 *
 * The length field covers everything after itself (opcode + payload).
 */
class PacketEncoder : MessageToByteEncoder<Packet>() {

    private val logger = LoggerFactory.getLogger(PacketEncoder::class.java)

    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: ByteBuf) {
        val payloadBytes = msg.message.toByteArray()
        val contentLength = 2 + payloadBytes.size // 2 bytes for opcode + payload

        // Write length prefix (4 bytes)
        out.writeInt(contentLength)

        // Write opcode (2 bytes)
        out.writeShort(msg.opcode.number)

        // Write protobuf payload
        out.writeBytes(payloadBytes)

        logger.trace("Encoded packet: opcode=0x{}, payloadSize={}, totalSize={}",
            Integer.toHexString(msg.opcode.number), payloadBytes.size, 4 + contentLength)
    }

    @Deprecated("Deprecated in Netty", ReplaceWith(""))
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("PacketEncoder error to {}: {}", ctx.channel().remoteAddress(), cause.message)
        ctx.close()
    }
}
