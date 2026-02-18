package com.flyagain.login

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("LoginService")
    val config = ConfigFactory.load()

    val tcpPort = config.getInt("flyagain.network.tcp-port")
    val dbServiceHost = config.getString("flyagain.database-service.host")
    val dbServicePort = config.getInt("flyagain.database-service.port")

    logger.info("FlyAgain Login Service starting...")
    logger.info("TCP port: {}", tcpPort)
    logger.info("Database Service: {}:{}", dbServiceHost, dbServicePort)

    // TODO: Initialize Redis connection
    // TODO: Initialize gRPC client to database-service
    // TODO: Start Netty TCP server (TLS 1.3)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain Login Service stopped.")
    })

    logger.info("FlyAgain Login Service started successfully.")

    // Block the main thread until shutdown
    Thread.currentThread().join()
}
