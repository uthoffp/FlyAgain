package com.flyagain.login.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import org.slf4j.LoggerFactory

/**
 * Handles bcrypt password hashing and verification.
 * Uses a configurable cost factor (default 12 per ARCHITECTURE.md spec).
 */
class PasswordHasher(private val cost: Int) {

    private val logger = LoggerFactory.getLogger(PasswordHasher::class.java)

    init {
        require(cost in 4..31) { "bcrypt cost must be between 4 and 31, got $cost" }
        logger.info("PasswordHasher initialized with bcrypt cost factor {}", cost)
    }

    /**
     * Hash a plaintext password using bcrypt.
     * @param password The plaintext password to hash (max 72 bytes for bcrypt).
     * @return The bcrypt hash string.
     */
    fun hash(password: String): String {
        return BCrypt.withDefaults().hashToString(cost, password.toCharArray())
    }

    /**
     * Verify a plaintext password against a bcrypt hash.
     * @param password The plaintext password to verify.
     * @param hash The stored bcrypt hash.
     * @return true if the password matches the hash.
     */
    fun verify(password: String, hash: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hash)
        return result.verified
    }
}
