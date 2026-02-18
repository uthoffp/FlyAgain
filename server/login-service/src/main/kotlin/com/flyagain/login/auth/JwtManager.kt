package com.flyagain.login.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import org.slf4j.LoggerFactory
import java.util.Date

/**
 * Creates and validates JWTs for client authentication.
 * Tokens contain the account ID and username as claims.
 */
class JwtManager(
    private val secret: String,
    private val expiryHours: Long
) {

    private val logger = LoggerFactory.getLogger(JwtManager::class.java)
    private val algorithm = Algorithm.HMAC256(secret)
    private val issuer = "flyagain-login-service"

    private val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    init {
        logger.info("JwtManager initialized with expiry of {} hours", expiryHours)
    }

    /**
     * Create a signed JWT for the given account.
     * @param accountId The account's database ID.
     * @param username The account's username.
     * @param sessionId The session ID bound to this token.
     * @return A signed JWT string.
     */
    fun createToken(accountId: Long, username: String, sessionId: String): String {
        val now = Date()
        val expiry = Date(now.time + expiryHours * 3600 * 1000)

        return JWT.create()
            .withIssuer(issuer)
            .withSubject(accountId.toString())
            .withClaim("username", username)
            .withClaim("sessionId", sessionId)
            .withIssuedAt(now)
            .withExpiresAt(expiry)
            .sign(algorithm)
    }

    /**
     * Validate and decode a JWT.
     * @param token The JWT string to validate.
     * @return The decoded JWT if valid, or null if invalid/expired.
     */
    fun validateToken(token: String): DecodedJWT? {
        return try {
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            logger.warn("JWT verification failed: {}", e.message)
            null
        }
    }

    /**
     * Extract the account ID from a validated JWT.
     * @param jwt The decoded JWT.
     * @return The account ID from the subject claim.
     */
    fun getAccountId(jwt: DecodedJWT): Long {
        return jwt.subject.toLong()
    }

    /**
     * Extract the session ID from a validated JWT.
     * @param jwt The decoded JWT.
     * @return The session ID from the claims.
     */
    fun getSessionId(jwt: DecodedJWT): String {
        return jwt.getClaim("sessionId").asString()
    }
}
