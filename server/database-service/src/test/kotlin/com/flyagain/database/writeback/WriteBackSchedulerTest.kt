package com.flyagain.database.writeback

import com.flyagain.common.grpc.SaveCharacterRequest
import com.flyagain.database.repository.CharacterRepository
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WriteBackSchedulerTest {

    private val characterRepo = mockk<CharacterRepository>()
    private val redisSync = mockk<RedisCommands<String, String>>()
    private val redisConnection = mockk<StatefulRedisConnection<String, String>> {
        every { sync() } returns redisSync
    }

    private val scheduler = WriteBackScheduler(characterRepo, redisConnection)

    @Test
    fun `flushDirtyCharacters does nothing when no dirty keys exist`() = runTest {
        every { redisSync.keys("character:*:dirty") } returns emptyList()

        scheduler.flushDirtyCharacters()

        verify(exactly = 0) { redisSync.hgetall(any()) }
        coVerify(exactly = 0) { characterRepo.save(any()) }
    }

    @Test
    fun `flushDirtyCharacters persists dirty character to PostgreSQL`() = runTest {
        every { redisSync.keys("character:*:dirty") } returns listOf("character:42:dirty")
        every { redisSync.hgetall("character:42") } returns mapOf(
            "hp" to "500",
            "mp" to "100",
            "xp" to "5000",
            "level" to "5",
            "map_id" to "2",
            "pos_x" to "100.5",
            "pos_y" to "0.0",
            "pos_z" to "200.3",
            "gold" to "1500",
            "play_time" to "3600",
            "str" to "25",
            "sta" to "20",
            "dex" to "15",
            "int_stat" to "10",
            "stat_points" to "3"
        )
        val savedSlot = slot<SaveCharacterRequest>()
        coEvery { characterRepo.save(capture(savedSlot)) } just Runs
        every { redisSync.del("character:42:dirty") } returns 1L

        scheduler.flushDirtyCharacters()

        coVerify(exactly = 1) { characterRepo.save(any()) }
        val saved = savedSlot.captured
        assertEquals(42L, saved.characterId)
        assertEquals(500, saved.hp)
        assertEquals(100, saved.mp)
        assertEquals(5000L, saved.xp)
        assertEquals(5, saved.level)
        assertEquals(2, saved.mapId)
        assertEquals(100.5f, saved.posX)
        assertEquals(0.0f, saved.posY)
        assertEquals(200.3f, saved.posZ)
        assertEquals(1500L, saved.gold)
        assertEquals(3600L, saved.playTime)
        assertEquals(25, saved.str)
        assertEquals(20, saved.sta)
        assertEquals(15, saved.dex)
        assertEquals(10, saved.intStat)
        assertEquals(3, saved.statPoints)
    }

    @Test
    fun `flushDirtyCharacters removes dirty marker after successful flush`() = runTest {
        every { redisSync.keys("character:*:dirty") } returns listOf("character:1:dirty")
        every { redisSync.hgetall("character:1") } returns mapOf("hp" to "100", "level" to "1")
        coEvery { characterRepo.save(any()) } just Runs
        every { redisSync.del("character:1:dirty") } returns 1L

        scheduler.flushDirtyCharacters()

        verify(exactly = 1) { redisSync.del("character:1:dirty") }
    }

    @Test
    fun `flushDirtyCharacters skips characters with empty hash data`() = runTest {
        every { redisSync.keys("character:*:dirty") } returns listOf("character:99:dirty")
        every { redisSync.hgetall("character:99") } returns emptyMap()

        scheduler.flushDirtyCharacters()

        coVerify(exactly = 0) { characterRepo.save(any()) }
        verify(exactly = 0) { redisSync.del(any<String>()) }
    }

    @Test
    fun `flushDirtyCharacters continues processing after individual failure`() = runTest {
        every { redisSync.keys("character:*:dirty") } returns listOf(
            "character:1:dirty",
            "character:2:dirty"
        )
        // First character: repo throws
        every { redisSync.hgetall("character:1") } returns mapOf("hp" to "100")
        coEvery { characterRepo.save(match { it.characterId == 1L }) } throws RuntimeException("DB down")

        // Second character: succeeds
        every { redisSync.hgetall("character:2") } returns mapOf("hp" to "200")
        coEvery { characterRepo.save(match { it.characterId == 2L }) } just Runs
        every { redisSync.del("character:2:dirty") } returns 1L

        scheduler.flushDirtyCharacters()

        // Second character should still be saved despite first failing
        coVerify(exactly = 1) { characterRepo.save(match { it.characterId == 2L }) }
        verify(exactly = 1) { redisSync.del("character:2:dirty") }
    }

    @Test
    fun `flushDirtyCharacters skips keys with non-numeric character ID`() = runTest {
        every { redisSync.keys("character:*:dirty") } returns listOf("character:abc:dirty")

        scheduler.flushDirtyCharacters()

        verify(exactly = 0) { redisSync.hgetall(any()) }
        coVerify(exactly = 0) { characterRepo.save(any()) }
    }

    @Test
    fun `flushDirtyCharacters handles multiple dirty characters`() = runTest {
        every { redisSync.keys("character:*:dirty") } returns listOf(
            "character:10:dirty",
            "character:20:dirty",
            "character:30:dirty"
        )
        every { redisSync.hgetall("character:10") } returns mapOf("hp" to "100")
        every { redisSync.hgetall("character:20") } returns mapOf("hp" to "200")
        every { redisSync.hgetall("character:30") } returns mapOf("hp" to "300")
        coEvery { characterRepo.save(any()) } just Runs
        every { redisSync.del(any<String>()) } returns 1L

        scheduler.flushDirtyCharacters()

        coVerify(exactly = 3) { characterRepo.save(any()) }
        verify(exactly = 3) { redisSync.del(any<String>()) }
    }

    @Test
    fun `flushDirtyCharacters uses default values for missing fields`() = runTest {
        every { redisSync.keys("character:*:dirty") } returns listOf("character:5:dirty")
        // Only provide hp — all other fields should fall back to defaults
        every { redisSync.hgetall("character:5") } returns mapOf("hp" to "250")
        val savedSlot = slot<SaveCharacterRequest>()
        coEvery { characterRepo.save(capture(savedSlot)) } just Runs
        every { redisSync.del("character:5:dirty") } returns 1L

        scheduler.flushDirtyCharacters()

        val saved = savedSlot.captured
        assertEquals(5L, saved.characterId)
        assertEquals(250, saved.hp)
        assertEquals(0, saved.mp)       // default
        assertEquals(0L, saved.xp)      // default
        assertEquals(1, saved.level)    // default
        assertEquals(1, saved.mapId)    // default
        assertEquals(0f, saved.posX)    // default
        assertEquals(0L, saved.gold)    // default
    }

    @Test
    fun `stop cancels the scheduler scope`() {
        scheduler.stop()
        // Verify that calling stop does not throw and the scheduler is stopped
        // A second stop should also not throw
        scheduler.stop()
    }
}
