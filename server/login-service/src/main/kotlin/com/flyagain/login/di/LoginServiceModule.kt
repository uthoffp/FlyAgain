package com.flyagain.login.di

import com.flyagain.common.grpc.AccountDataServiceGrpcKt
import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.network.TcpServer
import com.flyagain.common.redis.RedisClientFactory
import com.flyagain.login.auth.JwtManager
import com.flyagain.login.auth.PasswordHasher
import com.flyagain.login.handler.LoginHandler
import com.flyagain.login.handler.PacketRouter
import com.flyagain.login.handler.RegisterHandler
import com.flyagain.login.ratelimit.RateLimiter
import com.flyagain.login.session.SessionManager
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val loginServiceModule = module {

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
    single { AccountDataServiceGrpcKt.AccountDataServiceCoroutineStub(get<ManagedChannel>()) }
    single { CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub(get<ManagedChannel>()) }

    // Auth components
    single { PasswordHasher(get<Config>().getInt("flyagain.auth.bcrypt-cost")) }
    single {
        val config = get<Config>()
        JwtManager(
            config.getString("flyagain.auth.jwt-secret"),
            config.getLong("flyagain.auth.jwt-expiry-hours")
        )
    }
    single { SessionManager(get()) }
    single { RateLimiter(get()) }

    // Handlers
    single {
        val config = get<Config>()
        LoginHandler(
            accountService = get<AccountDataServiceGrpcKt.AccountDataServiceCoroutineStub>(),
            characterService = get<CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub>(),
            passwordHasher = get(),
            jwtManager = get(),
            sessionManager = get(),
            rateLimiter = get(),
            accountServiceHost = config.getString("flyagain.service-endpoints.account-service-host"),
            accountServicePort = config.getInt("flyagain.service-endpoints.account-service-port")
        )
    }
    single { RegisterHandler(get(), get(), get()) }

    // Coroutine scope for the router
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

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
            serviceName = "Login Service"
        )
    }
}
