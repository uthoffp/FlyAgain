package com.flyagain.world.gameloop

import io.netty.channel.Channel
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents a queued packet from a client, ready for processing
 * by the game loop on the main game thread.
 */
data class QueuedPacket(
    val accountId: Long,
    val opcode: Int,
    val payload: ByteArray,
    val tcpChannel: Channel?,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueuedPacket) return false
        return accountId == other.accountId && opcode == other.opcode && timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = accountId.hashCode()
        result = 31 * result + opcode
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Thread-safe input queue for incoming client packets.
 * Network threads enqueue packets; the game loop thread dequeues and processes them.
 * Uses ConcurrentLinkedQueue for lock-free, multi-producer single-consumer access.
 */
class InputQueue {

    private val queue = ConcurrentLinkedQueue<QueuedPacket>()

    /**
     * Enqueue a packet from a network thread.
     * This is thread-safe and non-blocking.
     */
    fun enqueue(packet: QueuedPacket) {
        queue.offer(packet)
    }

    /**
     * Dequeue a single packet for processing. Returns null if queue is empty.
     */
    fun dequeue(): QueuedPacket? {
        return queue.poll()
    }

    /**
     * Drain all currently queued packets into a list for batch processing.
     * Returns an empty list if the queue is empty.
     */
    fun drainAll(): List<QueuedPacket> {
        val packets = mutableListOf<QueuedPacket>()
        var packet = queue.poll()
        while (packet != null) {
            packets.add(packet)
            packet = queue.poll()
        }
        return packets
    }

    /**
     * Get the current approximate size of the queue.
     */
    fun size(): Int = queue.size

    /**
     * Check if the queue is empty.
     */
    fun isEmpty(): Boolean = queue.isEmpty()
}
