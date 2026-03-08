package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.CharacterRepository
import com.flyagain.database.repository.GameDataRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterGrpcServiceTest {

    private val characterRepo = mockk<CharacterRepository>()
    private val gameDataRepo = mockk<GameDataRepository>()
    private val service = CharacterGrpcService(characterRepo, gameDataRepo)

    @Test
    fun `getCharactersByAccount returns character list`() = runTest {
        val chars = listOf(
            CharacterRecord.newBuilder().setId("c-1").setName("Hero1").setFound(true).build(),
            CharacterRecord.newBuilder().setId("c-2").setName("Hero2").setFound(true).build()
        )
        coEvery { characterRepo.getByAccount("acc-1") } returns chars

        val result = service.getCharactersByAccount(
            GetCharactersByAccountRequest.newBuilder().setAccountId("acc-1").build()
        )

        assertEquals(2, result.charactersList.size)
        assertEquals("Hero1", result.charactersList[0].name)
    }

    @Test
    fun `getCharactersByAccount returns empty list for no characters`() = runTest {
        coEvery { characterRepo.getByAccount("acc-empty") } returns emptyList()

        val result = service.getCharactersByAccount(
            GetCharactersByAccountRequest.newBuilder().setAccountId("acc-empty").build()
        )

        assertEquals(0, result.charactersList.size)
    }

    @Test
    fun `getCharacter returns character when found and owned`() = runTest {
        val record = CharacterRecord.newBuilder()
            .setId("c-1").setName("Hero").setFound(true).setLevel(5).build()
        coEvery { characterRepo.getById("c-1", "acc-1") } returns record

        val result = service.getCharacter(
            GetCharacterRequest.newBuilder().setCharacterId("c-1").setAccountId("acc-1").build()
        )

        assertTrue(result.found)
        assertEquals("Hero", result.name)
        assertEquals(5, result.level)
    }

    @Test
    fun `getCharacter returns found=false when not found`() = runTest {
        coEvery { characterRepo.getById("c-missing", "acc-1") } returns null

        val result = service.getCharacter(
            GetCharacterRequest.newBuilder().setCharacterId("c-missing").setAccountId("acc-1").build()
        )

        assertFalse(result.found)
    }

    @Test
    fun `createCharacter returns success with ID`() = runTest {
        coEvery { characterRepo.create("acc-1", "NewHero", 0) } returns "c-new"

        val result = service.createCharacter(
            CreateCharacterRequest.newBuilder()
                .setAccountId("acc-1").setName("NewHero").setCharacterClass(0).build()
        )

        assertTrue(result.success)
        assertEquals("c-new", result.characterId)
    }

    @Test
    fun `createCharacter returns failure on 3-character limit`() = runTest {
        coEvery { characterRepo.create("acc-1", "FourthHero", 1) } throws
            IllegalStateException("Maximum of 3 characters per account")

        val result = service.createCharacter(
            CreateCharacterRequest.newBuilder()
                .setAccountId("acc-1").setName("FourthHero").setCharacterClass(1).build()
        )

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("3 characters"))
    }

    @Test
    fun `saveCharacter calls repository`() = runTest {
        coEvery { characterRepo.save(any()) } returns Unit

        val request = SaveCharacterRequest.newBuilder()
            .setCharacterId("c-1").setHp(100).setMp(50).build()
        service.saveCharacter(request)

        coVerify(exactly = 1) { characterRepo.save(request) }
    }

    @Test
    fun `deleteCharacter calls softDelete`() = runTest {
        coEvery { characterRepo.softDelete("c-1", "acc-1") } returns Unit

        service.deleteCharacter(
            DeleteCharacterRequest.newBuilder().setCharacterId("c-1").setAccountId("acc-1").build()
        )

        coVerify(exactly = 1) { characterRepo.softDelete("c-1", "acc-1") }
    }

    @Test
    fun `getCharacterSkills returns skill list`() = runTest {
        val skills = listOf(
            CharacterSkillRecord.newBuilder().setSkillId(1).setSkillLevel(2).build()
        )
        coEvery { gameDataRepo.getCharacterSkills("c-1") } returns skills

        val result = service.getCharacterSkills(
            GetCharacterSkillsRequest.newBuilder().setCharacterId("c-1").build()
        )

        assertEquals(1, result.skillsList.size)
        assertEquals(1, result.skillsList[0].skillId)
    }

    @Test
    fun `getCharacterSkills returns empty list for no skills`() = runTest {
        coEvery { gameDataRepo.getCharacterSkills("c-new") } returns emptyList()

        val result = service.getCharacterSkills(
            GetCharacterSkillsRequest.newBuilder().setCharacterId("c-new").build()
        )

        assertEquals(0, result.skillsList.size)
    }
}
