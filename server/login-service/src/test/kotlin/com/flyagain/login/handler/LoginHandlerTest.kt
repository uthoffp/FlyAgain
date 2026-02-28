package com.flyagain.login.handler

import com.flyagain.common.CharacterClassMapping
import com.flyagain.common.grpc.*
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.LoginRequest
import com.flyagain.common.proto.LoginResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.login.auth.JwtManager
import com.flyagain.login.auth.PasswordHasher
import com.flyagain.login.auth.SessionSecretGenerator
import com.flyagain.login.ratelimit.RateLimiter
import com.flyagain.login.session.SessionManager
import io.mockk.*
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginHandlerTest {

    private val accountStub = mockk<AccountDataServiceGrpcKt.AccountDataServiceCoroutineStub>()
    private val characterStub = mockk<CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub>()
    private val passwordHasher = mockk<PasswordHasher>()
    private val jwtManager = mockk<JwtManager>()
    private val sessionManager = mockk<SessionManager>()
    private val rateLimiter = mockk<RateLimiter>()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private val handler = LoginHandler(
        accountService = accountStub,
        characterService = characterStub,
        passwordHasher = passwordHasher,
        jwtManager = jwtManager,
        sessionManager = sessionManager,
        rateLimiter = rateLimiter,
        accountServiceHost = "127.0.0.1",
        accountServicePort = 7779
    )

    private fun setupSuccessfulLogin() {
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        every { channel.remoteAddress() } returns InetSocketAddress("127.0.0.1", 12345)
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
        coEvery { rateLimiter.checkLoginRateLimit(any()) } returns true

        val account = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-123")
            .setUsername("testuser")
            .setPasswordHash("hashed")
            .setIsBanned(false)
            .build()
        coEvery { accountStub.getAccountByUsername(any(), any()) } returns account
        every { passwordHasher.verify("testpass", "hashed") } returns true
        coEvery { sessionManager.getExistingSession(any()) } returns null
        coEvery { sessionManager.createSession(any(), any(), any(), any(), any()) } returns true
        every { jwtManager.createToken(any(), any(), any()) } returns "test-jwt"
        coEvery { accountStub.updateLastLogin(any(), any()) } returns mockk()
    }

    private fun captureLoginResponse(): LoginResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return LoginResponse.parseFrom(slot.captured.payload)
    }

    @Test
    fun `login response maps all character classes correctly`() = runTest {
        setupSuccessfulLogin()

        // Build a character list with all 4 classes (IDs 0-3)
        val charList = CharacterList.newBuilder()
        for ((id, expectedName) in CharacterClassMapping.ID_TO_NAME) {
            charList.addCharacters(
                CharacterRecord.newBuilder()
                    .setId("char-$id")
                    .setName("Hero$id")
                    .setCharacterClass(id)
                    .setLevel(id + 1)
                    .setFound(true)
                    .build()
            )
        }
        coEvery { characterStub.getCharactersByAccount(any(), any()) } returns charList.build()

        val request = LoginRequest.newBuilder()
            .setUsername("testuser")
            .setPassword("testpass")
            .build()

        handler.handle(ctx, request)

        val response = captureLoginResponse()
        assertTrue(response.success, "Login should succeed")
        assertEquals(4, response.charactersList.size, "Should contain 4 characters")

        for (charInfo in response.charactersList) {
            val classId = charInfo.id.removePrefix("char-").toInt()
            val expectedName = CharacterClassMapping.nameForId(classId)
            assertEquals(expectedName, charInfo.characterClass,
                "Class ID $classId should map to '$expectedName', got '${charInfo.characterClass}'")
            assertEquals(classId + 1, charInfo.level,
                "Level should be preserved for class $classId")
        }
    }

    @Test
    fun `login response maps Warrior (class 0) correctly`() = runTest {
        setupSuccessfulLogin()

        val charList = CharacterList.newBuilder()
            .addCharacters(
                CharacterRecord.newBuilder()
                    .setId("char-warrior")
                    .setName("WarriorHero")
                    .setCharacterClass(0)
                    .setLevel(10)
                    .setFound(true)
                    .build()
            )
            .build()
        coEvery { characterStub.getCharactersByAccount(any(), any()) } returns charList

        handler.handle(ctx, LoginRequest.newBuilder()
            .setUsername("testuser").setPassword("testpass").build())

        val response = captureLoginResponse()
        assertEquals(1, response.charactersList.size)
        assertEquals("Warrior", response.charactersList[0].characterClass)
        assertEquals(10, response.charactersList[0].level)
    }
}
