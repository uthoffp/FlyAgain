package com.flyagain.world.session

import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.google.protobuf.Empty
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneManager
import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.netty.channel.Channel
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionLifecycleManagerTest {

    private val entityManager = EntityManager()
    private val zoneManager = ZoneManager(entityManager)
    private val broadcastService = mockk<BroadcastService>(relaxed = true)

    // Mock Redis with proper CompletableFuture returns for pipeline await() calls
    private val redisAsync = mockk<RedisAsyncCommands<String, String>>(relaxed = true)
    private val redisConnection = mockk<StatefulRedisConnection<String, String>>(relaxed = true) {
        every { async() } returns redisAsync
    }

    private val characterDataStub = mockk<CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub>()

    private val manager: SessionLifecycleManager

    init {
        // Setup Redis async to return completed futures for all pipeline operations
        every { redisAsync.del(any<String>()) } returns completedRedisFuture(1L)
        every { redisAsync.srem(any(), any()) } returns completedRedisFuture(1L)
        every { redisAsync.hset(any<String>(), any<Map<String, String>>()) } returns completedRedisFuture(1L)
        every { redisAsync.expire(any<String>(), any<Long>()) } returns completedRedisFuture(true)
        every { redisAsync.set(any<String>(), any<String>()) } returns completedRedisFuture("OK")
        every { redisAsync.hget(any(), any()) } returns completedRedisFuture(null)
        every { redisAsync.get(any<String>()) } returns completedRedisFuture(null)

        // Setup gRPC stub
        coEvery { characterDataStub.saveCharacter(any(), any()) } returns Empty.getDefaultInstance()

        manager = SessionLifecycleManager(
            entityManager, zoneManager, redisConnection, characterDataStub, broadcastService
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> completedRedisFuture(value: T): RedisFuture<T> {
        val future = mockk<RedisFuture<T>>()
        val cf = CompletableFuture.completedFuture(value)
        every { future.toCompletableFuture() } returns cf as CompletableFuture<T>
        // For kotlinx.coroutines.future.await() — it calls toCompletableFuture() internally
        every { future.isDone } returns true
        every { future.get() } returns value
        every { future.get(any(), any()) } returns value
        return future
    }

    private fun makePlayer(
        entityId: Long = 1L,
        characterId: Long = 101L,
        accountId: Long = 201L
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = characterId,
            accountId = accountId,
            name = "Player$entityId",
            characterClass = 1,
            x = 100f, y = 0f, z = 100f,
            hp = 500, maxHp = 500,
            mp = 50, maxMp = 50,
            level = 5, zoneId = 1, channelId = 0,
            sessionId = "test-session-123"
        )
    }

    @Test
    fun `snapshotPlayer creates correct field map`() {
        val player = makePlayer()
        player.gold = 1000L
        player.xp = 5000L

        val (characterId, fields) = manager.snapshotPlayer(player)

        assertEquals(player.characterId, characterId)
        assertEquals(player.accountId.toString(), fields["account_id"])
        assertEquals(player.name, fields["name"])
        assertEquals(player.level.toString(), fields["level"])
        assertEquals(player.hp.toString(), fields["hp"])
        assertEquals(player.mp.toString(), fields["mp"])
        assertEquals(player.gold.toString(), fields["gold"])
        assertEquals(player.xp.toString(), fields["xp"])
        assertEquals(player.x.toString(), fields["pos_x"])
        assertEquals(player.y.toString(), fields["pos_y"])
        assertEquals(player.z.toString(), fields["pos_z"])
        assertEquals(player.zoneId.toString(), fields["map_id"])
    }

    @Test
    fun `snapshotPlayer includes all stat fields`() {
        val player = makePlayer()
        player.str = 25
        player.sta = 20
        player.dex = 15
        player.int = 30
        player.statPoints = 5

        val (_, fields) = manager.snapshotPlayer(player)

        assertEquals("25", fields["str"])
        assertEquals("20", fields["sta"])
        assertEquals("15", fields["dex"])
        assertEquals("30", fields["int_stat"])
        assertEquals("5", fields["stat_points"])
    }

    @Test
    fun `flushCharacterToDatabase calls gRPC and clears dirty flag`() = runTest {
        val player = makePlayer()
        player.dirty = true

        manager.flushCharacterToDatabase(player)

        assertFalse(player.dirty)
        coVerify(exactly = 1) { characterDataStub.saveCharacter(any(), any()) }
    }

    @Test
    fun `handleDisconnect removes player from entity manager`() = runTest {
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.initialize()
        zoneManager.addPlayerToZone(player, 1)

        assertNotNull(entityManager.getPlayer(player.entityId))

        manager.handleDisconnect(player)

        assertNull(entityManager.getPlayer(player.entityId))
    }

    @Test
    fun `handleDisconnect removes player from zone`() = runTest {
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.initialize()
        val channel = zoneManager.addPlayerToZone(player, 1)
        assertNotNull(channel)
        assertEquals(1, channel.getPlayerCount())

        manager.handleDisconnect(player)

        assertEquals(0, channel.getPlayerCount())
    }

    @Test
    fun `handleDisconnect broadcasts despawn`() = runTest {
        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.initialize()
        zoneManager.addPlayerToZone(player, 1)

        manager.handleDisconnect(player)

        verify(exactly = 1) { broadcastService.broadcastEntityDespawn(any(), eq(player.entityId), any(), any()) }
    }

    @Test
    fun `handleDisconnect closes tcp channel if active`() = runTest {
        val mockTcp = mockk<Channel>(relaxed = true)
        every { mockTcp.isActive } returns true
        val player = makePlayer().copy(tcpChannel = mockTcp)
        entityManager.tryAddPlayer(player)
        zoneManager.initialize()
        zoneManager.addPlayerToZone(player, 1)

        manager.handleDisconnect(player)

        verify(exactly = 1) { mockTcp.close() }
    }

    @Test
    fun `handleDisconnect does not close inactive tcp channel`() = runTest {
        val mockTcp = mockk<Channel>(relaxed = true)
        every { mockTcp.isActive } returns false
        val player = makePlayer().copy(tcpChannel = mockTcp)
        entityManager.tryAddPlayer(player)
        zoneManager.initialize()
        zoneManager.addPlayerToZone(player, 1)

        manager.handleDisconnect(player)

        verify(exactly = 0) { mockTcp.close() }
    }

    @Test
    fun `handleDisconnect continues even if gRPC flush fails`() = runTest {
        // Override the default coEvery to throw
        coEvery { characterDataStub.saveCharacter(any(), any()) } throws RuntimeException("gRPC down")

        val player = makePlayer()
        entityManager.tryAddPlayer(player)
        zoneManager.initialize()
        zoneManager.addPlayerToZone(player, 1)

        // Should not throw, should still clean up
        manager.handleDisconnect(player)

        assertNull(entityManager.getPlayer(player.entityId))

        // Restore for subsequent tests
        coEvery { characterDataStub.saveCharacter(any(), any()) } returns Empty.getDefaultInstance()
    }
}
