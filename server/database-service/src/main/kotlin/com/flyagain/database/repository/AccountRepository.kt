package com.flyagain.database.repository

import com.flyagain.common.grpc.AccountRecord

/**
 * Repository interface for account persistence operations.
 *
 * Provides CRUD and query operations against the `accounts` table.
 * Implementations handle connection management, transaction boundaries,
 * and mapping between SQL result sets and protobuf [AccountRecord] objects.
 *
 * @see AccountRepositoryImpl for the PostgreSQL-backed implementation
 */
interface AccountRepository {

    /**
     * Looks up an account by its unique username.
     *
     * @param username the case-sensitive username to search for
     * @return the matching [AccountRecord], or `null` if no account exists with that username
     */
    suspend fun getByUsername(username: String): AccountRecord?

    /**
     * Looks up an account by its primary key.
     *
     * @param accountId the account's database ID
     * @return the matching [AccountRecord], or `null` if no account exists with that ID
     */
    suspend fun getById(accountId: Long): AccountRecord?

    /**
     * Creates a new account with the given credentials.
     *
     * The password should already be hashed (bcrypt cost 12) before reaching this layer.
     * Runs inside a transaction so the insert is atomic.
     *
     * @param username the desired username (must be unique)
     * @param email the account email address (must be unique)
     * @param passwordHash the pre-hashed password (bcrypt)
     * @return the auto-generated account ID
     * @throws Exception if the username or email already exists (unique constraint violation)
     */
    suspend fun create(username: String, email: String, passwordHash: String): Long

    /**
     * Updates the `last_login` timestamp to the current server time.
     *
     * Called after successful authentication to track login activity.
     *
     * @param accountId the account whose login timestamp should be refreshed
     */
    suspend fun updateLastLogin(accountId: Long)
}
