package com.flyagain.login

import com.flyagain.login.di.loginServiceModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("LoginService")
    logger.info("FlyAgain Login Service starting...")

    val koinApp = startKoin {
        modules(loginServiceModule)
    }
    val koin = koinApp.koin

    // TODO: Start TCP server once login service is fully wired
    // val tcpServer = koin.get<TcpServer>()
    // runBlocking { tcpServer.start() }

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain Login Service shutting down...")
        // TODO: Shutdown resources once login service is fully wired
        // koin.get<TcpServer>().stop()
        // koin.get<ManagedChannel>().shutdown()
        // koin.get<StatefulRedisConnection<*, *>>().close()
        // koin.get<RedisClient>().shutdown()
        stopKoin()
        logger.info("FlyAgain Login Service stopped.")
    })

    logger.info("FlyAgain Login Service started successfully.")

    // Block the main thread until shutdown
    Thread.currentThread().join()
}
