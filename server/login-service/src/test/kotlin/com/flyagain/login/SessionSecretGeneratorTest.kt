package com.flyagain.login

import com.flyagain.login.auth.SessionSecretGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionSecretGeneratorTest {

    @Test
    fun `generateSessionId returns base64 encoded string`() {
        val sessionId = SessionSecretGenerator.generateSessionId()
        assertNotNull(sessionId)
        assertTrue(sessionId.isNotEmpty())
        // Should be decodable as Base64 URL-safe
        val decoded = Base64.getUrlDecoder().decode(sessionId)
        assertEquals(8, decoded.size, "Session ID should decode to 8 bytes")
    }

    @Test
    fun `generateHmacSecret returns base64 encoded string`() {
        val hmacSecret = SessionSecretGenerator.generateHmacSecret()
        assertNotNull(hmacSecret)
        assertTrue(hmacSecret.isNotEmpty())
        val decoded = Base64.getUrlDecoder().decode(hmacSecret)
        assertEquals(32, decoded.size, "HMAC secret should decode to 32 bytes")
    }

    @Test
    fun `generate returns both sessionId and hmacSecret`() {
        val credentials = SessionSecretGenerator.generate()
        assertNotNull(credentials.sessionId)
        assertNotNull(credentials.hmacSecret)
        assertTrue(credentials.sessionId.isNotEmpty())
        assertTrue(credentials.hmacSecret.isNotEmpty())
    }

    @Test
    fun `generated session IDs are unique`() {
        val ids = (1..100).map { SessionSecretGenerator.generateSessionId() }.toSet()
        assertEquals(100, ids.size, "100 generated session IDs should all be unique")
    }

    @Test
    fun `generated HMAC secrets are unique`() {
        val secrets = (1..100).map { SessionSecretGenerator.generateHmacSecret() }.toSet()
        assertEquals(100, secrets.size, "100 generated HMAC secrets should all be unique")
    }

    @Test
    fun `generate produces different credentials each time`() {
        val cred1 = SessionSecretGenerator.generate()
        val cred2 = SessionSecretGenerator.generate()
        assertNotEquals(cred1.sessionId, cred2.sessionId)
        assertNotEquals(cred1.hmacSecret, cred2.hmacSecret)
    }
}
