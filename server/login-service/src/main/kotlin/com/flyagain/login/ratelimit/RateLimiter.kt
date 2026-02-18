package com.flyagain.login.ratelimit

import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory

/**
 * Redis-based rate limiter for login and registration requests.
 *
 * Uses simple increment-with-TTL counters in Redis:
 * - Login:    rate_limit:{ip}:login     => max 5 requests per 60 seconds
 * - Register: rate_limit:{ip}:register  => max 3 requests per 3600 seconds (1 hour)
 *
 * The counter key is created with an expiry (TTL). Each request increments the counter.
 * If the counter exceeds the limit, the request is rejected.
 */
class RateLimiter(
    private val redisConnection: StatefulRedisConnection<String, String>
) {

    private val logger = LoggerFactory.getLogger(RateLimiter::class.java)

    companion object {
        private const val RATE_LIMIT_PREFIX = "rate_limit:"

        // Login: 5 attempts per 60 seconds
        private const val LOGIN_SUFFIX = "login"
        private const val LOGIN_MAX_ATTEMPTS = 5
        private const val LOGIN_WINDOW_SECONDS = 60L

        // Register: 3 attempts per 3600 seconds (1 hour)
        private const val REGISTER_SUFFIX = "register"
        private const val REGISTER_MAX_ATTEMPTS = 3
        private const val REGISTER_WINDOW_SECONDS = 3600L
    }

    /**
     * Check if a login attempt from this IP is within rate limits.
     * Increments the counter as a side effect.
     *
     * @param ip The client IP address.
     * @return true if the request is allowed, false if rate-limited.
     */
    suspend fun checkLoginRateLimit(ip: String): Boolean {
        return checkAndIncrement(ip, LOGIN_SUFFIX, LOGIN_MAX_ATTEMPTS, LOGIN_WINDOW_SECONDS)
    }

    /**
     * Check if a registration attempt from this IP is within rate limits.
     * Increments the counter as a side effect.
     *
     * @param ip The client IP address.
     * @return true if the request is allowed, false if rate-limited.
     */
    suspend fun checkRegisterRateLimit(ip: String): Boolean {
        return checkAndIncrement(ip, REGISTER_SUFFIX, REGISTER_MAX_ATTEMPTS, REGISTER_WINDOW_SECONDS)
    }

    /**
     * Generic rate limit check-and-increment.
     * Uses INCR + EXPIRE pattern for atomic increment with sliding window.
     *
     * @param ip The client IP address.
     * @param action The action suffix (login or register).
     * @param maxAttempts Maximum number of attempts in the window.
     * @param windowSeconds The time window in seconds.
     * @return true if allowed, false if rate-limited.
     */
    private suspend fun checkAndIncrement(
        ip: String,
        action: String,
        maxAttempts: Int,
        windowSeconds: Long
    ): Boolean {
        val key = "$RATE_LIMIT_PREFIX$ip:$action"
        val async = redisConnection.async()

        return try {
            // Increment the counter
            val count = async.incr(key).await()

            if (count == 1L) {
                // First request in this window -- set the TTL
                async.expire(key, windowSeconds).await()
            }

            if (count > maxAttempts) {
                logger.warn("Rate limit exceeded for IP {} on action {} ({}/{})", ip, action, count, maxAttempts)
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.error("Rate limit check failed for IP {} on action {}: {}", ip, action, e.message)
            // Fail-open: allow the request if Redis is unavailable
            true
        }
    }

    /**
     * Get the remaining attempts for a given IP and action.
     * Useful for informational responses.
     *
     * @param ip The client IP address.
     * @param action The action ("login" or "register").
     * @param maxAttempts The maximum number of attempts.
     * @return Number of remaining attempts (0 if rate-limited).
     */
    suspend fun getRemainingAttempts(ip: String, action: String, maxAttempts: Int): Int {
        val key = "$RATE_LIMIT_PREFIX$ip:$action"
        val async = redisConnection.async()

        return try {
            val countStr = async.get(key).await()
            val count = countStr?.toLongOrNull() ?: 0L
            maxOf(0, (maxAttempts - count).toInt())
        } catch (e: Exception) {
            logger.error("Failed to get remaining attempts for IP {} on action {}: {}", ip, action, e.message)
            maxAttempts
        }
    }
}
