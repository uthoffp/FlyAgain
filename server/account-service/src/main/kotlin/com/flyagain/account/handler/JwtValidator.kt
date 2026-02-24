package com.flyagain.account.handler

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import org.slf4j.LoggerFactory

/**
 * Validates JWT tokens issued by the login-service.
 *
 * The JWT is signed with HMAC-SHA256 using a shared secret between
 * login-service and account-service (configured via `flyagain.auth.jwt-secret`).
 *
 * Expected JWT claims:
 * - `sub` (subject): The account ID as a string
 * - `sid`: The session ID
 * - `iss` (issuer): "flyagain-login"
 */
class JwtValidator(jwtSecret: String) {

    private val logger = LoggerFactory.getLogger(JwtValidator::class.java)

    private val algorithm: Algorithm = Algorithm.HMAC256(jwtSecret)

    private val verifier = JWT.require(algorithm)
        .withIssuer("flyagain-login")
        .build()

    /**
     * Validates the given JWT token string.
     *
     * @param token The raw JWT string from the client.
     * @return [JwtClaims] if validation succeeds, or null if the token is invalid/expired.
     */
    fun validate(token: String): JwtClaims? {
        return try {
            val decoded: DecodedJWT = verifier.verify(token)

            val accountId = decoded.subject
                ?: run {
                    logger.warn("JWT missing 'sub' claim")
                    return null
                }

            if (accountId.isBlank()) {
                logger.warn("JWT 'sub' claim is empty")
                return null
            }

            val sessionId = decoded.getClaim("sid").asString()
                ?: run {
                    logger.warn("JWT missing 'sid' claim")
                    return null
                }

            JwtClaims(accountId = accountId, sessionId = sessionId)
        } catch (e: JWTVerificationException) {
            logger.warn("JWT verification failed: {}", e.message)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error during JWT validation", e)
            null
        }
    }
}

/**
 * Holds the validated claims extracted from a JWT.
 *
 * @param accountId The account ID (from the `sub` claim).
 * @param sessionId The session ID (from the `sid` claim).
 */
data class JwtClaims(
    val accountId: String,
    val sessionId: String
)
