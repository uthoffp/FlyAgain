package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.UseSkillRequest
import com.flyagain.common.proto.UseSkillResponse
import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.combat.SkillSystem
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
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
import kotlin.test.assertTrue

class UseSkillHandlerTest {

    private val skillSystem = mockk<SkillSystem>()
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val zoneManager = mockk<ZoneManager>()
    private val handler = UseSkillHandler(skillSystem, broadcastService, zoneManager)

    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private fun makePlayer(
        entityId: Long = 1L,
        hp: Int = 100,
        zoneId: Int = 1,
        channelId: Int = 0
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = "${entityId + 100}",
            accountId = "${entityId + 200}",
            name = "Player$entityId",
            characterClass = 1,
            x = 500f, y = 0f, z = 500f,
            hp = hp, maxHp = 100,
            zoneId = zoneId,
            channelId = channelId
        )
    }

    private fun makeRequest(skillId: Int = 42, targetEntityId: Long = 999L): UseSkillRequest {
        return UseSkillRequest.newBuilder()
            .setSkillId(skillId)
            .setTargetEntityId(targetEntityId)
            .build()
    }

    private fun captureResponse(): UseSkillResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return UseSkillResponse.parseFrom(slot.captured.payload)
    }

    private fun makeDamageResult(
        damage: Int = 50,
        isCritical: Boolean = false,
        targetKilled: Boolean = false,
        targetEntityId: Long = 999L,
        attackerEntityId: Long = 1L
    ): CombatEngine.DamageResult {
        return CombatEngine.DamageResult(
            damage = damage,
            isCritical = isCritical,
            targetKilled = targetKilled,
            targetEntityId = targetEntityId,
            attackerEntityId = attackerEntityId
        )
    }

    // --- Tests ---

    @Test
    fun `rejects skill use when player is dead`() {
        val player = makePlayer(hp = 0)
        val request = makeRequest(skillId = 10)

        handler.handle(ctx, player, request)

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals(10, response.skillId)
        assertEquals("Cannot use skills while dead", response.errorMessage)

        // SkillSystem should never be called
        verify(exactly = 0) { skillSystem.useSkill(any(), any(), any()) }
    }

    @Test
    fun `forwards skill system error message to client`() {
        val player = makePlayer(hp = 100)
        val request = makeRequest(skillId = 7, targetEntityId = 50L)

        every { skillSystem.useSkill(player, 7, 50L) } returns
            SkillSystem.SkillResult.Error("Not enough MP (need 30, have 10)")

        handler.handle(ctx, player, request)

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals(7, response.skillId)
        assertEquals("Not enough MP (need 30, have 10)", response.errorMessage)
    }

    @Test
    fun `sends success and broadcasts damage on skill success`() {
        val player = makePlayer(hp = 100, zoneId = 1, channelId = 0)
        val request = makeRequest(skillId = 42, targetEntityId = 999L)
        val damageResult = makeDamageResult()
        val channel = mockk<ZoneChannel>()

        every { skillSystem.useSkill(player, 42, 999L) } returns
            SkillSystem.SkillResult.Success(damageResult)
        every { zoneManager.getChannel(1, 0) } returns channel

        handler.handle(ctx, player, request)

        val response = captureResponse()
        assertTrue(response.success)
        assertEquals(42, response.skillId)

        verify(exactly = 1) { broadcastService.broadcastDamageEvent(channel, damageResult) }
    }

    @Test
    fun `skips broadcast when channel is null but still sends success`() {
        val player = makePlayer(hp = 100, zoneId = 1, channelId = 0)
        val request = makeRequest(skillId = 42, targetEntityId = 999L)
        val damageResult = makeDamageResult()

        every { skillSystem.useSkill(player, 42, 999L) } returns
            SkillSystem.SkillResult.Success(damageResult)
        every { zoneManager.getChannel(1, 0) } returns null

        handler.handle(ctx, player, request)

        val response = captureResponse()
        assertTrue(response.success)
        assertEquals(42, response.skillId)

        verify(exactly = 0) { broadcastService.broadcastDamageEvent(any(), any()) }
    }

    @Test
    fun `response contains correct skillId`() {
        val player = makePlayer(hp = 100)
        val request = makeRequest(skillId = 123, targetEntityId = 50L)
        val damageResult = makeDamageResult(attackerEntityId = 1L, targetEntityId = 50L)
        val channel = mockk<ZoneChannel>()

        every { skillSystem.useSkill(player, 123, 50L) } returns
            SkillSystem.SkillResult.Success(damageResult)
        every { zoneManager.getChannel(1, 0) } returns channel

        handler.handle(ctx, player, request)

        val response = captureResponse()
        assertTrue(response.success)
        assertEquals(123, response.skillId)
    }
}
