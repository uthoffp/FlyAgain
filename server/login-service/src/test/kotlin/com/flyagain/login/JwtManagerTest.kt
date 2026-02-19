package com.flyagain.login

import com.flyagain.login.auth.JwtManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtManagerTest {

    private val jwtManager = JwtManager(secret = "test-secret-key-for-unit-tests", expiryHours = 24)

    @Test
    fun `createToken produces a non-empty JWT string`() {
        val token = jwtManager.createToken(1L, "testuser", "session-abc")
        assertNotNull(token)
        // JWT has 3 dot-separated parts
        assertEquals(3, token.split(".").size, "JWT should have 3 parts separated by dots")
    }

    @Test
    fun `validateToken returns decoded JWT for valid token`() {
        val token = jwtManager.createToken(42L, "player1", "sess-xyz")
        val decoded = jwtManager.validateToken(token)
        assertNotNull(decoded)
    }

    @Test
    fun `validateToken returns null for invalid token`() {
        val result = jwtManager.validateToken("not.a.valid.jwt")
        assertNull(result)
    }

    @Test
    fun `validateToken returns null for token signed with different secret`() {
        val otherManager = JwtManager(secret = "different-secret", expiryHours = 24)
        val token = otherManager.createToken(1L, "user", "sess")
        // Validating with the original manager (different secret) should fail
        val result = jwtManager.validateToken(token)
        assertNull(result)
    }

    @Test
    fun `getAccountId extracts correct account ID from JWT`() {
        val token = jwtManager.createToken(99L, "admin", "sess-123")
        val decoded = jwtManager.validateToken(token)!!
        assertEquals(99L, jwtManager.getAccountId(decoded))
    }

    @Test
    fun `getSessionId extracts correct session ID from JWT`() {
        val token = jwtManager.createToken(1L, "user", "my-session-id")
        val decoded = jwtManager.validateToken(token)!!
        assertEquals("my-session-id", jwtManager.getSessionId(decoded))
    }

    @Test
    fun `JWT contains correct subject claim`() {
        val token = jwtManager.createToken(123L, "testplayer", "sess")
        val decoded = jwtManager.validateToken(token)!!
        assertEquals("123", decoded.subject)
    }

    @Test
    fun `JWT contains correct username claim`() {
        val token = jwtManager.createToken(1L, "krieger-main", "sess")
        val decoded = jwtManager.validateToken(token)!!
        assertEquals("krieger-main", decoded.getClaim("username").asString())
    }

    @Test
    fun `expired token is rejected`() {
        val shortLivedManager = JwtManager(secret = "test-secret-key-for-unit-tests", expiryHours = 0)
        val token = shortLivedManager.createToken(1L, "user", "sess")
        // Token with 0 hours expiry should be immediately expired
        val result = shortLivedManager.validateToken(token)
        assertNull(result, "Expired token should be rejected")
    }
}
