package com.flyagain.world

import com.flyagain.world.gameloop.InputQueue
import com.flyagain.world.gameloop.QueuedPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InputQueueTest {

    private fun makePacket(accountId: Long = 1L, opcode: Int = 0x0101): QueuedPacket {
        return QueuedPacket(
            accountId = accountId,
            opcode = opcode,
            payload = byteArrayOf(),
            tcpChannel = null
        )
    }

    @Test
    fun `new queue is empty`() {
        val queue = InputQueue()
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size())
    }

    @Test
    fun `enqueue increases size`() {
        val queue = InputQueue()
        queue.enqueue(makePacket())
        assertEquals(1, queue.size())
        queue.enqueue(makePacket(accountId = 2L))
        assertEquals(2, queue.size())
    }

    @Test
    fun `dequeue returns packet in FIFO order`() {
        val queue = InputQueue()
        val p1 = makePacket(accountId = 1L)
        val p2 = makePacket(accountId = 2L)
        queue.enqueue(p1)
        queue.enqueue(p2)

        val first = queue.dequeue()
        assertNotNull(first)
        assertEquals(1L, first.accountId)

        val second = queue.dequeue()
        assertNotNull(second)
        assertEquals(2L, second.accountId)
    }

    @Test
    fun `dequeue returns null when empty`() {
        val queue = InputQueue()
        assertNull(queue.dequeue())
    }

    @Test
    fun `drainAll returns all packets and empties the queue`() {
        val queue = InputQueue()
        queue.enqueue(makePacket(accountId = 1L))
        queue.enqueue(makePacket(accountId = 2L))
        queue.enqueue(makePacket(accountId = 3L))

        val packets = queue.drainAll()
        assertEquals(3, packets.size)
        assertEquals(1L, packets[0].accountId)
        assertEquals(2L, packets[1].accountId)
        assertEquals(3L, packets[2].accountId)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `drainAll returns empty list when queue is empty`() {
        val queue = InputQueue()
        val packets = queue.drainAll()
        assertTrue(packets.isEmpty())
    }

    @Test
    fun `isEmpty returns false after enqueue`() {
        val queue = InputQueue()
        queue.enqueue(makePacket())
        assertTrue(!queue.isEmpty())
    }

    @Test
    fun `size decreases after dequeue`() {
        val queue = InputQueue()
        queue.enqueue(makePacket())
        queue.enqueue(makePacket(accountId = 2L))
        assertEquals(2, queue.size())
        queue.dequeue()
        assertEquals(1, queue.size())
    }
}
