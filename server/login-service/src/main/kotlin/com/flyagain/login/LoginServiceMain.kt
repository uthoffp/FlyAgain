package com.flyagain.login

import com.flyagain.common.network.HeartbeatTracker
import com.flyagain.common.network.TcpServer
import com.flyagain.login.di.loginServiceModule
import io.grpc.ManagedChannel
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
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

    val heartbeatTracker = koin.get<HeartbeatTracker>()
    heartbeatTracker.start()

    val tcpServer = koin.get<TcpServer>()
    tcpServer.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain Login Service shutting down...")
        tcpServer.stop()
        heartbeatTracker.stop()
        koin.get<ManagedChannel>().shutdown()
        koin.get<StatefulRedisConnection<*, *>>().close()
        koin.get<RedisClient>().shutdown()
        stopKoin()
        logger.info("FlyAgain Login Service stopped.")
    })

    logger.info("FlyAgain Login Service started successfully.")

    // Block the main thread until shutdown
    Thread.currentThread().join()
}
