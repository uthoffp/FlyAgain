package com.flyagain.login.ratelimit

import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    private val asyncCommands = mockk<RedisAsyncCommands<String, String>>()
    private val redisConnection = mockk<StatefulRedisConnection<String, String>> {
        every { async() } returns asyncCommands
    }
    private val rateLimiter = RateLimiter(redisConnection)

    private fun mockIncr(key: String, returnValue: Long) {
        val future = mockk<RedisFuture<Long>>()
        every { future.toCompletableFuture() } returns CompletableFuture.completedFuture(returnValue)
        every { asyncCommands.incr(key) } returns future
    }

    private fun mockExpire(key: String) {
        val future = mockk<RedisFuture<Boolean>>()
        every { future.toCompletableFuture() } returns CompletableFuture.completedFuture(true)
        every { asyncCommands.expire(key, any<Long>()) } returns future
    }

    private fun mockGet(key: String, value: String?) {
        val future = mockk<RedisFuture<String>>()
        every { future.toCompletableFuture() } returns CompletableFuture.completedFuture(value)
        every { asyncCommands.get(key) } returns future
    }

    @Test
    fun `login allows first attempt`() = runTest {
        val key = "rate_limit:10.0.0.1:login"
        mockIncr(key, 1L)
        mockExpire(key)

        assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.1"))
    }

    @Test
    fun `login allows up to 5 attempts`() = runTest {
        val key = "rate_limit:10.0.0.1:login"
        mockExpire(key)

        for (i in 1L..5L) {
            mockIncr(key, i)
            assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.1"), "Attempt $i should be allowed")
        }
    }

    @Test
    fun `login rejects 6th attempt`() = runTest {
        val key = "rate_limit:10.0.0.1:login"
        mockIncr(key, 6L)

        assertFalse(rateLimiter.checkLoginRateLimit("10.0.0.1"))
    }

    @Test
    fun `register allows up to 3 attempts`() = runTest {
        val key = "rate_limit:10.0.0.1:register"
        mockExpire(key)

        for (i in 1L..3L) {
            mockIncr(key, i)
            assertTrue(rateLimiter.checkRegisterRateLimit("10.0.0.1"), "Attempt $i should be allowed")
        }
    }

    @Test
    fun `register rejects 4th attempt`() = runTest {
        val key = "rate_limit:10.0.0.1:register"
        mockIncr(key, 4L)

        assertFalse(rateLimiter.checkRegisterRateLimit("10.0.0.1"))
    }

    @Test
    fun `different IPs have separate counters`() = runTest {
        val key1 = "rate_limit:10.0.0.1:login"
        val key2 = "rate_limit:10.0.0.2:login"
        mockIncr(key1, 5L)
        mockIncr(key2, 1L)
        mockExpire(key2)

        assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.1"))
        assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.2"))
    }

    @Test
    fun `fails open when Redis is unavailable`() = runTest {
        val future = mockk<RedisFuture<Long>>()
        every { future.toCompletableFuture() } returns CompletableFuture<Long>().apply {
            completeExceptionally(RuntimeException("Redis down"))
        }
        every { asyncCommands.incr(any()) } returns future

        assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.1"))
    }

    @Test
    fun `getRemainingAttempts returns correct count`() = runTest {
        mockGet("rate_limit:10.0.0.1:login", "3")

        assertEquals(2, rateLimiter.getRemainingAttempts("10.0.0.1", "login", 5))
    }

    @Test
    fun `getRemainingAttempts returns max when no key exists`() = runTest {
        mockGet("rate_limit:10.0.0.1:login", null)

        assertEquals(5, rateLimiter.getRemainingAttempts("10.0.0.1", "login", 5))
    }

    @Test
    fun `getRemainingAttempts returns 0 when over limit`() = runTest {
        mockGet("rate_limit:10.0.0.1:login", "10")

        assertEquals(0, rateLimiter.getRemainingAttempts("10.0.0.1", "login", 5))
    }

    @Test
    fun `getRemainingAttempts returns max on Redis failure`() = runTest {
        val future = mockk<RedisFuture<String>>()
        every { future.toCompletableFuture() } returns CompletableFuture<String>().apply {
            completeExceptionally(RuntimeException("Redis down"))
        }
        every { asyncCommands.get(any()) } returns future

        assertEquals(5, rateLimiter.getRemainingAttempts("10.0.0.1", "login", 5))
    }
}
