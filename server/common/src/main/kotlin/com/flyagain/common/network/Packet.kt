package com.flyagain.common.network

/**
 * Represents a decoded network packet exchanged between client and server.
 *
 * The network layer treats packets as raw bytes — protobuf deserialization
 * is deferred to service-specific handlers (lazy decoding).
 *
 * @param opcode The 2-byte opcode identifying the message type (see [com.flyagain.common.proto.Opcode]).
 * @param payload The raw protobuf-encoded payload bytes.
 */
data class Packet(
    val opcode: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Packet) return false
        return opcode == other.opcode && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = opcode
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "Packet(opcode=0x${opcode.toString(16).padStart(4, '0')}, payloadSize=${payload.size})"
    }
}
