package com.flyagain.database.config

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory

object DatabaseConfig {

    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

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
