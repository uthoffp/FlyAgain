package com.flyagain.account

import com.flyagain.account.di.accountServiceModule
import com.flyagain.common.network.HeartbeatTracker
import com.flyagain.common.network.TcpServer
import io.grpc.ManagedChannel
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("AccountService")
    logger.info("FlyAgain Account Service starting...")

    val koinApp = startKoin {
        modules(accountServiceModule)
    }
    val koin = koinApp.koin

    val heartbeatTracker = koin.get<HeartbeatTracker>()
    heartbeatTracker.start()

    val tcpServer = koin.get<TcpServer>()
    tcpServer.start()

    // Shutdown hook for graceful cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain Account Service shutting down...")
        tcpServer.stop()
        heartbeatTracker.stop()
        koin.get<ManagedChannel>().shutdown()
        koin.get<StatefulRedisConnection<*, *>>().close()
        koin.get<RedisClient>().shutdown()
        stopKoin()
        logger.info("FlyAgain Account Service stopped.")
    })

    logger.info("FlyAgain Account Service started successfully.")

    // Block the main thread
    Thread.currentThread().join()
}
