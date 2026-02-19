package com.flyagain.database

import com.flyagain.database.di.databaseServiceModule
import com.flyagain.database.migration.FlywayRunner
import com.flyagain.database.writeback.WriteBackScheduler
import com.zaxxer.hikari.HikariDataSource
import io.grpc.Server
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

/**
 * Entry point for the FlyAgain Database Service.
 *
 * Bootstraps the service in order:
 * 1. Koin DI container with [databaseServiceModule]
 * 2. Flyway schema migrations ([FlywayRunner])
 * 3. [WriteBackScheduler] — periodic Redis-to-PostgreSQL flush
 * 4. gRPC server exposing all data operations to other services
 *
 * A JVM shutdown hook ensures graceful teardown in reverse order.
 */
fun main() {
    val logger = LoggerFactory.getLogger("DatabaseService")
    logger.info("FlyAgain Database Service starting...")

    val koinApp = startKoin {
        modules(databaseServiceModule)
    }
    val koin = koinApp.koin

    // Run Flyway migrations (one-shot side-effect)
    val dataSource = koin.get<HikariDataSource>()
    FlywayRunner.migrate(dataSource)

    // Start write-back scheduler
    val writeBackScheduler = koin.get<WriteBackScheduler>()
    writeBackScheduler.start()

    // Start gRPC server
    val grpcServer = koin.get<Server>()
    grpcServer.start()
    logger.info("gRPC server started on port {}", grpcServer.port)

    // Shutdown hook — teardown in reverse order
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down Database Service...")
        writeBackScheduler.stop()
        grpcServer.shutdown()
        koin.get<StatefulRedisConnection<*, *>>().close()
        koin.get<RedisClient>().shutdown()
        dataSource.close()
        stopKoin()
        logger.info("Database Service stopped.")
    })

    logger.info("FlyAgain Database Service started successfully.")
    grpcServer.awaitTermination()
}
