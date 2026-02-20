package com.flyagain.common.network

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Reusable Netty UDP server for real-time game traffic (movement, position updates).
 *
 * UDP wire format (per ARCHITECTURE.md):
 * ```
 * [SessionToken (8B)][Sequence (4B) uint32][Opcode (2B)][Protobuf Payload (N)][HMAC-SHA256 (32B)]
 * ```
 *
 * Processing pipeline:
 * 1. Size check (max [MAX_UDP_PACKET_SIZE] = 512 bytes)
 * 2. [UdpFloodProtection] — per-IP rate limiting (max 100 packets/s, in-memory)
 * 3. HMAC-SHA256 verification (session secret looked up via [sessionSecretProvider])
 * 4. Sequence check (replay/duplicate detection)
 * 5. Decode into [UdpPacket] and hand off to [packetHandler]
 *
 * @param port UDP port to bind to.
 * @param packetHandler callback invoked for each validated [UdpPacket].
 * @param sessionSecretProvider resolves a session token (8-byte long) to its HMAC secret, or null if unknown.
 * @param floodProtection the [UdpFloodProtection] instance for per-IP rate limiting.
 */
class UdpServer(
    private val port: Int,
    private val packetHandler: UdpPacketHandler,
    private val sessionSecretProvider: SessionSecretProvider,
    private val floodProtection: UdpFloodProtection
) {

    private val logger = LoggerFactory.getLogger(UdpServer::class.java)

    private var group: EventLoopGroup? = null
    private var channel: Channel? = null

    /** Tracks the last accepted sequence number per session for replay detection. */
    private val lastSequence = ConcurrentHashMap<Long, AtomicLong>()

    companion object {
        const val MAX_UDP_PACKET_SIZE = 512
        private const val SESSION_TOKEN_LENGTH = 8
        private const val SEQUENCE_LENGTH = 4
        private const val OPCODE_LENGTH = 2
        private const val HMAC_LENGTH = 32
        private const val MIN_PACKET_SIZE = SESSION_TOKEN_LENGTH + SEQUENCE_LENGTH + OPCODE_LENGTH + HMAC_LENGTH
    }

    fun start() {
        val eventLoopGroup = NioEventLoopGroup()
        group = eventLoopGroup

        val bootstrap = Bootstrap()
            .group(eventLoopGroup)
            .channel(NioDatagramChannel::class.java)
            .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
            .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
            .handler(UdpChannelHandler())

        val future = bootstrap.bind(port).sync()
        channel = future.channel()
        logger.info("UDP server listening on port {}", port)
    }

    fun stop() {
        logger.info("Stopping UDP server...")
        try {
            channel?.close()?.sync()
        } catch (e: Exception) {
            logger.warn("Error closing UDP channel: {}", e.message)
        }
        group?.shutdownGracefully(0, 5, TimeUnit.SECONDS)?.sync()
        lastSequence.clear()
        logger.info("UDP server stopped")
    }

    /**
     * Removes sequence tracking state for the given session.
     * Call this when a session ends to prevent unbounded map growth.
     */
    fun removeSession(sessionToken: Long) {
        lastSequence.remove(sessionToken)
    }

    /**
     * Sends a raw UDP datagram to the specified address.
     * The caller is responsible for constructing the full wire-format bytes.
     */
    fun send(data: ByteArray, address: InetSocketAddress) {
        val ch = channel ?: return
        val buf = ch.alloc().buffer(data.size)
        buf.writeBytes(data)
        ch.writeAndFlush(DatagramPacket(buf, address))
    }

    private inner class UdpChannelHandler : SimpleChannelInboundHandler<DatagramPacket>() {

        /** Reuse Mac instances per thread to avoid expensive Mac.getInstance() per packet. */
        private val threadLocalMac = ThreadLocal.withInitial {
            Mac.getInstance("HmacSHA256")
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
            val sender = msg.sender()
            val buf = msg.content()
            val readableBytes = buf.readableBytes()

            // 1. Size check
            if (readableBytes > MAX_UDP_PACKET_SIZE || readableBytes < MIN_PACKET_SIZE) {
                logger.debug("UDP packet from {} rejected: size {} (min={}, max={})",
                    sender, readableBytes, MIN_PACKET_SIZE, MAX_UDP_PACKET_SIZE)
                return
            }

            // 2. Flood protection (in-memory, before any Redis/crypto)
            val ip = sender.address.hostAddress
            if (!floodProtection.allowPacket(ip)) {
                logger.debug("UDP flood protection: dropping packet from {}", ip)
                return
            }

            // 3. Read raw bytes for HMAC verification
            val rawBytes = ByteArray(readableBytes)
            buf.getBytes(buf.readerIndex(), rawBytes)

            // Extract fields
            val sessionToken = buf.readLong()
            val sequence = buf.readUnsignedInt()
            val opcode = buf.readUnsignedShort()

            val payloadLength = readableBytes - MIN_PACKET_SIZE
            val payload = ByteArray(payloadLength)
            if (payloadLength > 0) {
                buf.readBytes(payload)
            }

            val receivedHmac = ByteArray(HMAC_LENGTH)
            buf.readBytes(receivedHmac)

            // 4. HMAC verification
            val secret = sessionSecretProvider.getSecret(sessionToken)
            if (secret == null) {
                logger.debug("UDP packet from {}: unknown session token {}", sender, sessionToken)
                return
            }

            val dataToSign = ByteArray(readableBytes - HMAC_LENGTH)
            System.arraycopy(rawBytes, 0, dataToSign, 0, dataToSign.size)

            if (!verifyHmac(dataToSign, receivedHmac, secret)) {
                logger.warn("UDP packet from {}: HMAC verification failed", sender)
                return
            }

            // 5. Sequence check — reject replayed or out-of-order packets
            val lastSeq = lastSequence.computeIfAbsent(sessionToken) { AtomicLong(0) }
            val lastVal = lastSeq.get()
            if (sequence <= lastVal) {
                logger.debug("UDP packet from {}: replay/duplicate seq {} (last={})", sender, sequence, lastVal)
                return
            }
            lastSeq.set(sequence)

            // 6. Hand off to packet handler
            val udpPacket = UdpPacket(
                sessionToken = sessionToken,
                sequence = sequence,
                opcode = opcode,
                payload = payload,
                sender = sender
            )
            packetHandler.handlePacket(udpPacket)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            logger.error("UDP server error", cause)
        }

        private fun verifyHmac(data: ByteArray, expectedHmac: ByteArray, secret: ByteArray): Boolean {
            return try {
                val mac = threadLocalMac.get()
                mac.init(SecretKeySpec(secret, "HmacSHA256"))
                val computedHmac = mac.doFinal(data)
                MessageDigest.isEqual(computedHmac, expectedHmac)
            } catch (e: Exception) {
                logger.error("HMAC computation failed: {}", e.message)
                false
            }
        }
    }
}

/**
 * Represents a decoded and verified UDP packet.
 */
data class UdpPacket(
    val sessionToken: Long,
    val sequence: Long,
    val opcode: Int,
    val payload: ByteArray,
    val sender: InetSocketAddress
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UdpPacket) return false
        return sessionToken == other.sessionToken &&
                sequence == other.sequence &&
                opcode == other.opcode &&
                payload.contentEquals(other.payload) &&
                sender == other.sender
    }

    override fun hashCode(): Int {
        var result = sessionToken.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + opcode
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + sender.hashCode()
        return result
    }

    override fun toString(): String {
        return "UdpPacket(session=$sessionToken, seq=$sequence, opcode=0x${opcode.toString(16).padStart(4, '0')}, payloadSize=${payload.size}, from=$sender)"
    }
}

/**
 * Callback interface for handling validated UDP packets.
 */
fun interface UdpPacketHandler {
    fun handlePacket(packet: UdpPacket)
}

/**
 * Resolves a session token to its HMAC secret.
 * Returns null if the session is unknown/expired.
 */
fun interface SessionSecretProvider {
    fun getSecret(sessionToken: Long): ByteArray?
}
