package com.flyagain.login.session

import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory

/**
 * Manages player sessions in Redis.
 *
 * Session key format: session:{sessionId}
 *   - Stored as a Redis hash with fields: accountId, characterId, ip, loginTime, hmacSecret
 *
 * Reverse lookup: session:account:{accountId} -> sessionId
 *   - Used to detect multi-login and invalidate previous sessions
 *
 * Sessions expire after [sessionTtlSeconds] (default: 24 hours).
 */
class SessionManager(
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val sessionTtlSeconds: Long = 86400L // 24 hours
) {

    private val logger = LoggerFactory.getLogger(SessionManager::class.java)

    companion object {
        private const val SESSION_PREFIX = "session:"
        private const val ACCOUNT_SESSION_PREFIX = "session:account:"
    }

    /**
     * Create a new session in Redis.
     * If the account already has an active session, the old session is removed first.
     *
     * @param sessionId The unique session identifier.
     * @param accountId The account's database ID.
     * @param ip The client's IP address.
     * @param hmacSecret The HMAC secret for this session.
     * @return true if the session was created successfully.
     */
    suspend fun createSession(
        sessionId: String,
        accountId: Long,
        ip: String,
        hmacSecret: String
    ): Boolean {
        val async = redisConnection.async()
        val sessionKey = "$SESSION_PREFIX$sessionId"
        val accountKey = "$ACCOUNT_SESSION_PREFIX$accountId"

        try {
            // Check for existing session (multi-login prevention)
            val existingSessionId = async.get(accountKey).await()
            if (existingSessionId != null) {
                logger.info("Account {} already has session {}, removing old session", accountId, existingSessionId)
                deleteSession(existingSessionId)
            }

            // Create session hash
            val fields = mapOf(
                "accountId" to accountId.toString(),
                "characterId" to "0",
                "ip" to ip,
                "loginTime" to System.currentTimeMillis().toString(),
                "hmacSecret" to hmacSecret
            )
            async.hset(sessionKey, fields).await()
            async.expire(sessionKey, sessionTtlSeconds).await()

            // Create reverse lookup
            async.set(accountKey, sessionId).await()
            async.expire(accountKey, sessionTtlSeconds).await()

            logger.debug("Session {} created for account {} from IP {}", sessionId, accountId, ip)
            return true
        } catch (e: Exception) {
            logger.error("Failed to create session {} for account {}: {}", sessionId, accountId, e.message)
            return false
        }
    }

    /**
     * Check if an account already has an active session (multi-login detection).
     * @param accountId The account ID to check.
     * @return The existing session ID, or null if no session exists.
     */
    suspend fun getExistingSession(accountId: Long): String? {
        val async = redisConnection.async()
        val accountKey = "$ACCOUNT_SESSION_PREFIX$accountId"
        return try {
            val sessionId = async.get(accountKey).await()
            if (sessionId != null) {
                // Verify the session still exists
                val sessionKey = "$SESSION_PREFIX$sessionId"
                val exists = async.exists(sessionKey).await()
                if (exists > 0) sessionId else {
                    // Stale reverse lookup, clean up
                    async.del(accountKey).await()
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to check existing session for account {}: {}", accountId, e.message)
            null
        }
    }

    /**
     * Get session data from Redis.
     * @param sessionId The session ID to look up.
     * @return A map of session fields, or null if the session doesn't exist.
     */
    suspend fun getSession(sessionId: String): Map<String, String>? {
        val async = redisConnection.async()
        val sessionKey = "$SESSION_PREFIX$sessionId"
        return try {
            val fields = async.hgetall(sessionKey).await()
            if (fields.isNullOrEmpty()) null else fields
        } catch (e: Exception) {
            logger.error("Failed to get session {}: {}", sessionId, e.message)
            null
        }
    }

    /**
     * Update the characterId field of an existing session.
     * @param sessionId The session ID to update.
     * @param characterId The selected character ID.
     */
    suspend fun updateCharacterId(sessionId: String, characterId: Long) {
        val async = redisConnection.async()
        val sessionKey = "$SESSION_PREFIX$sessionId"
        try {
            async.hset(sessionKey, "characterId", characterId.toString()).await()
            logger.debug("Session {} updated with characterId {}", sessionId, characterId)
        } catch (e: Exception) {
            logger.error("Failed to update characterId on session {}: {}", sessionId, e.message)
        }
    }

    /**
     * Delete a session from Redis, including the reverse lookup.
     * @param sessionId The session ID to delete.
     */
    suspend fun deleteSession(sessionId: String) {
        val async = redisConnection.async()
        val sessionKey = "$SESSION_PREFIX$sessionId"
        try {
            // Get the accountId before deleting so we can clean up the reverse lookup
            val accountId = async.hget(sessionKey, "accountId").await()
            async.del(sessionKey).await()

            if (accountId != null) {
                val accountKey = "$ACCOUNT_SESSION_PREFIX$accountId"
                // Only delete reverse lookup if it still points to this session
                val currentSessionId = async.get(accountKey).await()
                if (currentSessionId == sessionId) {
                    async.del(accountKey).await()
                }
            }

            logger.debug("Session {} deleted", sessionId)
        } catch (e: Exception) {
            logger.error("Failed to delete session {}: {}", sessionId, e.message)
        }
    }
}
