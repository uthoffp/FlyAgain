package com.flyagain.database.repository

import com.flyagain.common.grpc.AccountRecord
import com.google.protobuf.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [AccountRepository].
 *
 * Uses HikariCP-managed connections via [BaseRepository] and maps SQL rows
 * to protobuf [AccountRecord] objects. All reads use single connections;
 * writes that modify multiple rows use explicit transactions.
 *
 * @param dataSource the HikariCP connection pool
 */
class AccountRepositoryImpl(dataSource: DataSource) : BaseRepository(dataSource), AccountRepository {

    override suspend fun getByUsername(username: String): AccountRecord? = withConnection { conn ->
        conn.prepareStatement(
            "SELECT id, username, email, password_hash, is_banned, ban_reason, ban_until, created_at, last_login FROM accounts WHERE username = ?"
        ).use { stmt ->
            stmt.setString(1, username)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapToAccountRecord(rs) else null
            }
        }
    }

    override suspend fun getById(accountId: Long): AccountRecord? = withConnection { conn ->
        conn.prepareStatement(
            "SELECT id, username, email, password_hash, is_banned, ban_reason, ban_until, created_at, last_login FROM accounts WHERE id = ?"
        ).use { stmt ->
            stmt.setLong(1, accountId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapToAccountRecord(rs) else null
            }
        }
    }

    override suspend fun create(username: String, email: String, passwordHash: String): Long = withTransaction { conn ->
        conn.prepareStatement(
            "INSERT INTO accounts (username, email, password_hash) VALUES (?, ?, ?) RETURNING id"
        ).use { stmt ->
            stmt.setString(1, username)
            stmt.setString(2, email)
            stmt.setString(3, passwordHash)
            stmt.executeQuery().use { rs ->
                rs.next()
                rs.getLong("id")
            }
        }
    }

    override suspend fun updateLastLogin(accountId: Long) = withConnection { conn ->
        conn.prepareStatement(
            "UPDATE accounts SET last_login = NOW() WHERE id = ?"
        ).use { stmt ->
            stmt.setLong(1, accountId)
            stmt.executeUpdate()
        }
        conn.commit()
    }

    /**
     * Maps a SQL [ResultSet][java.sql.ResultSet] row to a protobuf [AccountRecord].
     *
     * Nullable timestamp columns (ban_until, created_at, last_login) are only
     * set on the builder when present in the result set.
     */
    private fun mapToAccountRecord(rs: java.sql.ResultSet): AccountRecord {
        val builder = AccountRecord.newBuilder()
            .setId(rs.getLong("id"))
            .setUsername(rs.getString("username"))
            .setEmail(rs.getString("email"))
            .setPasswordHash(rs.getString("password_hash"))
            .setIsBanned(rs.getBoolean("is_banned"))
            .setBanReason(rs.getString("ban_reason") ?: "")
            .setFound(true)

        rs.getTimestamp("ban_until")?.let {
            builder.setBanUntil(instantToTimestamp(it.toInstant()))
        }
        rs.getTimestamp("created_at")?.let {
            builder.setCreatedAt(instantToTimestamp(it.toInstant()))
        }
        rs.getTimestamp("last_login")?.let {
            builder.setLastLogin(instantToTimestamp(it.toInstant()))
        }

        return builder.build()
    }

    /** Converts a Java [Instant] to a protobuf [Timestamp]. */
    private fun instantToTimestamp(instant: Instant): Timestamp =
        Timestamp.newBuilder()
            .setSeconds(instant.epochSecond)
            .setNanos(instant.nano)
            .build()
}
