package com.flyagain.world.handler

import com.flyagain.common.network.HeartbeatTracker
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.EnterWorldRequest
import com.flyagain.common.proto.Heartbeat
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.session.SessionLifecycleManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.Attribute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test

class PacketRouterTest {

    private val enterWorldHandler = mockk<EnterWorldHandler>(relaxed = true)
    private val zoneChangeHandler = mockk<ZoneChangeHandler>(relaxed = true)
    private val entityManager = EntityManager()
    private val sessionLifecycleManager = mockk<SessionLifecycleManager>(relaxed = true)
    private val heartbeatTracker = mockk<HeartbeatTracker>(relaxed = true)
    private val testScope = TestScope()

    private val router = PacketRouter(
        enterWorldHandler, zoneChangeHandler, entityManager,
        sessionLifecycleManager, heartbeatTracker, testScope
    )

    private fun mockCtx(
        player: PlayerEntity? = null,
        authenticated: Boolean = false
    ): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val channel = mockk<Channel>(relaxed = true)

        val playerAttr = mockk<Attribute<PlayerEntity>>(relaxed = true)
        every { playerAttr.get() } returns player

        val authAttr = mockk<Attribute<Boolean>>(relaxed = true)
        every { authAttr.get() } returns authenticated

        every { channel.attr(PacketRouter.PLAYER_ENTITY_KEY) } returns playerAttr
        every { channel.attr(PacketRouter.AUTHENTICATED_KEY) } returns authAttr
        every { ctx.channel() } returns channel

        return ctx
    }

    private fun makePlayer(): PlayerEntity {
        return PlayerEntity(
            entityId = 1L,
            characterId = 101L,
            accountId = 201L,
            name = "TestPlayer",
            characterClass = 1,
            x = 0f, y = 0f, z = 0f
        )
    }

    // --- Heartbeat ---

    @Test
    fun `heartbeat is processed without authentication`() {
        val ctx = mockCtx()
        val heartbeat = Heartbeat.newBuilder()
            .setClientTime(System.currentTimeMillis())
            .build()
        val packet = Packet(Opcode.HEARTBEAT_VALUE, heartbeat.toByteArray())

        router.channelRead(ctx, packet)

        verify(exactly = 1) { heartbeatTracker.recordHeartbeat(any()) }
        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }

    // --- Unauthenticated access ---

    @Test
    fun `unauthenticated packet sends error response`() {
        val ctx = mockCtx(player = null)
        val packet = Packet(Opcode.ZONE_DATA_VALUE, byteArrayOf())

        router.channelRead(ctx, packet)

        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }

    @Test
    fun `enter world when already authenticated sends error`() {
        val ctx = mockCtx(authenticated = true)
        val request = EnterWorldRequest.newBuilder()
            .setCharacterId(1L)
            .setSessionId("test")
            .setJwt("test.jwt.token")
            .build()
        val packet = Packet(Opcode.ENTER_WORLD_VALUE, request.toByteArray())

        router.channelRead(ctx, packet)

        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }

    // --- Authenticated routing ---

    @Test
    fun `channel list request routes to zoneChangeHandler`() {
        val player = makePlayer()
        val ctx = mockCtx(player = player)
        val packet = Packet(Opcode.CHANNEL_LIST_VALUE, byteArrayOf())

        router.channelRead(ctx, packet)

        verify(exactly = 1) { zoneChangeHandler.sendChannelList(ctx, player) }
    }

    @Test
    fun `unsupported opcode sends error`() {
        val player = makePlayer()
        val ctx = mockCtx(player = player)
        val packet = Packet(0xFFFF, byteArrayOf())

        router.channelRead(ctx, packet)

        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }

    // --- Connection lifecycle ---

    @Test
    fun `channelActive registers heartbeat tracking`() {
        val ctx = mockCtx()
        router.channelActive(ctx)

        verify(exactly = 1) { heartbeatTracker.register(any()) }
    }

    @Test
    fun `channelInactive unregisters heartbeat tracking`() {
        val ctx = mockCtx()
        router.channelInactive(ctx)

        verify(exactly = 1) { heartbeatTracker.unregister(any()) }
    }

    @Test
    fun `channelInactive triggers disconnect handler for authenticated player`() {
        val player = makePlayer()
        val ctx = mockCtx(player = player)

        router.channelInactive(ctx)

        // Disconnect handler runs in coroutine
        testScope.testScheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { sessionLifecycleManager.handleDisconnect(player) }
    }

    @Test
    fun `channelInactive does nothing when no player`() {
        val ctx = mockCtx(player = null)

        router.channelInactive(ctx)
        testScope.testScheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 0) { sessionLifecycleManager.handleDisconnect(any()) }
    }

    @Test
    fun `IdleStateEvent closes connection`() {
        val ctx = mockCtx()
        router.userEventTriggered(ctx, IdleStateEvent.ALL_IDLE_STATE_EVENT)

        verify(exactly = 1) { ctx.close() }
    }

    @Test
    fun `exceptionCaught closes connection`() {
        val ctx = mockCtx()
        router.exceptionCaught(ctx, RuntimeException("test error"))

        verify(exactly = 1) { ctx.close() }
    }

    // --- Rate limiting ---

    @Test
    fun `zone change cooldown sends error on rapid requests`() {
        val player = makePlayer()
        player.lastZoneChangeTime = System.currentTimeMillis() // just changed
        val ctx = mockCtx(player = player)

        val packet = Packet(Opcode.ZONE_DATA_VALUE, byteArrayOf())
        router.channelRead(ctx, packet)

        // Should get rate limit error
        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }

    @Test
    fun `channel switch cooldown sends error on rapid requests`() {
        val player = makePlayer()
        player.lastChannelSwitchTime = System.currentTimeMillis() // just switched
        val ctx = mockCtx(player = player)

        val packet = Packet(Opcode.CHANNEL_SWITCH_VALUE, byteArrayOf())
        router.channelRead(ctx, packet)

        // Should get rate limit error
        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }
}
