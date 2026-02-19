package com.flyagain.database.config

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory

/**
 * Factory for the HikariCP connection pool used by all repositories.
 *
 * Reads PostgreSQL connection parameters from the Typesafe Config key
 * `flyagain.database` and creates a [HikariDataSource] with auto-commit
 * disabled (transactions are managed explicitly by [BaseRepository][com.flyagain.database.repository.BaseRepository]).
 */
object DatabaseConfig {

    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    /**
     * Creates and returns a configured HikariCP [HikariDataSource].
     *
     * Expected config keys under `flyagain.database`:
     * - `url` — JDBC connection URL (e.g. `jdbc:postgresql://localhost:5432/flyagain`)
     * - `username` — database user
     * - `password` — database password
     * - `max-pool-size` — maximum number of connections in the pool
     *
     * @param config the application's root Typesafe [Config]
     * @return a ready-to-use [HikariDataSource]
     */
    fun createDataSource(config: Config): HikariDataSource {
        val dbConfig = config.getConfig("flyagain.database")

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = dbConfig.getString("url")
            username = dbConfig.getString("username")
            password = dbConfig.getString("password")
            maximumPoolSize = dbConfig.getInt("max-pool-size")
            driverClassName = "org.postgresql.Driver"
            poolName = "flyagain-db-pool"
            isAutoCommit = false
        }

        logger.info("Creating HikariCP pool: url={}, maxPoolSize={}",
            hikariConfig.jdbcUrl, hikariConfig.maximumPoolSize)

        return HikariDataSource(hikariConfig)
    }
}
