package com.flyagain.login.auth

import java.security.SecureRandom
import java.util.Base64

/**
 * Generates cryptographically secure session tokens and HMAC secrets.
 * - Session token: 8 bytes, Base64-encoded (used as session ID)
 * - HMAC secret: 32 bytes, Base64-encoded (used for UDP packet signing)
 */
object SessionSecretGenerator {

    private val secureRandom = SecureRandom()

    /**
     * Represents a generated session with both a session ID and an HMAC secret.
     */
    data class SessionCredentials(
        val sessionId: String,
        val sessionToken: Long,
        val hmacSecret: String
    )

    /**
     * Generate a new session token (8 bytes, Base64 URL-safe encoded).
     * @return A Base64 URL-safe encoded session token string.
     */
    fun generateSessionId(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Generate a new HMAC secret (32 bytes, Base64 URL-safe encoded).
     * Used for HMAC-SHA256 signing of UDP packets.
     * @return A Base64 URL-safe encoded HMAC secret string.
     */
    fun generateHmacSecret(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Generate both a session ID and HMAC secret as a pair.
     * @return A SessionCredentials containing both values.
     */
    /**
     * Generate a random 64-bit session token for UDP packet identification.
     * @return A non-zero random Long.
     */
    fun generateSessionToken(): Long {
        var token: Long
        do {
            token = secureRandom.nextLong()
        } while (token == 0L)
        return token
    }

    fun generate(): SessionCredentials {
        return SessionCredentials(
            sessionId = generateSessionId(),
            sessionToken = generateSessionToken(),
            hmacSecret = generateHmacSecret()
        )
    }
}
