package com.flyagain.login

import com.flyagain.login.auth.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class PasswordHasherTest {

    // Use cost 4 (minimum) for fast test execution
    private val hasher = PasswordHasher(cost = 4)

    @Test
    fun `hash produces a bcrypt hash string`() {
        val hash = hasher.hash("password123")
        assertTrue(hash.startsWith("\$2a\$") || hash.startsWith("\$2b\$") || hash.startsWith("\$2y\$"),
            "Hash should be a bcrypt string, got: $hash")
    }

    @Test
    fun `verify returns true for correct password`() {
        val hash = hasher.hash("mySecurePassword")
        assertTrue(hasher.verify("mySecurePassword", hash))
    }

    @Test
    fun `verify returns false for wrong password`() {
        val hash = hasher.hash("correctPassword")
        assertFalse(hasher.verify("wrongPassword", hash))
    }

    @Test
    fun `different passwords produce different hashes`() {
        val hash1 = hasher.hash("password1")
        val hash2 = hasher.hash("password2")
        assertTrue(hash1 != hash2, "Different passwords should produce different hashes")
    }

    @Test
    fun `same password produces different hashes due to salt`() {
        val hash1 = hasher.hash("samePassword")
        val hash2 = hasher.hash("samePassword")
        assertTrue(hash1 != hash2, "Same password should produce different hashes due to random salt")
        // But both should verify
        assertTrue(hasher.verify("samePassword", hash1))
        assertTrue(hasher.verify("samePassword", hash2))
    }

    @Test
    fun `constructor rejects cost below 4`() {
        assertFailsWith<IllegalArgumentException> {
            PasswordHasher(cost = 3)
        }
    }

    @Test
    fun `constructor rejects cost above 31`() {
        assertFailsWith<IllegalArgumentException> {
            PasswordHasher(cost = 32)
        }
    }

    @Test
    fun `empty password can be hashed and verified`() {
        val hash = hasher.hash("")
        assertTrue(hasher.verify("", hash))
        assertFalse(hasher.verify("notempty", hash))
    }
}
