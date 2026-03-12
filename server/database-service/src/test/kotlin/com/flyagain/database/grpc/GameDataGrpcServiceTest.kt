package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.GameDataRepository
import com.google.protobuf.Empty
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GameDataGrpcServiceTest {

    private val gameDataRepo = mockk<GameDataRepository>()
    private val service = GameDataGrpcService(gameDataRepo)

    @Test
    fun `getAllItemDefinitions returns items from repo`() = runTest {
        val items = listOf(
            ItemDefinitionRecord.newBuilder().setId(1).setName("Sword").build(),
            ItemDefinitionRecord.newBuilder().setId(2).setName("Shield").build()
        )
        coEvery { gameDataRepo.getAllItemDefinitions() } returns items

        val result = service.getAllItemDefinitions(Empty.getDefaultInstance())

        assertEquals(2, result.itemsList.size)
        assertEquals("Sword", result.itemsList[0].name)
        assertEquals("Shield", result.itemsList[1].name)
    }

    @Test
    fun `getAllItemDefinitions returns empty list`() = runTest {
        coEvery { gameDataRepo.getAllItemDefinitions() } returns emptyList()

        val result = service.getAllItemDefinitions(Empty.getDefaultInstance())

        assertEquals(0, result.itemsList.size)
    }

    @Test
    fun `getAllMonsterDefinitions returns monsters from repo`() = runTest {
        val monsters = listOf(
            MonsterDefinitionRecord.newBuilder().setId(1).setName("Slime").build()
        )
        coEvery { gameDataRepo.getAllMonsterDefinitions() } returns monsters

        val result = service.getAllMonsterDefinitions(Empty.getDefaultInstance())

        assertEquals(1, result.monstersList.size)
        assertEquals("Slime", result.monstersList[0].name)
    }

    @Test
    fun `getAllMonsterDefinitions returns empty list`() = runTest {
        coEvery { gameDataRepo.getAllMonsterDefinitions() } returns emptyList()

        val result = service.getAllMonsterDefinitions(Empty.getDefaultInstance())

        assertEquals(0, result.monstersList.size)
    }

    @Test
    fun `getAllMonsterSpawns returns spawns from repo`() = runTest {
        val spawns = listOf(
            MonsterSpawnRecord.newBuilder().setId(1).setMonsterId(1).setMapId(1).build()
        )
        coEvery { gameDataRepo.getAllMonsterSpawns() } returns spawns

        val result = service.getAllMonsterSpawns(Empty.getDefaultInstance())

        assertEquals(1, result.spawnsList.size)
        assertEquals(1, result.spawnsList[0].monsterId)
        assertEquals(1, result.spawnsList[0].mapId)
    }

    @Test
    fun `getAllMonsterSpawns returns empty list`() = runTest {
        coEvery { gameDataRepo.getAllMonsterSpawns() } returns emptyList()

        val result = service.getAllMonsterSpawns(Empty.getDefaultInstance())

        assertEquals(0, result.spawnsList.size)
    }

    @Test
    fun `getAllSkillDefinitions returns skills from repo`() = runTest {
        val skills = listOf(
            SkillDefinitionRecord.newBuilder().setId(1).setName("Fireball").build()
        )
        coEvery { gameDataRepo.getAllSkillDefinitions() } returns skills

        val result = service.getAllSkillDefinitions(Empty.getDefaultInstance())

        assertEquals(1, result.skillsList.size)
        assertEquals("Fireball", result.skillsList[0].name)
    }

    @Test
    fun `getAllSkillDefinitions returns empty list`() = runTest {
        coEvery { gameDataRepo.getAllSkillDefinitions() } returns emptyList()

        val result = service.getAllSkillDefinitions(Empty.getDefaultInstance())

        assertEquals(0, result.skillsList.size)
    }

    @Test
    fun `getAllLootTables returns loot entries from repo`() = runTest {
        val entries = listOf(
            LootTableRecord.newBuilder().setId(1).setMonsterId(1).setItemId(1).build()
        )
        coEvery { gameDataRepo.getAllLootTables() } returns entries

        val result = service.getAllLootTables(Empty.getDefaultInstance())

        assertEquals(1, result.entriesList.size)
        assertEquals(1, result.entriesList[0].monsterId)
        assertEquals(1, result.entriesList[0].itemId)
    }

    @Test
    fun `getAllLootTables returns empty list`() = runTest {
        coEvery { gameDataRepo.getAllLootTables() } returns emptyList()

        val result = service.getAllLootTables(Empty.getDefaultInstance())

        assertEquals(0, result.entriesList.size)
    }
}
