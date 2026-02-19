package com.flyagain.world

import com.flyagain.world.di.worldServiceModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("WorldService")
    logger.info("FlyAgain World Service starting...")

    val koinApp = startKoin {
        modules(worldServiceModule)
    }

    // TODO: Start game loop, TCP server, UDP server

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain World Service shutting down...")
        stopKoin()
        logger.info("FlyAgain World Service stopped.")
    })

    logger.info("FlyAgain World Service started successfully.")

    // Block the main thread until shutdown
    Thread.currentThread().join()
}
