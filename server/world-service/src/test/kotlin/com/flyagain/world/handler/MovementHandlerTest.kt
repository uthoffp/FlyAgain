package com.flyagain.world.handler

import com.flyagain.common.proto.MovementInput
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.gameloop.QueuedPacket
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovementHandlerTest {

    private val entityManager = EntityManager()
    private val zoneManager = ZoneManager(entityManager)
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val handler = MovementHandler(entityManager, zoneManager, broadcastService)

    private fun makePlayer(
        entityId: Long = 1L,
        accountId: Long = 100L,
        x: Float = 500f,
        y: Float = 0f,
        z: Float = 500f
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = entityId + 100,
            accountId = accountId,
            name = "Player$entityId",
            characterClass = 1,
            x = x, y = y, z = z
        )
    }

    private fun makeMovementPacket(
        accountId: Long,
        dx: Float = 1f,
        dy: Float = 0f,
        dz: Float = 0f,
        isMoving: Boolean = true,
        isFlying: Boolean = false,
        rotation: Float = 0f
    ): QueuedPacket {
        val input = MovementInput.newBuilder()
            .setDx(dx)
            .setDy(dy)
            .setDz(dz)
            .setIsMoving(isMoving)
            .setIsFlying(isFlying)
            .setRotation(rotation)
            .build()

        return QueuedPacket(
            accountId = accountId,
            opcode = 0x0101,
            payload = input.toByteArray(),
            tcpChannel = null
        )
    }

    // --- handleMovementInput tests ---

    @Test
    fun `handleMovementInput sets player movement state`() {
        val player = makePlayer(accountId = 100L)
        entityManager.tryAddPlayer(player)

        val packet = makeMovementPacket(accountId = 100L, dx = 1f, dz = 0f, isMoving = true, rotation = 1.5f)
        handler.handleMovementInput(packet)

        assertEquals(1f, player.inputDx)
        assertEquals(0f, player.inputDz)
        assertTrue(player.isMoving)
        assertEquals(1.5f, player.rotation)
    }

    @Test
    fun `handleMovementInput normalizes direction vector over length 1`() {
        val player = makePlayer(accountId = 100L)
        entityManager.tryAddPlayer(player)

        // Vector (3, 0, 4) has length 5 — should be normalized to (0.6, 0, 0.8)
        val packet = makeMovementPacket(accountId = 100L, dx = 3f, dy = 0f, dz = 4f)
        handler.handleMovementInput(packet)

        assertTrue(player.inputDx < 1f, "dx should be normalized, got ${player.inputDx}")
        assertTrue(player.inputDz < 1f, "dz should be normalized, got ${player.inputDz}")
    }

    @Test
    fun `handleMovementInput ignores unknown account`() {
        // No player with accountId = 999
        val packet = makeMovementPacket(accountId = 999L)
        handler.handleMovementInput(packet)
        // Should not throw
    }

    @Test
    fun `handleMovementInput rejects NaN input`() {
        val player = makePlayer(accountId = 100L)
        entityManager.tryAddPlayer(player)

        val input = MovementInput.newBuilder()
            .setDx(Float.NaN)
            .setDy(0f)
            .setDz(0f)
            .setIsMoving(true)
            .setRotation(0f)
            .build()

        val packet = QueuedPacket(
            accountId = 100L,
            opcode = 0x0101,
            payload = input.toByteArray(),
            tcpChannel = null
        )

        handler.handleMovementInput(packet)

        // Movement state should not be updated
        assertFalse(player.isMoving)
    }

    @Test
    fun `handleMovementInput toggles flight mode and marks dirty`() {
        val player = makePlayer(accountId = 100L)
        entityManager.tryAddPlayer(player)
        assertFalse(player.isFlying)

        val packet = makeMovementPacket(accountId = 100L, isFlying = true, isMoving = true)
        handler.handleMovementInput(packet)

        assertTrue(player.isFlying)
        assertTrue(player.dirty)
    }

    // --- applyMovement tests ---

    @Test
    fun `applyMovement moves player and updates spatial grid`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer(x = 500f, z = 500f)
        channel.addPlayer(player)

        player.isMoving = true
        player.inputDx = 1f
        player.inputDy = 0f
        player.inputDz = 0f

        val moved = handler.applyMovement(player, 50, channel) // 50ms tick

        assertTrue(moved)
        assertTrue(player.x > 500f, "Player should have moved in x direction")
        assertTrue(player.dirty)
    }

    @Test
    fun `applyMovement returns false when not moving`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer()
        channel.addPlayer(player)
        player.isMoving = false

        val moved = handler.applyMovement(player, 50, channel)
        assertFalse(moved)
    }

    @Test
    fun `applyMovement queues broadcast`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer(x = 500f, z = 500f)
        channel.addPlayer(player)

        player.isMoving = true
        player.inputDx = 1f
        player.inputDy = 0f
        player.inputDz = 0f

        handler.applyMovement(player, 50, channel)

        verify(exactly = 1) { broadcastService.queuePositionUpdate(player, channel) }
    }

    @Test
    fun `applyMovement rejects out-of-bounds position`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer(x = -99f, z = 500f) // near boundary
        channel.addPlayer(player)

        player.isMoving = true
        player.inputDx = -1f
        player.inputDy = 0f
        player.inputDz = 0f

        // With a long enough tick, this would push past WORLD_BOUNDARY_MIN (-100)
        val moved = handler.applyMovement(player, 5000, channel)
        assertFalse(moved, "Should reject out-of-bounds movement")
    }

    @Test
    fun `applyMovement uses fly speed when flying`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val groundPlayer = makePlayer(entityId = 1L, accountId = 1L, x = 500f, z = 500f)
        val flyPlayer = makePlayer(entityId = 2L, accountId = 2L, x = 500f, z = 500f)
        channel.addPlayer(groundPlayer)
        channel.addPlayer(flyPlayer)

        groundPlayer.isMoving = true
        groundPlayer.inputDx = 1f
        groundPlayer.inputDy = 0f
        groundPlayer.inputDz = 0f

        flyPlayer.isMoving = true
        flyPlayer.isFlying = true
        flyPlayer.inputDx = 1f
        flyPlayer.inputDy = 0f
        flyPlayer.inputDz = 0f

        handler.applyMovement(groundPlayer, 1000, channel)
        handler.applyMovement(flyPlayer, 1000, channel)

        assertTrue(flyPlayer.x > groundPlayer.x,
            "Flying player should move faster (fly=${flyPlayer.x}, ground=${groundPlayer.x})")
    }
}
