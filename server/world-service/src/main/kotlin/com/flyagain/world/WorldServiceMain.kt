package com.flyagain.world

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("WorldService")
    val config = ConfigFactory.load()

    val tcpPort = config.getInt("flyagain.network.tcp-port")
    val udpPort = config.getInt("flyagain.network.udp-port")
    val tickRate = config.getInt("flyagain.gameloop.tick-rate")
    val dbServiceHost = config.getString("flyagain.database-service.host")
    val dbServicePort = config.getInt("flyagain.database-service.port")

    logger.info("FlyAgain World Service starting...")
    logger.info("TCP port: {}", tcpPort)
    logger.info("UDP port: {}", udpPort)
    logger.info("Tick rate: {} Hz", tickRate)
    logger.info("Database Service: {}:{}", dbServiceHost, dbServicePort)

    // TODO: Initialize Redis connection
    // TODO: Initialize gRPC client to database-service
    // TODO: Load game data (items, monsters, skills, loot tables)
    // TODO: Start Netty TCP server (TLS 1.3)
    // TODO: Start Netty UDP server
    // TODO: Start game loop (20 Hz)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain World Service stopped.")
    })

    logger.info("FlyAgain World Service started successfully.")

    // Block the main thread until shutdown
    Thread.currentThread().join()
}
