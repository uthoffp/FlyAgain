package com.flyagain.account

import com.flyagain.account.handler.CharacterCreateHandler
import com.flyagain.account.handler.CharacterSelectHandler
import com.flyagain.account.handler.JwtValidator
import com.flyagain.account.handler.PacketRouter
import com.flyagain.account.network.TcpServer
import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.redis.RedisClientFactory
import com.typesafe.config.ConfigFactory
import io.grpc.ManagedChannelBuilder
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("AccountService")
    val config = ConfigFactory.load()

    val tcpPort = config.getInt("flyagain.network.tcp-port")
    val maxConnections = config.getInt("flyagain.network.max-connections")
    val maxConnectionsPerIp = config.getInt("flyagain.network.max-connections-per-ip")
    val redisUrl = config.getString("flyagain.redis.url")
    val jwtSecret = config.getString("flyagain.auth.jwt-secret")
    val dbServiceHost = config.getString("flyagain.database-service.host")
    val dbServicePort = config.getInt("flyagain.database-service.port")
    val worldServiceHost = config.getString("flyagain.service-endpoints.world-service-host")
    val worldServiceTcpPort = config.getInt("flyagain.service-endpoints.world-service-tcp-port")
    val worldServiceUdpPort = config.getInt("flyagain.service-endpoints.world-service-udp-port")

    logger.info("FlyAgain Account Service starting...")
    logger.info("TCP port: {}", tcpPort)
    logger.info("Database Service: {}:{}", dbServiceHost, dbServicePort)

    // Initialize Redis connection
    val redisClient: RedisClient = RedisClientFactory.create(redisUrl)
    val redisConnection: StatefulRedisConnection<String, String> = RedisClientFactory.createConnection(redisClient)
    logger.info("Redis connection established")

    // Initialize gRPC channel to database-service
    val grpcChannel = ManagedChannelBuilder
        .forAddress(dbServiceHost, dbServicePort)
        .usePlaintext()
        .build()
    val characterDataStub = CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub(grpcChannel)
    logger.info("gRPC channel to database-service established at {}:{}", dbServiceHost, dbServicePort)

    // Initialize JWT validator
    val jwtValidator = JwtValidator(jwtSecret)

    // Initialize handlers
    val characterCreateHandler = CharacterCreateHandler(characterDataStub)
    val characterSelectHandler = CharacterSelectHandler(
        characterDataStub = characterDataStub,
        redisConnection = redisConnection,
        worldServiceHost = worldServiceHost,
        worldServiceTcpPort = worldServiceTcpPort,
        worldServiceUdpPort = worldServiceUdpPort
    )

    // Initialize packet router
    val packetRouter = PacketRouter(
        jwtValidator = jwtValidator,
        characterCreateHandler = characterCreateHandler,
        characterSelectHandler = characterSelectHandler
    )

    // Start Netty TCP server
    val tcpServer = TcpServer(
        port = tcpPort,
        maxConnections = maxConnections,
        maxConnectionsPerIp = maxConnectionsPerIp,
        packetRouter = packetRouter
    )

    runBlocking {
        tcpServer.start()
    }

    // Shutdown hook for graceful cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain Account Service shutting down...")
        tcpServer.stop()
        grpcChannel.shutdown()
        redisConnection.close()
        redisClient.shutdown()
        logger.info("FlyAgain Account Service stopped.")
    })

    logger.info("FlyAgain Account Service started successfully on port {}", tcpPort)

    // Block the main thread
    Thread.currentThread().join()
}
