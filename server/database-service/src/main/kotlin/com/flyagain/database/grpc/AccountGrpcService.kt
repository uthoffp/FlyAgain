package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.AccountRepository
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.StatusException
import org.slf4j.LoggerFactory

/**
 * gRPC service implementation for account-related database operations.
 *
 * Exposes [AccountRepository] operations over gRPC so that the login-service
 * and account-service can read/write account data without direct database access.
 * Returns protobuf [AccountRecord] messages with a `found` flag to distinguish
 * "not found" from errors.
 *
 * @param accountRepo the account repository (interface — testable with mocks)
 */
class AccountGrpcService(
    private val accountRepo: AccountRepository
) : AccountDataServiceGrpcKt.AccountDataServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(AccountGrpcService::class.java)

    /** Looks up an account by username. Returns an [AccountRecord] with `found=false` if missing. */
    override suspend fun getAccountByUsername(request: GetAccountRequest): AccountRecord {
        logger.debug("getAccountByUsername: {}", request.username)
        return accountRepo.getByUsername(request.username)
            ?: AccountRecord.newBuilder().setFound(false).build()
    }

    /** Looks up an account by ID. Returns an [AccountRecord] with `found=false` if missing. */
    override suspend fun getAccountById(request: GetAccountByIdRequest): AccountRecord {
        logger.debug("getAccountById: {}", request.accountId)
        return accountRepo.getById(request.accountId)
            ?: AccountRecord.newBuilder().setFound(false).build()
    }

    /** Creates a new account. Returns success with the new ID, or failure with an error message. */
    override suspend fun createAccount(request: CreateAccountRequest): CreateAccountResponse {
        logger.info("createAccount: username={}", request.username)
        return try {
            val accountId = accountRepo.create(request.username, request.email, request.passwordHash)
            CreateAccountResponse.newBuilder()
                .setSuccess(true)
                .setAccountId(accountId)
                .build()
        } catch (e: Exception) {
            logger.warn("createAccount failed: {}", e.message)
            CreateAccountResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.message ?: "Unknown error")
                .build()
        }
    }

    /** Refreshes the `last_login` timestamp for the given account. */
    override suspend fun updateLastLogin(request: UpdateLastLoginRequest): Empty {
        logger.debug("updateLastLogin: accountId={}", request.accountId)
        accountRepo.updateLastLogin(request.accountId)
        return Empty.getDefaultInstance()
    }

    /** Checks whether an account is currently banned. Throws NOT_FOUND if the account doesn't exist. */
    override suspend fun checkBan(request: CheckBanRequest): BanStatus {
        logger.debug("checkBan: accountId={}", request.accountId)
        val account = accountRepo.getById(request.accountId)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Account not found"))

        return BanStatus.newBuilder()
            .setIsBanned(account.isBanned)
            .setBanReason(account.banReason)
            .setBanUntil(account.banUntil)
            .build()
    }
}
