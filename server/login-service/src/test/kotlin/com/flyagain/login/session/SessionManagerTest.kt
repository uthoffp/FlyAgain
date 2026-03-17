package com.flyagain.login.session

import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    private val asyncCommands = mockk<RedisAsyncCommands<String, String>>(relaxed = true)
    private val redisConnection = mockk<StatefulRedisConnection<String, String>> {
        every { async() } returns asyncCommands
    }
    private val sessionManager = SessionManager(redisConnection, sessionTtlSeconds = 3600L)

    private fun <T> redisFuture(value: T): RedisFuture<T> {
        val future = mockk<RedisFuture<T>>()
        every { future.toCompletableFuture() } returns CompletableFuture.completedFuture(value)
        return future
    }

    private fun <T> failedFuture(error: Throwable): RedisFuture<T> {
        val future = mockk<RedisFuture<T>>()
        every { future.toCompletableFuture() } returns CompletableFuture<T>().apply {
            completeExceptionally(error)
        }
        return future
    }

    private fun setupBasicRedis() {
        every { asyncCommands.get(any()) } returns redisFuture(null)
        every { asyncCommands.hset(any(), any<Map<String, String>>()) } returns redisFuture(6L)
        every { asyncCommands.expire(any(), any<Long>()) } returns redisFuture(true)
        every { asyncCommands.set(any(), any()) } returns redisFuture("OK")
        every { asyncCommands.del(any<String>()) } returns redisFuture(1L)
        every { asyncCommands.hget(any(), any()) } returns redisFuture(null)
        every { asyncCommands.hgetall(any()) } returns redisFuture(emptyMap())
        every { asyncCommands.exists(any<String>()) } returns redisFuture(1L)
    }

    @Test
    fun `createSession returns true on success`() = runTest {
        setupBasicRedis()

        val result = sessionManager.createSession("sess-1", 12345L, "acc-1", "127.0.0.1", "secret")

        assertTrue(result)
    }

    @Test
    fun `createSession stores session hash and reverse lookup`() = runTest {
        setupBasicRedis()

        sessionManager.createSession("sess-1", 12345L, "acc-1", "127.0.0.1", "secret")

        verify { asyncCommands.hset("session:sess-1", any<Map<String, String>>()) }
        verify { asyncCommands.expire("session:sess-1", 3600L) }
        verify { asyncCommands.set("session:account:acc-1", "sess-1") }
        verify { asyncCommands.expire("session:account:acc-1", 3600L) }
    }

    @Test
    fun `createSession deletes old session on multi-login`() = runTest {
        setupBasicRedis()
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("old-session")
        every { asyncCommands.hget("session:old-session", "accountId") } returns redisFuture("acc-1")

        val result = sessionManager.createSession("sess-new", 99L, "acc-1", "127.0.0.1", "secret2")

        assertTrue(result)
        verify { asyncCommands.del("session:old-session") }
    }

    @Test
    fun `createSession returns false on Redis failure`() = runTest {
        every { asyncCommands.get(any()) } returns failedFuture(RuntimeException("Redis down"))

        val result = sessionManager.createSession("sess-1", 12345L, "acc-1", "127.0.0.1", "secret")

        assertFalse(result)
    }

    @Test
    fun `getSession returns data when session exists`() = runTest {
        setupBasicRedis()
        val sessionData = mapOf("accountId" to "acc-1", "ip" to "127.0.0.1")
        every { asyncCommands.hgetall("session:sess-1") } returns redisFuture(sessionData)

        val result = sessionManager.getSession("sess-1")

        assertEquals("acc-1", result?.get("accountId"))
        assertEquals("127.0.0.1", result?.get("ip"))
    }

    @Test
    fun `getSession returns null when session missing`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hgetall("session:nonexistent") } returns redisFuture(emptyMap())

        val result = sessionManager.getSession("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getSession returns null on Redis failure`() = runTest {
        every { asyncCommands.hgetall(any()) } returns failedFuture(RuntimeException("timeout"))

        val result = sessionManager.getSession("sess-1")

        assertNull(result)
    }

    @Test
    fun `getExistingSession returns session ID when active`() = runTest {
        setupBasicRedis()
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("sess-1")
        every { asyncCommands.exists("session:sess-1") } returns redisFuture(1L)

        val result = sessionManager.getExistingSession("acc-1")

        assertEquals("sess-1", result)
    }

    @Test
    fun `getExistingSession returns null when no session`() = runTest {
        setupBasicRedis()
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture(null)

        val result = sessionManager.getExistingSession("acc-1")

        assertNull(result)
    }

    @Test
    fun `getExistingSession cleans up stale reverse lookup`() = runTest {
        setupBasicRedis()
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("expired-sess")
        every { asyncCommands.exists("session:expired-sess") } returns redisFuture(0L)

        val result = sessionManager.getExistingSession("acc-1")

        assertNull(result)
        verify { asyncCommands.del("session:account:acc-1") }
    }

    @Test
    fun `getExistingSession returns null on Redis failure`() = runTest {
        every { asyncCommands.get(any()) } returns failedFuture(RuntimeException("connection lost"))

        val result = sessionManager.getExistingSession("acc-1")

        assertNull(result)
    }

    @Test
    fun `deleteSession removes session and reverse lookup`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hget("session:sess-1", "accountId") } returns redisFuture("acc-1")
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("sess-1")

        sessionManager.deleteSession("sess-1")

        verify { asyncCommands.del("session:sess-1") }
        verify { asyncCommands.del("session:account:acc-1") }
    }

    @Test
    fun `deleteSession preserves reverse lookup when pointing to different session`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hget("session:old-sess", "accountId") } returns redisFuture("acc-1")
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("new-sess")

        sessionManager.deleteSession("old-sess")

        verify { asyncCommands.del("session:old-sess") }
        verify(exactly = 0) { asyncCommands.del("session:account:acc-1") }
    }

    @Test
    fun `deleteSession handles missing accountId gracefully`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hget("session:sess-1", "accountId") } returns redisFuture(null)

        sessionManager.deleteSession("sess-1")

        verify { asyncCommands.del("session:sess-1") }
    }

    @Test
    fun `updateCharacterId sets field in Redis`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hset("session:sess-1", "characterId", "c-42") } returns redisFuture(true)

        sessionManager.updateCharacterId("sess-1", "c-42")

        verify { asyncCommands.hset("session:sess-1", "characterId", "c-42") }
    }
}
