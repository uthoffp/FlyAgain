package com.flyagain.world.di

import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.GameDataServiceGrpcKt
import com.flyagain.common.network.HeartbeatTracker
import com.flyagain.common.network.TcpServer
import com.flyagain.common.network.UdpFloodProtection
import com.flyagain.common.network.UdpServer
import com.flyagain.common.redis.RedisClientFactory
import com.flyagain.world.ai.MonsterAI
import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.combat.SkillSystem
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.gameloop.GameLoop
import com.flyagain.world.gameloop.InputQueue
import com.flyagain.world.handler.EnterWorldHandler
import com.flyagain.world.handler.MovementHandler
import com.flyagain.world.handler.PacketRouter
import com.flyagain.world.handler.ZoneChangeHandler
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.network.RedisSessionSecretProvider
import com.flyagain.world.network.WorldUdpHandler
import com.flyagain.world.session.SessionLifecycleManager
import com.flyagain.world.zone.ZoneManager
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val worldServiceModule = module {

    // Config
    single<Config> { ConfigFactory.load() }

    // Coroutine scope for async I/O operations (Redis writes, gRPC calls).
    // Uses Dispatchers.IO (unbounded thread pool) instead of Dispatchers.Default
    // (CPU-core-count limited) to avoid blocking CPU-bound work under load.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    // Redis
    single { RedisClientFactory.create(get<Config>().getString("flyagain.redis.url")) }
    single { RedisClientFactory.createConnection(get()) }

    // gRPC channel to database-service
    single<ManagedChannel> {
        val config = get<Config>()
        ManagedChannelBuilder
            .forAddress(
                config.getString("flyagain.database-service.host"),
                config.getInt("flyagain.database-service.port")
            )
            .usePlaintext()
            .build()
    }
    single { CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub(get<ManagedChannel>()) }
    single { GameDataServiceGrpcKt.GameDataServiceCoroutineStub(get<ManagedChannel>()) }

    // Core game systems
    single { EntityManager() }
    single { InputQueue() }
    single { ZoneManager(get()) }
    single { CombatEngine(get()) }
    single { SkillSystem(get(), get()) }
    single { MonsterAI(get(), get()) }
    single { BroadcastService(get()) }

    // Session lifecycle
    single {
        SessionLifecycleManager(
            entityManager = get(),
            zoneManager = get(),
            redisConnection = get(),
            characterDataStub = get(),
            broadcastService = get()
        )
    }

    // Handlers
    single {
        EnterWorldHandler(
            entityManager = get(),
            zoneManager = get(),
            redisConnection = get(),
            jwtSecret = get<Config>().getString("flyagain.auth.jwt-secret")
        )
    }
    single { MovementHandler(get(), get(), get()) }
    single { ZoneChangeHandler(get(), get(), get(), get()) }

    // Heartbeat tracker
    single { HeartbeatTracker() }

    // Packet Router (TCP)
    single {
        PacketRouter(
            enterWorldHandler = get(),
            zoneChangeHandler = get(),
            entityManager = get(),
            sessionLifecycleManager = get(),
            heartbeatTracker = get(),
            coroutineScope = get()
        )
    }

    // TCP server
    single {
        val config = get<Config>()
        TcpServer(
            port = config.getInt("flyagain.network.tcp-port"),
            maxConnections = config.getInt("flyagain.network.max-connections"),
            maxConnectionsPerIp = config.getInt("flyagain.network.max-connections-per-ip"),
            packetRouter = get<PacketRouter>(),
            serviceName = "World Service"
        )
    }

    // UDP components
    single { UdpFloodProtection() }
    single { RedisSessionSecretProvider(get()) }
    single { WorldUdpHandler(get(), get()) }
    single {
        val config = get<Config>()
        UdpServer(
            port = config.getInt("flyagain.network.udp-port"),
            packetHandler = get<WorldUdpHandler>(),
            sessionSecretProvider = get<RedisSessionSecretProvider>(),
            floodProtection = get()
        )
    }

    // Game Loop
    single {
        val config = get<Config>()
        GameLoop(
            inputQueue = get(),
            entityManager = get(),
            zoneManager = get(),
            movementHandler = get(),
            monsterAI = get(),
            broadcastService = get(),
            sessionLifecycleManager = get(),
            asyncScope = get(),
            tickRate = config.getInt("flyagain.gameloop.tick-rate")
        )
    }
}
