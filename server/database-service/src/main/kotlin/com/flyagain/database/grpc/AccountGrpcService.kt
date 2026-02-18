package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.AccountRepository
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.StatusException
import org.slf4j.LoggerFactory

class AccountGrpcService(
    private val accountRepo: AccountRepository
) : AccountDataServiceGrpcKt.AccountDataServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(AccountGrpcService::class.java)

    override suspend fun getAccountByUsername(request: GetAccountRequest): AccountRecord {
        logger.debug("getAccountByUsername: {}", request.username)
        return accountRepo.getByUsername(request.username)
            ?: AccountRecord.newBuilder().setFound(false).build()
    }

    override suspend fun getAccountById(request: GetAccountByIdRequest): AccountRecord {
        logger.debug("getAccountById: {}", request.accountId)
        return accountRepo.getById(request.accountId)
            ?: AccountRecord.newBuilder().setFound(false).build()
    }

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

    override suspend fun updateLastLogin(request: UpdateLastLoginRequest): Empty {
        logger.debug("updateLastLogin: accountId={}", request.accountId)
        accountRepo.updateLastLogin(request.accountId)
        return Empty.getDefaultInstance()
    }

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
