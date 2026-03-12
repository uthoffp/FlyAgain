package com.flyagain.login.handler

import com.flyagain.common.grpc.AccountDataServiceGrpcKt
import com.flyagain.common.grpc.CreateAccountResponse
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.RegisterRequest
import com.flyagain.common.proto.RegisterResponse
import com.flyagain.login.auth.PasswordHasher
import com.flyagain.login.ratelimit.RateLimiter
import io.mockk.*
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegisterHandlerTest {

    private val accountStub = mockk<AccountDataServiceGrpcKt.AccountDataServiceCoroutineStub>()
    private val passwordHasher = mockk<PasswordHasher>()
    private val rateLimiter = mockk<RateLimiter>()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private val handler = RegisterHandler(accountStub, passwordHasher, rateLimiter)

    init {
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        every { channel.remoteAddress() } returns InetSocketAddress("127.0.0.1", 12345)
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
    }

    private fun captureResponse(): RegisterResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return RegisterResponse.parseFrom(slot.captured.payload)
    }

    private fun makeRequest(
        username: String = "validuser",
        email: String = "valid@test.com",
        password: String = "securepass"
    ): RegisterRequest {
        return RegisterRequest.newBuilder()
            .setUsername(username)
            .setEmail(email)
            .setPassword(password)
            .build()
    }

    private fun setupAllowRate() {
        coEvery { rateLimiter.checkRegisterRateLimit(any()) } returns true
    }

    private fun setupSuccessfulCreate() {
        setupAllowRate()
        every { passwordHasher.hash(any()) } returns "bcrypt-hash"
        coEvery { accountStub.createAccount(any(), any()) } returns
            CreateAccountResponse.newBuilder().setSuccess(true).setAccountId("acc-1").build()
    }

    @Test
    fun `rejects when rate limited`() = runTest {
        coEvery { rateLimiter.checkRegisterRateLimit(any()) } returns false

        handler.handle(ctx, makeRequest())

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("Too many"))
    }

    @Test
    fun `rejects blank username`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(username = ""))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects username shorter than 3 chars`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(username = "ab"))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects username longer than 16 chars`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(username = "a".repeat(17)))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects username with special characters`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(username = "user name!"))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `accepts username with hyphens`() = runTest {
        setupSuccessfulCreate()
        handler.handle(ctx, makeRequest(username = "my-user"))
        assertTrue(captureResponse().success)
    }

    @Test
    fun `rejects blank email`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(email = ""))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects invalid email format`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(email = "not-an-email"))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects email longer than 254 chars`() = runTest {
        setupAllowRate()
        val longEmail = "a".repeat(250) + "@b.com"
        handler.handle(ctx, makeRequest(email = longEmail))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects password shorter than 8 chars`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(password = "short"))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects password longer than 72 chars (bcrypt max)`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(password = "a".repeat(73)))
        assertFalse(captureResponse().success)
    }

    @Test
    fun `returns service unavailable on hash failure`() = runTest {
        setupAllowRate()
        every { passwordHasher.hash(any()) } throws RuntimeException("hash error")

        handler.handle(ctx, makeRequest())

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("unavailable"))
    }

    @Test
    fun `returns service unavailable on gRPC failure`() = runTest {
        setupAllowRate()
        every { passwordHasher.hash(any()) } returns "hash"
        coEvery { accountStub.createAccount(any(), any()) } throws RuntimeException("connection refused")

        handler.handle(ctx, makeRequest())

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("unavailable"))
    }

    @Test
    fun `returns gRPC error message on create failure`() = runTest {
        setupAllowRate()
        every { passwordHasher.hash(any()) } returns "hash"
        coEvery { accountStub.createAccount(any(), any()) } returns
            CreateAccountResponse.newBuilder().setSuccess(false).setErrorMessage("Username taken").build()

        handler.handle(ctx, makeRequest())

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals("Username taken", response.errorMessage)
    }

    @Test
    fun `returns success on valid registration`() = runTest {
        setupSuccessfulCreate()

        handler.handle(ctx, makeRequest())

        assertTrue(captureResponse().success)
    }
}
