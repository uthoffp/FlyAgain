package com.flyagain.database.di

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

@OptIn(KoinExperimentalAPI::class)
class DatabaseServiceModuleTest {

    @Test
    fun `database service module verifies successfully`() {
        databaseServiceModule.verify(
            extraTypes = listOf(
                io.grpc.Server::class,
                com.zaxxer.hikari.HikariDataSource::class,
                com.zaxxer.hikari.HikariConfig::class,
                io.lettuce.core.RedisClient::class,
                io.lettuce.core.api.StatefulRedisConnection::class,
                javax.sql.DataSource::class,
                com.typesafe.config.Config::class,
                String::class,
                Int::class,
                Long::class,
                Float::class,
            )
        )
    }
}
