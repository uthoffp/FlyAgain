package com.flyagain.account.di

import com.flyagain.account.handler.CharacterCreateHandler
import com.flyagain.account.handler.CharacterSelectHandler
import com.flyagain.account.handler.JwtValidator
import com.flyagain.account.handler.PacketRouter
import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.network.TcpServer
import com.flyagain.common.redis.RedisClientFactory
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.koin.core.qualifier.named
import org.koin.dsl.module

val accountServiceModule = module {

    // Config
    single<Config> { ConfigFactory.load() }

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

    // Handlers
    single { JwtValidator(get<Config>().getString("flyagain.auth.jwt-secret")) }
    single { CharacterCreateHandler(get()) }
    single {
        val config = get<Config>()
        CharacterSelectHandler(
            characterDataStub = get(),
            redisConnection = get(),
            worldServiceHost = config.getString("flyagain.service-endpoints.world-service-host"),
            worldServiceTcpPort = config.getInt("flyagain.service-endpoints.world-service-tcp-port"),
            worldServiceUdpPort = config.getInt("flyagain.service-endpoints.world-service-udp-port")
        )
    }

    // Packet router
    single { PacketRouter(get(), get(), get()) }

    // TCP server
    single {
        val config = get<Config>()
        TcpServer(
            port = config.getInt("flyagain.network.tcp-port"),
            maxConnections = config.getInt("flyagain.network.max-connections"),
            maxConnectionsPerIp = config.getInt("flyagain.network.max-connections-per-ip"),
            packetRouter = get<PacketRouter>(),
            serviceName = "Account Service"
        )
    }
}
