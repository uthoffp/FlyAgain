package com.flyagain.common.network

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import org.slf4j.LoggerFactory

/**
 * Encodes a [Packet] into a raw TCP frame.
 *
 * Output format (before [io.netty.handler.codec.LengthFieldPrepender] adds the 4-byte length):
 *
 *   [2-byte opcode (big-endian unsigned short)][protobuf payload]
 *
 * The [LengthFieldPrepender] in the pipeline then wraps this with a 4-byte length prefix,
 * producing the final wire format:
 *
 *   [4-byte length][2-byte opcode][protobuf payload]
 */
class PacketEncoder : MessageToByteEncoder<Packet>() {

    private val logger = LoggerFactory.getLogger(PacketEncoder::class.java)

    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: ByteBuf) {
        out.writeShort(msg.opcode)
        out.writeBytes(msg.payload)
        logger.debug("Encoded {} to {}", msg, ctx.channel().remoteAddress())
    }
}
