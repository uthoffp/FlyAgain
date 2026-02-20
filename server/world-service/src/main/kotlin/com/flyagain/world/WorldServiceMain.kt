package com.flyagain.world

import com.flyagain.common.grpc.GameDataServiceGrpcKt
import com.flyagain.common.network.HeartbeatTracker
import com.flyagain.common.network.TcpServer
import com.flyagain.common.network.UdpServer
import com.flyagain.world.combat.SkillSystem
import com.flyagain.world.di.worldServiceModule
import com.flyagain.world.gameloop.GameLoop
import com.flyagain.world.zone.ZoneManager
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("WorldService")
    logger.info("FlyAgain World Service starting...")

    val koinApp = startKoin {
        modules(worldServiceModule)
    }
    val koin = koinApp.koin

    // 1. Initialize zones
    val zoneManager = koin.get<ZoneManager>()
    zoneManager.initialize()

    // 2. Load game data from database service and spawn monsters
    runBlocking {
        try {
            val gameDataStub = koin.get<GameDataServiceGrpcKt.GameDataServiceCoroutineStub>()

            // Load monster definitions
            val monsterDefs = gameDataStub.getAllMonsterDefinitions(Empty.getDefaultInstance())
            val monsterDefMap = monsterDefs.monstersList.associateBy { it.id }
            logger.info("Loaded {} monster definitions", monsterDefMap.size)

            // Load spawn data and create monster entities
            val spawns = gameDataStub.getAllMonsterSpawns(Empty.getDefaultInstance())
            zoneManager.spawnMonsters(spawns.spawnsList, monsterDefMap)

            // Load skill definitions
            val skillDefs = gameDataStub.getAllSkillDefinitions(Empty.getDefaultInstance())
            val skillSystem = koin.get<SkillSystem>()
            skillSystem.loadSkillDefinitions(skillDefs.skillsList)
        } catch (e: Exception) {
            logger.warn("Could not load game data from database service (may not be running): {}", e.message)
            logger.info("World service will start without monster spawns and skill definitions")
        }
    }

    // 3. Start heartbeat tracker
    val heartbeatTracker = koin.get<HeartbeatTracker>()
    heartbeatTracker.start()

    // 4. Start game loop
    val gameLoop = koin.get<GameLoop>()
    gameLoop.start()

    // 5. Start TCP server
    val tcpServer = koin.get<TcpServer>()
    tcpServer.start()

    // 6. Start UDP server
    val udpServer = koin.get<UdpServer>()
    udpServer.start()

    // Shutdown hook for graceful cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("FlyAgain World Service shutting down...")

        // Stop in reverse order
        udpServer.stop()
        tcpServer.stop()
        gameLoop.stop()
        heartbeatTracker.stop()
        koin.get<ManagedChannel>().shutdown()
        koin.get<StatefulRedisConnection<*, *>>().close()
        koin.get<RedisClient>().shutdown()
        stopKoin()

        logger.info("FlyAgain World Service stopped.")
    })

    logger.info("FlyAgain World Service started successfully.")
    logger.info("  TCP: port {}", koin.get<com.typesafe.config.Config>().getInt("flyagain.network.tcp-port"))
    logger.info("  UDP: port {}", koin.get<com.typesafe.config.Config>().getInt("flyagain.network.udp-port"))
    logger.info("  Tick rate: {} Hz", koin.get<com.typesafe.config.Config>().getInt("flyagain.gameloop.tick-rate"))

    // Block the main thread until shutdown
    Thread.currentThread().join()
}
