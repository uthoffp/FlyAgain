package com.flyagain.database.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

abstract class BaseRepository(protected val dataSource: DataSource) {

    protected suspend fun <T> withConnection(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                block(conn)
            }
        }

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

    protected fun ResultSet.getTimestampOrNull(column: String) =
        getTimestamp(column)?.toInstant()

    protected fun ResultSet.getIntOrNull(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }
}
