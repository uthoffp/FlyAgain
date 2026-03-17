package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.ToggleAutoAttackRequest
import com.flyagain.common.proto.ToggleAutoAttackResponse
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

class ToggleAutoAttackHandlerTest {

    private val entityManager = mockk<EntityManager>(relaxed = true)
    private val zoneManager = mockk<ZoneManager>(relaxed = true)
    private val handler = ToggleAutoAttackHandler(entityManager, zoneManager)

    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private fun makePlayer(
        entityId: Long = 1L,
        zoneId: Int = 1,
        channelId: Int = 0,
        targetEntityId: Long? = null,
        autoAttacking: Boolean = false
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = "${entityId + 100}",
            accountId = "${entityId + 200}",
            name = "Player$entityId",
            characterClass = 1,
            x = 500f, y = 0f, z = 500f,
            zoneId = zoneId,
            channelId = channelId,
            targetEntityId = targetEntityId,
            autoAttacking = autoAttacking
        )
    }

    private fun makeMonster(
        entityId: Long = 100L,
        hp: Int = 100,
        aiState: AIState = AIState.IDLE
    ): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = "TestMonster",
            x = 510f, y = 0f, z = 510f,
            spawnX = 510f, spawnY = 0f, spawnZ = 510f,
            hp = hp,
            maxHp = 100,
            attack = 10,
            defense = 5,
            level = 1,
            xpReward = 50,
            aggroRange = 20f,
            attackRange = 3f,
            attackSpeedMs = 1000,
            moveSpeed = 3f,
            aiState = aiState
        )
    }

    private fun makeRequest(enable: Boolean, targetEntityId: Long = 0L): ToggleAutoAttackRequest {
        return ToggleAutoAttackRequest.newBuilder()
            .setEnable(enable)
            .setTargetEntityId(targetEntityId)
            .build()
    }

    private fun captureResponse(): ToggleAutoAttackResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        assertEquals(Opcode.TOGGLE_AUTO_ATTACK_VALUE, slot.captured.opcode)
        return ToggleAutoAttackResponse.parseFrom(slot.captured.payload)
    }

    @Test
    fun `disable auto-attack always succeeds`() {
        val player = makePlayer(autoAttacking = true, targetEntityId = 50L)
        val request = makeRequest(enable = false)

        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        val response = captureResponse()
        assertFalse(response.autoAttacking)
        assertEquals(50L, response.targetEntityId)
    }

    @Test
    fun `enable with no target fails`() {
        val player = makePlayer(targetEntityId = null)
        val request = makeRequest(enable = true, targetEntityId = 0L)

        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        val response = captureResponse()
        assertFalse(response.autoAttacking)
        assertEquals(0L, response.targetEntityId)
    }

    @Test
    fun `enable sets targetEntityId from request before validation`() {
        // Player has no target, but request provides one.
        // Even if channel lookup fails, the targetEntityId should have been set on the player.
        val player = makePlayer(targetEntityId = null)
        every { zoneManager.getChannel(player.zoneId, player.channelId) } returns null

        val request = makeRequest(enable = true, targetEntityId = 77L)
        handler.handle(ctx, player, request)

        // The handler sets player.targetEntityId = 77L before checking the channel.
        // When channel is null, it sets autoAttacking = false but does NOT clear targetEntityId
        // (see source lines 43-45 and 60-63).
        assertFalse(player.autoAttacking)
        assertEquals(77L, player.targetEntityId)
    }

    @Test
    fun `enable with alive monster succeeds`() {
        val player = makePlayer(zoneId = 1, channelId = 0)
        val monster = makeMonster(entityId = 100L, hp = 50, aiState = AIState.IDLE)
        val channel = mockk<ZoneChannel>(relaxed = true)

        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(100L) } returns monster

        val request = makeRequest(enable = true, targetEntityId = 100L)
        handler.handle(ctx, player, request)

        assertTrue(player.autoAttacking)
        assertEquals(100L, player.targetEntityId)
        val response = captureResponse()
        assertTrue(response.autoAttacking)
        assertEquals(100L, response.targetEntityId)
    }

    @Test
    fun `enable with dead monster disables and clears target`() {
        val player = makePlayer(zoneId = 1, channelId = 0, targetEntityId = 100L)
        val deadMonster = makeMonster(entityId = 100L, hp = 0, aiState = AIState.DEAD)
        val channel = mockk<ZoneChannel>(relaxed = true)

        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(100L) } returns deadMonster

        val request = makeRequest(enable = true)
        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        assertNull(player.targetEntityId)
        val response = captureResponse()
        assertFalse(response.autoAttacking)
        assertEquals(0L, response.targetEntityId)
    }

    @Test
    fun `enable with invalid channel (null) disables auto-attack`() {
        val player = makePlayer(zoneId = 99, channelId = 0, targetEntityId = 50L)
        every { zoneManager.getChannel(99, 0) } returns null

        val request = makeRequest(enable = true)
        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        // When channel is null, the handler returns early without sending a response
        // and without clearing targetEntityId (lines 60-63)
        verify(exactly = 0) { ctx.writeAndFlush(any()) }
    }

    @Test
    fun `enable with non-existent target disables and clears target`() {
        val player = makePlayer(zoneId = 1, channelId = 0, targetEntityId = 999L)
        val channel = mockk<ZoneChannel>(relaxed = true)

        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(999L) } returns null

        val request = makeRequest(enable = true)
        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        assertNull(player.targetEntityId)
        val response = captureResponse()
        assertFalse(response.autoAttacking)
        assertEquals(0L, response.targetEntityId)
    }
}
