package com.flyagain.world.handler

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.EnterWorldRequest
import com.flyagain.common.proto.EnterWorldResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.RedisSessionSecretProvider
import com.flyagain.world.zone.ZoneManager
import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import java.util.Date
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnterWorldHandlerTest {

    companion object {
        private const val JWT_SECRET = "test-jwt-secret-key-for-unit-tests"
        private const val WRONG_JWT_SECRET = "wrong-secret-key-totally-different"
        private const val ACCOUNT_ID = "acc-uuid-123"
        private const val CHARACTER_ID = "char-uuid-456"
        private const val SESSION_ID = "sess-uuid-789"
        private const val SESSION_TOKEN = "1234567890"
        private const val HMAC_SECRET = "hmac-test-secret"
        private const val PLAYER_NAME = "TestHero"
    }

    private val entityManager = EntityManager()
    private val zoneManager = ZoneManager(entityManager)

    // Mock Redis sync commands for hgetall calls in the handler
    private val redisSync = mockk<RedisCommands<String, String>>(relaxed = true)
    private val redisAsync = mockk<RedisAsyncCommands<String, String>>(relaxed = true)
    private val redisConnection = mockk<StatefulRedisConnection<String, String>>(relaxed = true) {
        every { sync() } returns redisSync
        every { async() } returns redisAsync
    }

    private val sessionSecretProvider = mockk<RedisSessionSecretProvider>(relaxed = true)

    private val handler = EnterWorldHandler(
        entityManager = entityManager,
        zoneManager = zoneManager,
        redisConnection = redisConnection,
        jwtSecret = JWT_SECRET,
        sessionSecretProvider = sessionSecretProvider
    )

    init {
        // Initialize zones so addPlayerToZone works
        zoneManager.initialize()

        // Default: Redis async sadd returns a completed future (used after successful entry)
        @Suppress("UNCHECKED_CAST")
        val saddFuture = mockk<RedisFuture<Long>>()
        val cf = CompletableFuture.completedFuture(1L)
        every { saddFuture.toCompletableFuture() } returns cf
        every { saddFuture.isDone } returns true
        every { saddFuture.get() } returns 1L
        every { saddFuture.get(any(), any()) } returns 1L
        every { redisAsync.sadd(any(), any()) } returns saddFuture
    }

    // ---- Helper functions ----

    private fun makeValidJwt(
        accountId: String = ACCOUNT_ID,
        secret: String = JWT_SECRET
    ): String {
        return JWT.create()
            .withSubject(accountId)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600_000)) // 1 hour from now
            .sign(Algorithm.HMAC256(secret))
    }

    private fun makeRequest(
        jwt: String = makeValidJwt(),
        characterId: String = CHARACTER_ID,
        sessionId: String = SESSION_ID
    ): EnterWorldRequest {
        return EnterWorldRequest.newBuilder()
            .setJwt(jwt)
            .setCharacterId(characterId)
            .setSessionId(sessionId)
            .build()
    }

    private fun mockCtx(): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        return ctx
    }

    private fun setupRedisCharacter(
        characterId: String = CHARACTER_ID,
        accountId: String = ACCOUNT_ID,
        mapId: Int = ZoneManager.ZONE_AERHEIM,
        name: String = PLAYER_NAME
    ) {
        every { redisSync.hgetall("character:$characterId") } returns mapOf(
            "account_id" to accountId,
            "name" to name,
            "class" to "1",
            "map_id" to mapId.toString(),
            "pos_x" to "500.0",
            "pos_y" to "0.0",
            "pos_z" to "500.0",
            "rotation" to "0.0",
            "level" to "5",
            "hp" to "200",
            "max_hp" to "200",
            "mp" to "80",
            "max_mp" to "80",
            "str" to "15",
            "sta" to "12",
            "dex" to "10",
            "int" to "8",
            "stat_points" to "3",
            "xp" to "1500",
            "gold" to "5000"
        )
    }

    private fun setupRedisSession(
        sessionId: String = SESSION_ID,
        sessionToken: String = SESSION_TOKEN,
        hmacSecret: String = HMAC_SECRET
    ) {
        every { redisSync.hgetall("session:$sessionId") } returns mapOf(
            "sessionToken" to sessionToken,
            "hmacSecret" to hmacSecret
        )
    }

    private fun setupFullSuccess(
        accountId: String = ACCOUNT_ID,
        characterId: String = CHARACTER_ID,
        sessionId: String = SESSION_ID,
        mapId: Int = ZoneManager.ZONE_AERHEIM
    ) {
        setupRedisCharacter(characterId = characterId, accountId = accountId, mapId = mapId)
        setupRedisSession(sessionId = sessionId)
    }

    private fun captureResponse(ctx: ChannelHandlerContext): Packet {
        val packetSlot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(packetSlot)) }
        return packetSlot.captured
    }

    private fun captureErrorResponse(ctx: ChannelHandlerContext): EnterWorldResponse {
        val packet = captureResponse(ctx)
        assertEquals(Opcode.ENTER_WORLD_VALUE, packet.opcode)
        return EnterWorldResponse.parseFrom(packet.payload)
    }

    // ---- Tests ----

    @Test
    fun `rejects invalid JWT`() = runTest {
        val ctx = mockCtx()
        val request = makeRequest(jwt = "not-a-valid-jwt-token")

        val result = handler.handle(ctx, request)

        assertNull(result)
        val response = captureErrorResponse(ctx)
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("authentication", ignoreCase = true))
    }

    @Test
    fun `rejects JWT signed with wrong secret`() = runTest {
        val ctx = mockCtx()
        val wrongJwt = makeValidJwt(secret = WRONG_JWT_SECRET)
        val request = makeRequest(jwt = wrongJwt)

        val result = handler.handle(ctx, request)

        assertNull(result)
        val response = captureErrorResponse(ctx)
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("authentication", ignoreCase = true))
    }

    @Test
    fun `rejects when character not in Redis cache`() = runTest {
        val ctx = mockCtx()
        // Redis returns empty map for character lookup
        every { redisSync.hgetall("character:$CHARACTER_ID") } returns emptyMap()
        val request = makeRequest()

        val result = handler.handle(ctx, request)

        assertNull(result)
        val response = captureErrorResponse(ctx)
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("Character data not found", ignoreCase = true))
    }

    @Test
    fun `rejects when character belongs to different account`() = runTest {
        val ctx = mockCtx()
        // Character belongs to a different account
        setupRedisCharacter(accountId = "different-account-id")
        val request = makeRequest()

        val result = handler.handle(ctx, request)

        assertNull(result)
        val response = captureErrorResponse(ctx)
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("Character not found", ignoreCase = true))
    }

    @Test
    fun `rejects when session not in Redis`() = runTest {
        val ctx = mockCtx()
        setupRedisCharacter()
        // Session returns empty map
        every { redisSync.hgetall("session:$SESSION_ID") } returns emptyMap()
        val request = makeRequest()

        val result = handler.handle(ctx, request)

        assertNull(result)
        val response = captureErrorResponse(ctx)
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("Session expired", ignoreCase = true))
    }

    @Test
    fun `rejects when session missing token or secret`() = runTest {
        val ctx = mockCtx()
        setupRedisCharacter()
        // Session has data but missing hmacSecret
        every { redisSync.hgetall("session:$SESSION_ID") } returns mapOf(
            "sessionToken" to SESSION_TOKEN
            // hmacSecret intentionally missing
        )
        val request = makeRequest()

        val result = handler.handle(ctx, request)

        assertNull(result)
        val response = captureErrorResponse(ctx)
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("Session invalid", ignoreCase = true))
    }

    @Test
    fun `rejects duplicate login for same account`() = runTest {
        // First, successfully add a player for this account
        setupFullSuccess()
        val ctx1 = mockCtx()
        val result1 = handler.handle(ctx1, makeRequest())
        assertNotNull(result1, "First login should succeed")

        // Second login attempt for the same account should fail
        val ctx2 = mockCtx()
        val request2 = makeRequest(
            jwt = makeValidJwt(),
            characterId = "char-uuid-different",
            sessionId = "sess-uuid-different"
        )
        // Setup Redis for the second character, same account
        setupRedisCharacter(characterId = "char-uuid-different", accountId = ACCOUNT_ID)
        setupRedisSession(sessionId = "sess-uuid-different")

        val result2 = handler.handle(ctx2, request2)

        assertNull(result2)
        val response = captureErrorResponse(ctx2)
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("already in world", ignoreCase = true))
    }

    @Test
    fun `rejects when zone is full and cleans up entity`() = runTest {
        val ctx = mockCtx()
        setupFullSuccess()

        // Create a ZoneManager where all zones are full by using a special setup:
        // We use a real ZoneManager but fill the zone to capacity first
        val localEntityManager = EntityManager()
        val localZoneManager = ZoneManager(localEntityManager)
        localZoneManager.initialize()

        // Create handler with local managers
        val localHandler = EnterWorldHandler(
            entityManager = localEntityManager,
            zoneManager = localZoneManager,
            redisConnection = redisConnection,
            jwtSecret = JWT_SECRET,
            sessionSecretProvider = sessionSecretProvider
        )

        // Mock zoneManager behavior: we need to mock addPlayerToZone to return null.
        // Since ZoneManager is not an interface, we mock it directly.
        val mockedZoneManager = mockk<ZoneManager>()
        every { mockedZoneManager.zoneExists(any()) } returns true
        every { mockedZoneManager.addPlayerToZone(any(), any()) } returns null

        val mockedHandler = EnterWorldHandler(
            entityManager = localEntityManager,
            zoneManager = mockedZoneManager,
            redisConnection = redisConnection,
            jwtSecret = JWT_SECRET,
            sessionSecretProvider = sessionSecretProvider
        )

        val request = makeRequest()
        val result = mockedHandler.handle(ctx, request)

        assertNull(result)
        val response = captureErrorResponse(ctx)
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("Failed to enter zone", ignoreCase = true))

        // Verify entity was cleaned up (removePlayer was called)
        // The entityId was generated by localEntityManager, so the player should have been removed
        assertEquals(0, localEntityManager.getPlayerCount(), "Player entity should be cleaned up after zone failure")
    }

    @Test
    fun `falls back to Aerheim when zone does not exist`() = runTest {
        val ctx = mockCtx()
        // Character has a non-existent zone (mapId = 999)
        setupRedisCharacter(mapId = 999)
        setupRedisSession()
        val request = makeRequest()

        val result = handler.handle(ctx, request)

        assertNotNull(result, "Should succeed with fallback to Aerheim")
        assertEquals(ZoneManager.ZONE_AERHEIM, result.zoneId)
        assertEquals(PLAYER_NAME, result.name)
    }

    @Test
    fun `returns player entity on successful entry`() = runTest {
        val ctx = mockCtx()
        setupFullSuccess()
        val request = makeRequest()

        val result = handler.handle(ctx, request)

        assertNotNull(result, "Should return non-null player entity")
        assertEquals(CHARACTER_ID, result.characterId)
        assertEquals(ACCOUNT_ID, result.accountId)
        assertEquals(PLAYER_NAME, result.name)
        assertEquals(1, result.characterClass)
        assertEquals(500f, result.x)
        assertEquals(0f, result.y)
        assertEquals(500f, result.z)
        assertEquals(5, result.level)
        assertEquals(200, result.hp)
        assertEquals(200, result.maxHp)
        assertEquals(80, result.mp)
        assertEquals(80, result.maxMp)
        assertEquals(15, result.str)
        assertEquals(12, result.sta)
        assertEquals(10, result.dex)
        assertEquals(8, result.int)
        assertEquals(3, result.statPoints)
        assertEquals(1500L, result.xp)
        assertEquals(5000L, result.gold)
        assertEquals(SESSION_ID, result.sessionId)
        assertEquals(SESSION_TOKEN.toLong(), result.sessionTokenLong)
        assertEquals(HMAC_SECRET, result.hmacSecret)
        assertEquals(ZoneManager.ZONE_AERHEIM, result.zoneId)

        // Verify session secret was registered
        verify(exactly = 1) {
            sessionSecretProvider.registerSecret(
                SESSION_TOKEN.toLong(),
                HMAC_SECRET.toByteArray(Charsets.UTF_8)
            )
        }

        // Verify player is tracked in entity manager
        assertNotNull(entityManager.getPlayer(result.entityId))
        assertNotNull(entityManager.getPlayerByAccount(ACCOUNT_ID))
    }
}
