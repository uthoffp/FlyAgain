package com.flyagain.world.handler

import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.session.SessionLifecycleManager
import com.flyagain.world.zone.ZoneManager
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneChangeHandlerTest {

    private val entityManager = EntityManager()
    private val zoneManager = ZoneManager(entityManager)
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val sessionLifecycleManager = mockk<SessionLifecycleManager>(relaxed = true)

    private val handler = ZoneChangeHandler(
        entityManager, zoneManager, broadcastService, sessionLifecycleManager
    )

    private fun makePlayer(entityId: Long = 1L): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = entityId + 100,
            accountId = entityId + 200,
            name = "Player$entityId",
            characterClass = 1,
            x = 500f, y = 0f, z = 500f,
            hp = 500, maxHp = 500
        )
    }

    private fun mockCtx(): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val channel = mockk<Channel>(relaxed = true)
        val attr = mockk<io.netty.util.Attribute<Any>>(relaxed = true)
        io.mockk.every { ctx.channel() } returns channel
        io.mockk.every { channel.attr<Any>(any()) } returns attr
        return ctx
    }

    @Test
    fun `handleZoneChange moves player to new zone`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.handleZoneChange(ctx, player, ZoneManager.ZONE_GRUENE_EBENE)

        assertEquals(ZoneManager.ZONE_GRUENE_EBENE, player.zoneId)
        assertTrue(player.dirty)
    }

    @Test
    fun `handleZoneChange broadcasts despawn in old zone`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.handleZoneChange(ctx, player, ZoneManager.ZONE_GRUENE_EBENE)

        verify(exactly = 1) { broadcastService.broadcastEntityDespawn(any(), eq(player.entityId), any(), any()) }
    }

    @Test
    fun `handleZoneChange flushes character to database`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.handleZoneChange(ctx, player, ZoneManager.ZONE_GRUENE_EBENE)

        io.mockk.coVerify(exactly = 1) { sessionLifecycleManager.flushCharacterToDatabase(player) }
    }

    @Test
    fun `handleZoneChange rejects non-existent zone`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.handleZoneChange(ctx, player, 999)

        // Player should still be in Aerheim
        assertEquals(ZoneManager.ZONE_AERHEIM, player.zoneId)
        // Error should be sent
        verify(atLeast = 1) { ctx.writeAndFlush(any()) }
    }

    @Test
    fun `handleZoneChange rejects same zone`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.handleZoneChange(ctx, player, ZoneManager.ZONE_AERHEIM)

        // No despawn should occur
        verify(exactly = 0) { broadcastService.broadcastEntityDespawn(any(), any(), any(), any()) }
        // Error sent
        verify(atLeast = 1) { ctx.writeAndFlush(any()) }
    }

    @Test
    fun `handleZoneChange sets default spawn for target zone`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.handleZoneChange(ctx, player, ZoneManager.ZONE_DUNKLER_WALD)

        // Dunkler Wald spawn: (100, 0, 100)
        assertEquals(100f, player.x)
        assertEquals(0f, player.y)
        assertEquals(100f, player.z)
        assertFalse(player.isFlying)
    }

    // --- Channel switch tests ---

    @Test
    fun `handleChannelSwitch rejects non-existent channel`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.handleChannelSwitch(ctx, player, 99)

        verify(atLeast = 1) { ctx.writeAndFlush(any()) }
    }

    @Test
    fun `handleChannelSwitch rejects same channel`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.handleChannelSwitch(ctx, player, 0) // already in channel 0

        verify(atLeast = 1) { ctx.writeAndFlush(any()) }
    }

    // --- sendChannelList tests ---

    @Test
    fun `sendChannelList sends response with channel info`() = runTest {
        zoneManager.initialize()
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.addPlayerToZone(player, ZoneManager.ZONE_AERHEIM)

        val ctx = mockCtx()
        handler.sendChannelList(ctx, player)

        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }
}
