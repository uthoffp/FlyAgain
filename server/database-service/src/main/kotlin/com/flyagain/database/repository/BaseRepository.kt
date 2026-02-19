package com.flyagain.database.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Base class for all PostgreSQL repository implementations.
 *
 * Provides connection management helpers that execute database operations
 * on [Dispatchers.IO] via Kotlin coroutines. All concrete repositories
 * (e.g. [AccountRepositoryImpl], [CharacterRepositoryImpl]) extend this
 * class to inherit connection pooling and transaction support.
 *
 * @param dataSource the HikariCP-managed connection pool shared across repositories
 */
abstract class BaseRepository(protected val dataSource: DataSource) {

    /**
     * Acquires a connection from the pool, executes [block], and returns
     * the connection when done. Runs on [Dispatchers.IO] to avoid blocking
     * the caller's coroutine dispatcher.
     *
     * The connection's auto-commit state is inherited from the pool default.
     * For operations that require atomicity, use [withTransaction] instead.
     */
    protected suspend fun <T> withConnection(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                block(conn)
            }
        }

    /**
     * Acquires a connection, disables auto-commit, executes [block] inside
     * a transaction, and commits on success or rolls back on any exception.
     *
     * Use this for any operation that performs multiple SQL statements that
     * must succeed or fail atomically (e.g. inventory swaps, character creation
     * with count checks).
     */
    protected suspend fun <T> withTransaction(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val result = block(conn)
                    conn.commit()
                    result
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        }

    /** Reads a nullable SQL TIMESTAMP column as a Java [java.time.Instant]. */
    protected fun ResultSet.getTimestampOrNull(column: String) =
        getTimestamp(column)?.toInstant()

    /** Reads a nullable SQL INT column, returning `null` when the value is SQL NULL. */
    protected fun ResultSet.getIntOrNull(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }
}
