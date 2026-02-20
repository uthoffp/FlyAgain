package com.flyagain.world.network

import com.flyagain.common.network.UdpPacket
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.gameloop.InputQueue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldUdpHandlerTest {

    private val inputQueue = InputQueue()
    private val entityManager = EntityManager()
    private val handler = WorldUdpHandler(inputQueue, entityManager)

    private fun makePlayer(entityId: Long = 1L, sessionToken: Long = 12345L): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = entityId + 100,
            accountId = entityId + 200,
            name = "Player$entityId",
            characterClass = 1,
            x = 0f, y = 0f, z = 0f,
            sessionTokenLong = sessionToken
        )
    }

    private fun makeUdpPacket(
        opcode: Int = Opcode.MOVEMENT_INPUT_VALUE,
        sessionToken: Long = 12345L,
        payload: ByteArray = byteArrayOf(1, 2, 3)
    ): UdpPacket {
        return UdpPacket(
            sessionToken = sessionToken,
            sequence = 1L,
            opcode = opcode,
            payload = payload,
            sender = InetSocketAddress("127.0.0.1", 12345)
        )
    }

    @Test
    fun `movement packet enqueued for valid session`() {
        val player = makePlayer(entityId = 1L, sessionToken = 12345L)
        entityManager.tryAddPlayer(player)

        val packet = makeUdpPacket(opcode = Opcode.MOVEMENT_INPUT_VALUE, sessionToken = 12345L)
        handler.handlePacket(packet)

        assertEquals(1, inputQueue.size())
        val queued = inputQueue.dequeue()!!
        assertEquals(player.accountId, queued.accountId)
        assertEquals(Opcode.MOVEMENT_INPUT_VALUE, queued.opcode)
    }

    @Test
    fun `movement packet ignored for unknown session token`() {
        val packet = makeUdpPacket(sessionToken = 99999L)
        handler.handlePacket(packet)

        assertTrue(inputQueue.isEmpty())
    }

    @Test
    fun `unknown opcode is silently ignored`() {
        val player = makePlayer(entityId = 1L, sessionToken = 12345L)
        entityManager.tryAddPlayer(player)

        val packet = makeUdpPacket(opcode = 0xFFFF, sessionToken = 12345L)
        handler.handlePacket(packet)

        assertTrue(inputQueue.isEmpty())
    }

    @Test
    fun `multiple movement packets from same session are enqueued`() {
        val player = makePlayer(entityId = 1L, sessionToken = 12345L)
        entityManager.tryAddPlayer(player)

        handler.handlePacket(makeUdpPacket(sessionToken = 12345L))
        handler.handlePacket(makeUdpPacket(sessionToken = 12345L))
        handler.handlePacket(makeUdpPacket(sessionToken = 12345L))

        assertEquals(3, inputQueue.size())
    }

    @Test
    fun `queued packet has correct tcp channel reference`() {
        val mockChannel = mockk<io.netty.channel.Channel>(relaxed = true)
        val player = makePlayer(entityId = 1L, sessionToken = 12345L)
            .copy(tcpChannel = mockChannel)
        entityManager.tryAddPlayer(player)

        handler.handlePacket(makeUdpPacket(sessionToken = 12345L))

        val queued = inputQueue.dequeue()!!
        assertEquals(mockChannel, queued.tcpChannel)
    }
}

class RedisSessionSecretProviderTest {

    @Test
    fun `registerSecret and getSecret returns cached value`() {
        val redis = mockk<io.lettuce.core.api.StatefulRedisConnection<String, String>>(relaxed = true)
        val provider = RedisSessionSecretProvider(redis)
        val secret = "my-secret".toByteArray()

        provider.registerSecret(12345L, secret)

        val result = provider.getSecret(12345L)
        assertTrue(result.contentEquals(secret))
    }

    @Test
    fun `removeSecret clears cached value`() {
        val redis = mockk<io.lettuce.core.api.StatefulRedisConnection<String, String>>(relaxed = true)
        val syncCommands = mockk<io.lettuce.core.api.sync.RedisCommands<String, String>>(relaxed = true)
        every { redis.sync() } returns syncCommands
        every { syncCommands.hget(any(), any()) } returns null

        val provider = RedisSessionSecretProvider(redis)
        val secret = "my-secret".toByteArray()

        provider.registerSecret(12345L, secret)
        provider.removeSecret(12345L)

        val result = provider.getSecret(12345L)
        assertEquals(null, result)
    }

    @Test
    fun `getSecret falls back to Redis for unknown token`() {
        val redis = mockk<io.lettuce.core.api.StatefulRedisConnection<String, String>>(relaxed = true)
        val syncCommands = mockk<io.lettuce.core.api.sync.RedisCommands<String, String>>(relaxed = true)
        every { redis.sync() } returns syncCommands
        every { syncCommands.hget(any(), eq("hmacSecret")) } returns "redis-secret"

        val provider = RedisSessionSecretProvider(redis)

        val result = provider.getSecret(12345L)
        assertTrue(result != null)
        assertEquals("redis-secret", String(result))
    }

    @Test
    fun `getSecret returns null when Redis has no secret`() {
        val redis = mockk<io.lettuce.core.api.StatefulRedisConnection<String, String>>(relaxed = true)
        val syncCommands = mockk<io.lettuce.core.api.sync.RedisCommands<String, String>>(relaxed = true)
        every { redis.sync() } returns syncCommands
        every { syncCommands.hget(any(), any()) } returns null

        val provider = RedisSessionSecretProvider(redis)

        val result = provider.getSecret(99999L)
        assertEquals(null, result)
    }

    @Test
    fun `getSecret caches Redis result for subsequent calls`() {
        val redis = mockk<io.lettuce.core.api.StatefulRedisConnection<String, String>>(relaxed = true)
        val syncCommands = mockk<io.lettuce.core.api.sync.RedisCommands<String, String>>(relaxed = true)
        every { redis.sync() } returns syncCommands
        every { syncCommands.hget(any(), eq("hmacSecret")) } returns "redis-secret"

        val provider = RedisSessionSecretProvider(redis)

        // First call hits Redis
        provider.getSecret(12345L)
        // Second call should use cache
        provider.getSecret(12345L)

        // Redis should only be called once
        verify(exactly = 1) { syncCommands.hget(any(), any()) }
    }
}
