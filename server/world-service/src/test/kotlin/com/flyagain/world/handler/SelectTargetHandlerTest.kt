package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.SelectTargetRequest
import com.flyagain.common.proto.SelectTargetResponse
import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.netty.channel.ChannelHandlerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectTargetHandlerTest {

    private val entityManager = mockk<EntityManager>(relaxed = true)
    private val zoneManager = mockk<ZoneManager>(relaxed = true)
    private val handler = SelectTargetHandler(entityManager, zoneManager)

    private fun makePlayer(
        entityId: Long = 1L,
        name: String = "Player$entityId",
        hp: Int = 500,
        maxHp: Int = 500,
        level: Int = 10,
        zoneId: Int = 1,
        channelId: Int = 0
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = "${entityId + 100}",
            accountId = "${entityId + 200}",
            name = name,
            characterClass = 1,
            x = 500f, y = 0f, z = 500f,
            hp = hp, maxHp = maxHp, level = level,
            zoneId = zoneId, channelId = channelId
        )
    }

    private fun makeMonster(
        entityId: Long = 100L,
        name: String = "TestMonster",
        hp: Int = 200,
        maxHp: Int = 200,
        level: Int = 5,
        aiState: AIState = AIState.IDLE
    ): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = name,
            x = 510f, y = 0f, z = 510f,
            spawnX = 510f, spawnY = 0f, spawnZ = 510f,
            hp = hp, maxHp = maxHp,
            attack = 20, defense = 10,
            level = level, xpReward = 50,
            aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 2000, moveSpeed = 3f,
            aiState = aiState
        )
    }

    private fun captureResponse(ctx: ChannelHandlerContext): SelectTargetResponse {
        val packetSlot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(packetSlot)) }
        val packet = packetSlot.captured
        assertEquals(Opcode.SELECT_TARGET_VALUE, packet.opcode)
        return SelectTargetResponse.parseFrom(packet.payload)
    }

    // --- Tests ---

    @Test
    fun `clear target with id 0 clears target and auto-attack`() {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()
        player.targetEntityId = 50L
        player.autoAttacking = true

        val request = SelectTargetRequest.newBuilder()
            .setTargetEntityId(0)
            .build()

        handler.handle(ctx, player, request)

        assertNull(player.targetEntityId)
        assertFalse(player.autoAttacking)

        val response = captureResponse(ctx)
        assertTrue(response.success)
        assertEquals(0L, response.targetEntityId)
    }

    @Test
    fun `selecting alive monster sets target and returns stats`() {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(zoneId = 1, channelId = 0)
        val monster = makeMonster(entityId = 100L, name = "Goblin", hp = 150, maxHp = 200, level = 5)

        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(100L) } returns monster

        val request = SelectTargetRequest.newBuilder()
            .setTargetEntityId(100L)
            .build()

        handler.handle(ctx, player, request)

        assertEquals(100L, player.targetEntityId)

        val response = captureResponse(ctx)
        assertTrue(response.success)
        assertEquals(100L, response.targetEntityId)
        assertEquals(150, response.targetHp)
        assertEquals(200, response.targetMaxHp)
        assertEquals("Goblin", response.targetName)
        assertEquals(5, response.targetLevel)
    }

    @Test
    fun `selecting dead monster is rejected`() {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(zoneId = 1, channelId = 0)
        val deadMonster = makeMonster(entityId = 100L, hp = 0, maxHp = 200, aiState = AIState.DEAD)

        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(100L) } returns deadMonster

        val request = SelectTargetRequest.newBuilder()
            .setTargetEntityId(100L)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertFalse(response.success)
        assertEquals(100L, response.targetEntityId)
    }

    @Test
    fun `selecting another player sets target and returns stats`() {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(entityId = 1L, zoneId = 1, channelId = 0)
        val targetPlayer = makePlayer(entityId = 2L, name = "OtherPlayer", hp = 400, maxHp = 500, level = 15)

        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(2L) } returns null
        every { channel.getPlayer(2L) } returns targetPlayer

        val request = SelectTargetRequest.newBuilder()
            .setTargetEntityId(2L)
            .build()

        handler.handle(ctx, player, request)

        assertEquals(2L, player.targetEntityId)

        val response = captureResponse(ctx)
        assertTrue(response.success)
        assertEquals(2L, response.targetEntityId)
        assertEquals(400, response.targetHp)
        assertEquals(500, response.targetMaxHp)
        assertEquals("OtherPlayer", response.targetName)
        assertEquals(15, response.targetLevel)
    }

    @Test
    fun `returns error when target not found`() {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(zoneId = 1, channelId = 0)

        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(999L) } returns null
        every { channel.getPlayer(999L) } returns null

        val request = SelectTargetRequest.newBuilder()
            .setTargetEntityId(999L)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertFalse(response.success)
        assertEquals(999L, response.targetEntityId)
    }

    @Test
    fun `returns error when not in valid zone channel`() {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(zoneId = 99, channelId = 0)

        every { zoneManager.getChannel(99, 0) } returns null

        val request = SelectTargetRequest.newBuilder()
            .setTargetEntityId(100L)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertFalse(response.success)
        assertEquals(100L, response.targetEntityId)
    }
}
