package com.flyagain.database.migration

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

object FlywayRunner {

    private val logger = LoggerFactory.getLogger(FlywayRunner::class.java)

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
