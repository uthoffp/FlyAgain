package com.flyagain.world.gameloop

import io.netty.channel.Channel
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

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
class InputQueue(private val maxSize: Int = 50_000) {

    private val logger = LoggerFactory.getLogger(InputQueue::class.java)
    private val queue = ConcurrentLinkedQueue<QueuedPacket>()
    private val count = AtomicInteger(0)

    // Pre-allocated drain buffer reused every tick to avoid ArrayList allocation.
    // Only accessed from the single game loop thread via drainAll().
    private val drainBuffer = ArrayList<QueuedPacket>(1024)

    /**
     * Enqueue a packet from a network thread.
     * This is thread-safe and non-blocking.
     * Returns false and drops the packet if the queue is at capacity.
     */
    fun enqueue(packet: QueuedPacket): Boolean {
        if (count.get() >= maxSize) {
            logger.warn("InputQueue full ({} packets), dropping packet from account {}", maxSize, packet.accountId)
            return false
        }
        queue.offer(packet)
        count.incrementAndGet()
        return true
    }

    /**
     * Dequeue a single packet for processing. Returns null if queue is empty.
     */
    fun dequeue(): QueuedPacket? {
        val packet = queue.poll() ?: return null
        count.decrementAndGet()
        return packet
    }

    /**
     * Drain all currently queued packets into a reusable list for batch processing.
     * The returned list is valid until the next call to drainAll().
     * Only call from the game loop thread.
     */
    fun drainAll(): List<QueuedPacket> {
        drainBuffer.clear()
        var packet = queue.poll()
        while (packet != null) {
            drainBuffer.add(packet)
            packet = queue.poll()
        }
        count.addAndGet(-drainBuffer.size)
        return drainBuffer
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
