package com.flyagain.database.di

import com.flyagain.common.redis.RedisClientFactory
import com.flyagain.database.config.DatabaseConfig
import com.flyagain.database.grpc.*
import com.flyagain.database.repository.*
import com.flyagain.database.writeback.WriteBackScheduler
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.koin.dsl.module

val databaseServiceModule = module {

    // Config
    single<Config> { ConfigFactory.load() }

    // DataSource
    single<javax.sql.DataSource> { DatabaseConfig.createDataSource(get()) }

    // Repositories (interface -> implementation)
    single<AccountRepository> { AccountRepositoryImpl(get()) }
    single<CharacterRepository> { CharacterRepositoryImpl(get()) }
    single<InventoryRepository> { InventoryRepositoryImpl(get()) }
    single<GameDataRepository> { GameDataRepositoryImpl(get()) }

    // Redis
    single { RedisClientFactory.create(get<Config>().getString("flyagain.redis.url")) }
    single { RedisClientFactory.createConnection(get()) }

    // Write-back scheduler
    single {
        WriteBackScheduler(
            get(),
            get(),
            get<Config>().getLong("flyagain.writeback.redis-to-pg-interval-seconds")
        )
    }

    // gRPC services
    single { AccountGrpcService(get()) }
    single { CharacterGrpcService(get(), get()) }
    single { InventoryGrpcService(get()) }
    single { GameDataGrpcService(get()) }

    // gRPC server
    single {
        GrpcServerFactory.create(
            get<Config>().getInt("flyagain.grpc.port"),
            get(),
            get(),
            get(),
            get()
        )
    }
}
