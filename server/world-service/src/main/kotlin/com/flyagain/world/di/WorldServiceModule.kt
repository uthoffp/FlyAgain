package com.flyagain.world.di

import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.gameloop.InputQueue
import com.flyagain.world.zone.SpatialGrid
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.koin.dsl.module

val worldServiceModule = module {

    // Config
    single<Config> { ConfigFactory.load() }

    // Core game systems
    single { EntityManager() }
    single { CombatEngine(get()) }
    single { SpatialGrid() }
    single { InputQueue() }

    // TODO: Add Redis, gRPC client, ZoneManager, MonsterAI, SkillSystem,
    //       TcpServer, UdpServer, and GameLoop when world-service is fleshed out
}
