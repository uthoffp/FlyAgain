package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.AccountRepository
import com.google.protobuf.Timestamp
import io.grpc.StatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountGrpcServiceTest {

    private val accountRepo = mockk<AccountRepository>()
    private val service = AccountGrpcService(accountRepo)

    @Test
    fun `getAccountByUsername returns account when found`() = runTest {
        val record = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-1")
            .setUsername("testuser")
            .setPasswordHash("hashed")
            .setIsBanned(false)
            .build()
        coEvery { accountRepo.getByUsername("testuser") } returns record

        val result = service.getAccountByUsername(
            GetAccountRequest.newBuilder().setUsername("testuser").build()
        )

        assertTrue(result.found)
        assertEquals("acc-1", result.id)
        assertEquals("testuser", result.username)
    }

    @Test
    fun `getAccountByUsername returns found=false when not found`() = runTest {
        coEvery { accountRepo.getByUsername("nobody") } returns null

        val result = service.getAccountByUsername(
            GetAccountRequest.newBuilder().setUsername("nobody").build()
        )

        assertFalse(result.found)
    }

    @Test
    fun `getAccountById returns account when found`() = runTest {
        val record = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-1")
            .setUsername("player1")
            .build()
        coEvery { accountRepo.getById("acc-1") } returns record

        val result = service.getAccountById(
            GetAccountByIdRequest.newBuilder().setAccountId("acc-1").build()
        )

        assertTrue(result.found)
        assertEquals("player1", result.username)
    }

    @Test
    fun `getAccountById returns found=false when not found`() = runTest {
        coEvery { accountRepo.getById("nonexistent") } returns null

        val result = service.getAccountById(
            GetAccountByIdRequest.newBuilder().setAccountId("nonexistent").build()
        )

        assertFalse(result.found)
    }

    @Test
    fun `createAccount returns success with account ID`() = runTest {
        coEvery { accountRepo.create("newuser", "new@test.com", "hashed") } returns "acc-new"

        val result = service.createAccount(
            CreateAccountRequest.newBuilder()
                .setUsername("newuser")
                .setEmail("new@test.com")
                .setPasswordHash("hashed")
                .build()
        )

        assertTrue(result.success)
        assertEquals("acc-new", result.accountId)
    }

    @Test
    fun `createAccount returns failure on duplicate username`() = runTest {
        coEvery { accountRepo.create("taken", "a@b.com", "hash") } throws
            RuntimeException("duplicate key value violates unique constraint")

        val result = service.createAccount(
            CreateAccountRequest.newBuilder()
                .setUsername("taken")
                .setEmail("a@b.com")
                .setPasswordHash("hash")
                .build()
        )

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("duplicate"))
    }

    @Test
    fun `updateLastLogin calls repository`() = runTest {
        coEvery { accountRepo.updateLastLogin("acc-1") } returns Unit

        service.updateLastLogin(
            UpdateLastLoginRequest.newBuilder().setAccountId("acc-1").build()
        )

        coVerify(exactly = 1) { accountRepo.updateLastLogin("acc-1") }
    }

    @Test
    fun `checkBan returns ban status for existing account`() = runTest {
        val banTimestamp = Timestamp.newBuilder()
            .setSeconds(9999999999L)
            .build()
        val record = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-1")
            .setIsBanned(true)
            .setBanReason("Cheating")
            .setBanUntil(banTimestamp)
            .build()
        coEvery { accountRepo.getById("acc-1") } returns record

        val result = service.checkBan(
            CheckBanRequest.newBuilder().setAccountId("acc-1").build()
        )

        assertTrue(result.isBanned)
        assertEquals("Cheating", result.banReason)
        assertEquals(9999999999L, result.banUntil.seconds)
    }

    @Test
    fun `checkBan returns not banned for clean account`() = runTest {
        val record = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-1")
            .setIsBanned(false)
            .build()
        coEvery { accountRepo.getById("acc-1") } returns record

        val result = service.checkBan(
            CheckBanRequest.newBuilder().setAccountId("acc-1").build()
        )

        assertFalse(result.isBanned)
    }

    @Test
    fun `checkBan throws NOT_FOUND for missing account`() = runTest {
        coEvery { accountRepo.getById("ghost") } returns null

        assertFailsWith<StatusException> {
            service.checkBan(
                CheckBanRequest.newBuilder().setAccountId("ghost").build()
            )
        }
    }
}
