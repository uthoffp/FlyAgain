package com.flyagain.database

import com.flyagain.common.redis.RedisClientFactory
import com.flyagain.database.config.DatabaseConfig
import com.flyagain.database.grpc.*
import com.flyagain.database.migration.FlywayRunner
import com.flyagain.database.repository.*
import com.flyagain.database.writeback.WriteBackScheduler
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("DatabaseService")
    val config = ConfigFactory.load()

    logger.info("FlyAgain Database Service starting...")

    // 1. Database connection pool
    val dataSource = DatabaseConfig.createDataSource(config)

    // 2. Run Flyway migrations
    FlywayRunner.migrate(dataSource)

    // 3. Create repositories
    val accountRepo = AccountRepository(dataSource)
    val characterRepo = CharacterRepository(dataSource)
    val inventoryRepo = InventoryRepository(dataSource)
    val gameDataRepo = GameDataRepository(dataSource)

    // 4. Redis connection
    val redisUrl = config.getString("flyagain.redis.url")
    val redisClient = RedisClientFactory.create(redisUrl)
    val redisConnection = RedisClientFactory.createConnection(redisClient)

    // 5. Write-back scheduler
    val writeBackInterval = config.getLong("flyagain.writeback.redis-to-pg-interval-seconds")
    val writeBackScheduler = WriteBackScheduler(characterRepo, redisConnection, writeBackInterval)
    writeBackScheduler.start()

    // 6. gRPC server
    val grpcPort = config.getInt("flyagain.grpc.port")
    val grpcServer = GrpcServerFactory.create(
        grpcPort,
        AccountGrpcService(accountRepo),
        CharacterGrpcService(characterRepo, gameDataRepo),
        InventoryGrpcService(inventoryRepo),
        GameDataGrpcService(gameDataRepo)
    )
    grpcServer.start()
    logger.info("gRPC server started on port {}", grpcPort)

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down Database Service...")
        writeBackScheduler.stop()
        grpcServer.shutdown()
        redisConnection.close()
        redisClient.shutdown()
        dataSource.close()
        logger.info("Database Service stopped.")
    })

    logger.info("FlyAgain Database Service started successfully.")
    grpcServer.awaitTermination()
}
