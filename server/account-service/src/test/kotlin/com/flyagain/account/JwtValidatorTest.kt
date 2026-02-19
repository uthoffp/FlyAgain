package com.flyagain.account

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.flyagain.account.handler.JwtValidator
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtValidatorTest {

    private val secret = "test-jwt-secret"
    private val validator = JwtValidator(secret)

    private fun createValidToken(accountId: Long = 1L, sessionId: String = "test-session"): String {
        val algorithm = Algorithm.HMAC256(secret)
        val now = Date()
        val expiry = Date(now.time + 3600_000) // 1 hour from now
        return JWT.create()
            .withIssuer("flyagain-login")
            .withSubject(accountId.toString())
            .withClaim("sid", sessionId)
            .withIssuedAt(now)
            .withExpiresAt(expiry)
            .sign(algorithm)
    }

    @Test
    fun `validate returns claims for valid token`() {
        val token = createValidToken(accountId = 42L, sessionId = "sess-abc")
        val claims = validator.validate(token)
        assertNotNull(claims)
        assertEquals(42L, claims.accountId)
        assertEquals("sess-abc", claims.sessionId)
    }

    @Test
    fun `validate returns null for token with wrong secret`() {
        val algorithm = Algorithm.HMAC256("wrong-secret")
        val token = JWT.create()
            .withIssuer("flyagain-login")
            .withSubject("1")
            .withClaim("sid", "sess")
            .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
            .sign(algorithm)
        assertNull(validator.validate(token))
    }

    @Test
    fun `validate returns null for token with wrong issuer`() {
        val algorithm = Algorithm.HMAC256(secret)
        val token = JWT.create()
            .withIssuer("wrong-issuer")
            .withSubject("1")
            .withClaim("sid", "sess")
            .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
            .sign(algorithm)
        assertNull(validator.validate(token))
    }

    @Test
    fun `validate returns null for expired token`() {
        val algorithm = Algorithm.HMAC256(secret)
        val token = JWT.create()
            .withIssuer("flyagain-login")
            .withSubject("1")
            .withClaim("sid", "sess")
            .withExpiresAt(Date(System.currentTimeMillis() - 1000)) // already expired
            .sign(algorithm)
        assertNull(validator.validate(token))
    }

    @Test
    fun `validate returns null for token missing sub claim`() {
        val algorithm = Algorithm.HMAC256(secret)
        val token = JWT.create()
            .withIssuer("flyagain-login")
            // no subject
            .withClaim("sid", "sess")
            .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
            .sign(algorithm)
        assertNull(validator.validate(token))
    }

    @Test
    fun `validate returns null for token missing sid claim`() {
        val algorithm = Algorithm.HMAC256(secret)
        val token = JWT.create()
            .withIssuer("flyagain-login")
            .withSubject("1")
            // no sid claim
            .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
            .sign(algorithm)
        assertNull(validator.validate(token))
    }

    @Test
    fun `validate returns null for garbage string`() {
        assertNull(validator.validate("not-a-jwt-at-all"))
    }

    @Test
    fun `validate returns null for token with non-numeric subject`() {
        val algorithm = Algorithm.HMAC256(secret)
        val token = JWT.create()
            .withIssuer("flyagain-login")
            .withSubject("not-a-number")
            .withClaim("sid", "sess")
            .withExpiresAt(Date(System.currentTimeMillis() + 3600_000))
            .sign(algorithm)
        assertNull(validator.validate(token))
    }
}
