package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.GameDataRepository
import com.google.protobuf.Empty
import org.slf4j.LoggerFactory

/**
 * gRPC service implementation for static game data queries.
 *
 * Serves read-only game definition data (items, monsters, spawns, skills,
 * loot tables) to other services. The world-service typically calls these
 * once at startup to populate its in-memory caches.
 *
 * @param gameDataRepo the game data repository (interface — testable with mocks)
 */
class GameDataGrpcService(
    private val gameDataRepo: GameDataRepository
) : GameDataServiceGrpcKt.GameDataServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(GameDataGrpcService::class.java)

    /** Returns all item definitions (weapons, armor, consumables, etc.). */
    override suspend fun getAllItemDefinitions(request: Empty): ItemDefinitionList {
        logger.debug("getAllItemDefinitions")
        val items = gameDataRepo.getAllItemDefinitions()
        return ItemDefinitionList.newBuilder().addAllItems(items).build()
    }

    /** Returns all monster type definitions with stats and AI parameters. */
    override suspend fun getAllMonsterDefinitions(request: Empty): MonsterDefinitionList {
        logger.debug("getAllMonsterDefinitions")
        val monsters = gameDataRepo.getAllMonsterDefinitions()
        return MonsterDefinitionList.newBuilder().addAllMonsters(monsters).build()
    }

    /** Returns all monster spawn points with map placement and respawn timing. */
    override suspend fun getAllMonsterSpawns(request: Empty): MonsterSpawnList {
        logger.debug("getAllMonsterSpawns")
        val spawns = gameDataRepo.getAllMonsterSpawns()
        return MonsterSpawnList.newBuilder().addAllSpawns(spawns).build()
    }

    /** Returns all skill definitions with class requirements, damage, and cooldowns. */
    override suspend fun getAllSkillDefinitions(request: Empty): SkillDefinitionList {
        logger.debug("getAllSkillDefinitions")
        val skills = gameDataRepo.getAllSkillDefinitions()
        return SkillDefinitionList.newBuilder().addAllSkills(skills).build()
    }

    /** Returns the complete monster-to-item loot table. */
    override suspend fun getAllLootTables(request: Empty): LootTableList {
        logger.debug("getAllLootTables")
        val entries = gameDataRepo.getAllLootTables()
        return LootTableList.newBuilder().addAllEntries(entries).build()
    }
}
