package com.flyagain.login.network

import com.flyagain.common.proto.Heartbeat
import com.flyagain.common.proto.LoginRequest
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.RegisterRequest
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import org.slf4j.LoggerFactory

/**
 * Decodes TCP packets from raw ByteBuf into typed Packet objects.
 *
 * Wire format (after length-prefix frame decoding):
 *   [2-byte opcode][protobuf payload]
 *
 * The LengthFieldBasedFrameDecoder upstream has already stripped the 4-byte length prefix
 * and delivered the remaining bytes (opcode + payload) as a ByteBuf.
 */
class PacketDecoder : MessageToMessageDecoder<ByteBuf>() {

    private val logger = LoggerFactory.getLogger(PacketDecoder::class.java)

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (msg.readableBytes() < 2) {
            logger.warn("Packet too short from {}: {} bytes", ctx.channel().remoteAddress(), msg.readableBytes())
            return
        }

        val opcodeValue = msg.readUnsignedShort()
        val opcode = try {
            Opcode.forNumber(opcodeValue)
        } catch (e: Exception) {
            null
        }

        if (opcode == null) {
            logger.warn("Unknown opcode 0x{} from {}", Integer.toHexString(opcodeValue), ctx.channel().remoteAddress())
            return
        }

        val payloadLength = msg.readableBytes()
        val payloadBytes = ByteArray(payloadLength)
        msg.readBytes(payloadBytes)

        val message: com.google.protobuf.MessageLite? = try {
            when (opcode) {
                Opcode.LOGIN_REQUEST -> LoginRequest.parseFrom(payloadBytes)
                Opcode.REGISTER_REQUEST -> RegisterRequest.parseFrom(payloadBytes)
                Opcode.HEARTBEAT -> Heartbeat.parseFrom(payloadBytes)
                else -> {
                    logger.warn("Unhandled opcode {} from {}", opcode, ctx.channel().remoteAddress())
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse payload for opcode {} from {}: {}",
                opcode, ctx.channel().remoteAddress(), e.message)
            null
        }

        if (message != null) {
            out.add(Packet(opcode, message))
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("PacketDecoder error from {}: {}", ctx.channel().remoteAddress(), cause.message)
        ctx.close()
    }
}

/**
 * Represents a decoded network packet with its opcode and protobuf message.
 */
data class Packet(
    val opcode: Opcode,
    val message: com.google.protobuf.MessageLite
)
