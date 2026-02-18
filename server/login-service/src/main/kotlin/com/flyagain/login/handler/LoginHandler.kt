package com.flyagain.login.handler

import com.flyagain.common.grpc.AccountDataServiceGrpcKt
import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.GetAccountRequest
import com.flyagain.common.grpc.GetCharactersByAccountRequest
import com.flyagain.common.grpc.UpdateLastLoginRequest
import com.flyagain.common.proto.CharacterInfo
import com.flyagain.common.proto.LoginRequest
import com.flyagain.common.proto.LoginResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.login.auth.JwtManager
import com.flyagain.login.auth.PasswordHasher
import com.flyagain.login.auth.SessionSecretGenerator
import com.flyagain.login.network.Packet
import com.flyagain.login.ratelimit.RateLimiter
import com.flyagain.login.session.SessionManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * Handles LOGIN_REQUEST packets.
 *
 * Flow:
 * 1. Rate limit check
 * 2. Look up account via gRPC (GetAccountByUsername)
 * 3. Verify bcrypt password hash
 * 4. Check ban status
 * 5. Check for existing session (multi-login prevention) -- existing session is invalidated
 * 6. Generate session credentials (sessionId + HMAC secret)
 * 7. Create session in Redis
 * 8. Generate JWT
 * 9. Fetch character list via gRPC
 * 10. Update last login timestamp via gRPC
 * 11. Return LoginResponse with JWT, characters, HMAC secret, and account-service endpoint
 */
class LoginHandler(
    private val accountService: AccountDataServiceGrpcKt.AccountDataServiceCoroutineStub,
    private val characterService: CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub,
    private val passwordHasher: PasswordHasher,
    private val jwtManager: JwtManager,
    private val sessionManager: SessionManager,
    private val rateLimiter: RateLimiter,
    private val accountServiceHost: String,
    private val accountServicePort: Int
) {

    private val logger = LoggerFactory.getLogger(LoginHandler::class.java)

    /**
     * Character class ID to name mapping (German-themed names from GDD).
     */
    companion object {
        private val CLASS_NAMES = mapOf(
            1 to "Krieger",
            2 to "Magier",
            3 to "Assassine",
            4 to "Kleriker"
        )
    }

    /**
     * Process a login request.
     * @param ctx The Netty channel context.
     * @param request The parsed LoginRequest protobuf.
     */
    suspend fun handle(ctx: ChannelHandlerContext, request: LoginRequest) {
        val ip = (ctx.channel().remoteAddress() as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
        val username = request.username

        logger.info("Login attempt for user '{}' from IP {}", username, ip)

        // Step 1: Rate limit check
        if (!rateLimiter.checkLoginRateLimit(ip)) {
            logger.warn("Login rate limit exceeded for IP {}", ip)
            sendFailure(ctx, "Too many login attempts. Please try again later.")
            return
        }

        // Step 2: Look up account by username via gRPC
        val account = try {
            val grpcRequest = GetAccountRequest.newBuilder()
                .setUsername(username)
                .build()
            accountService.getAccountByUsername(grpcRequest)
        } catch (e: Exception) {
            logger.error("gRPC error looking up account '{}': {}", username, e.message)
            sendFailure(ctx, "Login service temporarily unavailable. Please try again.")
            return
        }

        if (!account.found) {
            logger.info("Account not found for username '{}'", username)
            // Use generic message to prevent username enumeration
            sendFailure(ctx, "Invalid username or password.")
            return
        }

        // Step 3: Verify password with bcrypt
        if (!passwordHasher.verify(request.password, account.passwordHash)) {
            logger.info("Invalid password for user '{}'", username)
            sendFailure(ctx, "Invalid username or password.")
            return
        }

        // Step 4: Check ban status
        if (account.isBanned) {
            val banReason = account.banReason.ifEmpty { "No reason provided" }
            logger.info("Banned account '{}' attempted login. Reason: {}", username, banReason)
            sendFailure(ctx, "Your account has been banned. Reason: $banReason")
            return
        }

        val accountId = account.id

        // Step 5: Multi-login check -- invalidate any existing session
        val existingSession = sessionManager.getExistingSession(accountId)
        if (existingSession != null) {
            logger.info("Account {} already has session {}, will be invalidated", accountId, existingSession)
            // The SessionManager.createSession will handle removing the old session
        }

        // Step 6: Generate session credentials
        val credentials = SessionSecretGenerator.generate()

        // Step 7: Create session in Redis
        val sessionCreated = sessionManager.createSession(
            sessionId = credentials.sessionId,
            accountId = accountId,
            ip = ip,
            hmacSecret = credentials.hmacSecret
        )

        if (!sessionCreated) {
            logger.error("Failed to create session for account {}", accountId)
            sendFailure(ctx, "Login service temporarily unavailable. Please try again.")
            return
        }

        // Step 8: Generate JWT
        val jwt = jwtManager.createToken(accountId, username, credentials.sessionId)

        // Step 9: Fetch character list
        val characters = try {
            val charRequest = GetCharactersByAccountRequest.newBuilder()
                .setAccountId(accountId)
                .build()
            val charList = characterService.getCharactersByAccount(charRequest)
            charList.charactersList.map { charRecord ->
                CharacterInfo.newBuilder()
                    .setId(charRecord.id)
                    .setName(charRecord.name)
                    .setCharacterClass(CLASS_NAMES.getOrDefault(charRecord.characterClass, "Unknown"))
                    .setLevel(charRecord.level)
                    .build()
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch characters for account {}: {}", accountId, e.message)
            // Return empty list rather than failing the login
            emptyList()
        }

        // Step 10: Update last login timestamp (fire-and-forget)
        try {
            val updateRequest = UpdateLastLoginRequest.newBuilder()
                .setAccountId(accountId)
                .build()
            accountService.updateLastLogin(updateRequest)
        } catch (e: Exception) {
            logger.warn("Failed to update last login for account {}: {}", accountId, e.message)
            // Non-critical, don't fail the login
        }

        // Step 11: Build and send success response
        val response = LoginResponse.newBuilder()
            .setSuccess(true)
            .setJwt(jwt)
            .addAllCharacters(characters)
            .setHmacSecret(credentials.hmacSecret)
            .setAccountServiceHost(accountServiceHost)
            .setAccountServicePort(accountServicePort)
            .build()

        ctx.writeAndFlush(Packet(Opcode.LOGIN_RESPONSE, response))
        logger.info("Login successful for user '{}' (accountId={}, sessionId={}, characters={})",
            username, accountId, credentials.sessionId, characters.size)
    }

    private fun sendFailure(ctx: ChannelHandlerContext, errorMessage: String) {
        val response = LoginResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(errorMessage)
            .build()
        ctx.writeAndFlush(Packet(Opcode.LOGIN_RESPONSE, response))
    }
}
