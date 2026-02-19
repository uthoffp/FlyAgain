package com.flyagain.login.handler

import com.flyagain.common.grpc.AccountDataServiceGrpcKt
import com.flyagain.common.grpc.CreateAccountRequest
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.RegisterRequest
import com.flyagain.common.proto.RegisterResponse
import com.flyagain.login.auth.PasswordHasher
import com.flyagain.common.network.Packet
import com.flyagain.login.ratelimit.RateLimiter
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * Handles REGISTER_REQUEST packets.
 *
 * Flow:
 * 1. Rate limit check (3 registrations per hour per IP)
 * 2. Input validation:
 *    - Username: 3-16 characters, alphanumeric + hyphens only [a-zA-Z0-9-]
 *    - Email: basic format validation
 *    - Password: 8-72 characters (72 is bcrypt max)
 * 3. Hash password with bcrypt
 * 4. Call gRPC CreateAccount on database-service
 * 5. Return RegisterResponse
 */
class RegisterHandler(
    private val accountService: AccountDataServiceGrpcKt.AccountDataServiceCoroutineStub,
    private val passwordHasher: PasswordHasher,
    private val rateLimiter: RateLimiter
) {

    private val logger = LoggerFactory.getLogger(RegisterHandler::class.java)

    companion object {
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9-]{3,16}$")
        private val EMAIL_REGEX = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 72 // bcrypt maximum
    }

    /**
     * Process a registration request.
     * @param ctx The Netty channel context.
     * @param request The parsed RegisterRequest protobuf.
     */
    suspend fun handle(ctx: ChannelHandlerContext, request: RegisterRequest) {
        val ip = (ctx.channel().remoteAddress() as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
        val username = request.username
        val email = request.email

        logger.info("Registration attempt for user '{}' from IP {}", username, ip)

        // Step 1: Rate limit check
        if (!rateLimiter.checkRegisterRateLimit(ip)) {
            logger.warn("Registration rate limit exceeded for IP {}", ip)
            sendFailure(ctx, "Too many registration attempts. Please try again later.")
            return
        }

        // Step 2: Input validation
        val validationError = validateInput(username, email, request.password)
        if (validationError != null) {
            logger.info("Registration validation failed for user '{}': {}", username, validationError)
            sendFailure(ctx, validationError)
            return
        }

        // Step 3: Hash the password with bcrypt
        val passwordHash = try {
            passwordHasher.hash(request.password)
        } catch (e: Exception) {
            logger.error("Failed to hash password for registration '{}': {}", username, e.message)
            sendFailure(ctx, "Registration service temporarily unavailable. Please try again.")
            return
        }

        // Step 4: Call gRPC CreateAccount
        val createResponse = try {
            val grpcRequest = CreateAccountRequest.newBuilder()
                .setUsername(username)
                .setEmail(email)
                .setPasswordHash(passwordHash)
                .build()
            accountService.createAccount(grpcRequest)
        } catch (e: Exception) {
            logger.error("gRPC error creating account '{}': {}", username, e.message)
            sendFailure(ctx, "Registration service temporarily unavailable. Please try again.")
            return
        }

        // Step 5: Build and send response
        if (createResponse.success) {
            val response = RegisterResponse.newBuilder()
                .setSuccess(true)
                .build()
            ctx.writeAndFlush(Packet(Opcode.REGISTER_RESPONSE_VALUE, response.toByteArray()))
            logger.info("Registration successful for user '{}' (accountId={})", username, createResponse.accountId)
        } else {
            val errorMsg = createResponse.errorMessage.ifEmpty { "Registration failed." }
            logger.info("Registration failed for user '{}': {}", username, errorMsg)
            sendFailure(ctx, errorMsg)
        }
    }

    /**
     * Validate registration input fields.
     * @return An error message string if validation fails, or null if all inputs are valid.
     */
    private fun validateInput(username: String, email: String, password: String): String? {
        // Username validation
        if (username.isBlank()) {
            return "Username is required."
        }
        if (!USERNAME_REGEX.matches(username)) {
            return "Username must be 3-16 characters and contain only letters, numbers, and hyphens."
        }

        // Email validation
        if (email.isBlank()) {
            return "Email is required."
        }
        if (!EMAIL_REGEX.matches(email)) {
            return "Please provide a valid email address."
        }
        if (email.length > 254) {
            return "Email address is too long."
        }

        // Password validation
        if (password.length < MIN_PASSWORD_LENGTH) {
            return "Password must be at least $MIN_PASSWORD_LENGTH characters."
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            return "Password must not exceed $MAX_PASSWORD_LENGTH characters."
        }

        return null
    }

    private fun sendFailure(ctx: ChannelHandlerContext, errorMessage: String) {
        val response = RegisterResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(errorMessage)
            .build()
        ctx.writeAndFlush(Packet(Opcode.REGISTER_RESPONSE_VALUE, response.toByteArray()))
    }
}
