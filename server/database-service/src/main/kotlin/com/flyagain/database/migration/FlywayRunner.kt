package com.flyagain.database.migration

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Runs Flyway database migrations on startup.
 *
 * Migration SQL scripts are loaded from `classpath:db/migration` and applied
 * in version order. This ensures the PostgreSQL schema is always up-to-date
 * before repositories begin serving queries.
 */
object FlywayRunner {

    private val logger = LoggerFactory.getLogger(FlywayRunner::class.java)

    /**
     * Executes all pending Flyway migrations against the given [dataSource].
     *
     * @param dataSource the JDBC data source pointing to the target database
     */
    fun migrate(dataSource: DataSource) {
        logger.info("Running Flyway migrations...")

        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()

        val result = flyway.migrate()
        logger.info("Flyway migration complete: {} migrations applied", result.migrationsExecuted)
    }
}
