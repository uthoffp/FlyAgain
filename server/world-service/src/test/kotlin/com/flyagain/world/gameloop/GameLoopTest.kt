package com.flyagain.world.gameloop

import com.flyagain.world.ai.MonsterAI
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.handler.MovementHandler
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.session.SessionLifecycleManager
import com.flyagain.world.zone.ZoneManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test

class GameLoopTest {

    private val inputQueue = InputQueue()
    private val entityManager = EntityManager()
    private val zoneManager = ZoneManager(entityManager)
    private val movementHandler = mockk<MovementHandler>(relaxed = true)
    private val monsterAI = mockk<MonsterAI>(relaxed = true)
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val sessionLifecycleManager = mockk<SessionLifecycleManager>(relaxed = true)
    private val testScope = TestScope()

    private fun makeGameLoop(tickRate: Int = 20): GameLoop {
        return GameLoop(
            inputQueue = inputQueue,
            entityManager = entityManager,
            zoneManager = zoneManager,
            movementHandler = movementHandler,
            monsterAI = monsterAI,
            broadcastService = broadcastService,
            sessionLifecycleManager = sessionLifecycleManager,
            asyncScope = testScope,
            tickRate = tickRate
        )
    }

    @Test
    fun `start and stop lifecycle`() {
        val loop = makeGameLoop()
        loop.start()
        Thread.sleep(150) // let a few ticks run
        loop.stop()
        // Should not throw, should terminate cleanly
    }

    @Test
    fun `double start does not create two threads`() {
        val loop = makeGameLoop()
        loop.start()
        loop.start() // second start should be ignored
        Thread.sleep(100)
        loop.stop()
    }

    @Test
    fun `stop without start does nothing`() {
        val loop = makeGameLoop()
        loop.stop() // should not throw
    }

    @Test
    fun `game loop processes movement for moving players`() {
        zoneManager.initialize()
        val player = PlayerEntity(
            entityId = 1L,
            characterId = 101L,
            accountId = 201L,
            name = "Mover",
            characterClass = 1,
            x = 500f, y = 0f, z = 500f,
            isMoving = true,
            inputDx = 1f, inputDy = 0f, inputDz = 0f
        )
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val loop = makeGameLoop()
        loop.start()
        Thread.sleep(200) // let several ticks run
        loop.stop()

        // movementHandler.applyMovement should have been called
        verify(atLeast = 1) { movementHandler.applyMovement(any(), any(), any()) }
    }

    @Test
    fun `game loop updates monster AI`() {
        zoneManager.initialize()

        val loop = makeGameLoop()
        loop.start()
        Thread.sleep(200)
        loop.stop()

        verify(atLeast = 1) { monsterAI.updateChannel(any(), any()) }
    }

    @Test
    fun `game loop flushes broadcast service each tick`() {
        zoneManager.initialize()

        val loop = makeGameLoop()
        loop.start()
        Thread.sleep(200)
        loop.stop()

        verify(atLeast = 1) { broadcastService.flushPendingUpdates() }
        verify(atLeast = 1) { broadcastService.flushNetworkWrites() }
    }

    @Test
    fun `game loop drains input queue`() {
        zoneManager.initialize()

        // Enqueue some packets
        inputQueue.enqueue(QueuedPacket(accountId = 1L, opcode = 0x0101, payload = byteArrayOf(), tcpChannel = null))
        inputQueue.enqueue(QueuedPacket(accountId = 2L, opcode = 0x0101, payload = byteArrayOf(), tcpChannel = null))

        val loop = makeGameLoop()
        loop.start()
        Thread.sleep(200)
        loop.stop()

        // InputQueue should be empty after processing
        assertTrue(inputQueue.isEmpty())
    }

    private fun assertTrue(condition: Boolean) {
        kotlin.test.assertTrue(condition)
    }
}
