package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.GameDataRepository
import com.google.protobuf.Empty
import org.slf4j.LoggerFactory

class GameDataGrpcService(
    private val gameDataRepo: GameDataRepository
) : GameDataServiceGrpcKt.GameDataServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(GameDataGrpcService::class.java)

    override suspend fun getAllItemDefinitions(request: Empty): ItemDefinitionList {
        logger.debug("getAllItemDefinitions")
        val items = gameDataRepo.getAllItemDefinitions()
        return ItemDefinitionList.newBuilder().addAllItems(items).build()
    }

    override suspend fun getAllMonsterDefinitions(request: Empty): MonsterDefinitionList {
        logger.debug("getAllMonsterDefinitions")
        val monsters = gameDataRepo.getAllMonsterDefinitions()
        return MonsterDefinitionList.newBuilder().addAllMonsters(monsters).build()
    }

    override suspend fun getAllMonsterSpawns(request: Empty): MonsterSpawnList {
        logger.debug("getAllMonsterSpawns")
        val spawns = gameDataRepo.getAllMonsterSpawns()
        return MonsterSpawnList.newBuilder().addAllSpawns(spawns).build()
    }

    override suspend fun getAllSkillDefinitions(request: Empty): SkillDefinitionList {
        logger.debug("getAllSkillDefinitions")
        val skills = gameDataRepo.getAllSkillDefinitions()
        return SkillDefinitionList.newBuilder().addAllSkills(skills).build()
    }

    override suspend fun getAllLootTables(request: Empty): LootTableList {
        logger.debug("getAllLootTables")
        val entries = gameDataRepo.getAllLootTables()
        return LootTableList.newBuilder().addAllEntries(entries).build()
    }
}
