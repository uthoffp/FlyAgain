package com.flyagain.server

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("FlyAgain")
    val config = ConfigFactory.load()

    val version = "0.1.0-SNAPSHOT"
    val tcpPort = config.getInt("flyagain.network.tcp-port")
    val udpPort = config.getInt("flyagain.network.udp-port")

    logger.info("FlyAgain Server v{} starting...", version)
    logger.info("TCP port: {}", tcpPort)
    logger.info("UDP port: {}", udpPort)
    logger.info("Tick rate: {} Hz", config.getInt("flyagain.gameloop.tick-rate"))

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain Server stopped.")
    })

    logger.info("FlyAgain Server started successfully.")
}
