package com.flyagain.world

import com.flyagain.world.gameloop.InputQueue
import com.flyagain.world.gameloop.QueuedPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InputQueueTest {

    private fun makePacket(accountId: String = "1", opcode: Int = 0x0101): QueuedPacket {
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
        queue.enqueue(makePacket(accountId = "2"))
        assertEquals(2, queue.size())
    }

    @Test
    fun `dequeue returns packet in FIFO order`() {
        val queue = InputQueue()
        val p1 = makePacket(accountId = "1")
        val p2 = makePacket(accountId = "2")
        queue.enqueue(p1)
        queue.enqueue(p2)

        val first = queue.dequeue()
        assertNotNull(first)
        assertEquals("1", first.accountId)

        val second = queue.dequeue()
        assertNotNull(second)
        assertEquals("2", second.accountId)
    }

    @Test
    fun `dequeue returns null when empty`() {
        val queue = InputQueue()
        assertNull(queue.dequeue())
    }

    @Test
    fun `drainAll returns all packets and empties the queue`() {
        val queue = InputQueue()
        queue.enqueue(makePacket(accountId = "1"))
        queue.enqueue(makePacket(accountId = "2"))
        queue.enqueue(makePacket(accountId = "3"))

        val packets = queue.drainAll()
        assertEquals(3, packets.size)
        assertEquals("1", packets[0].accountId)
        assertEquals("2", packets[1].accountId)
        assertEquals("3", packets[2].accountId)
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
        queue.enqueue(makePacket(accountId = "2"))
        assertEquals(2, queue.size())
        queue.dequeue()
        assertEquals(1, queue.size())
    }
}
