package com.flyagain.world

import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class EntityManagerTest {

    private val manager = EntityManager()

    private fun makePlayer(entityId: Long = 1L, accountId: Long = 100L, characterId: Long = 200L): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = characterId,
            accountId = accountId,
            name = "TestPlayer",
            characterClass = 1,
            x = 0f, y = 0f, z = 0f
        )
    }

    private fun makeMonster(entityId: Long = 1_000_000L): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = "TestMonster",
            x = 0f, y = 0f, z = 0f,
            spawnX = 0f, spawnY = 0f, spawnZ = 0f,
            hp = 100, maxHp = 100,
            attack = 10, defense = 5, level = 1,
            xpReward = 50, aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 1500, moveSpeed = 3f
        )
    }

    @Test
    fun `nextPlayerId generates sequential IDs starting at 1`() {
        val mgr = EntityManager()
        assertEquals(1L, mgr.nextPlayerId())
        assertEquals(2L, mgr.nextPlayerId())
        assertEquals(3L, mgr.nextPlayerId())
    }

    @Test
    fun `nextMonsterId generates sequential IDs starting at 1_000_000`() {
        val mgr = EntityManager()
        assertEquals(1_000_000L, mgr.nextMonsterId())
        assertEquals(1_000_001L, mgr.nextMonsterId())
    }

    @Test
    fun `addPlayer and getPlayer`() {
        val player = makePlayer()
        manager.tryAddPlayer(player)
        val retrieved = manager.getPlayer(player.entityId)
        assertNotNull(retrieved)
        assertEquals("TestPlayer", retrieved.name)
    }

    @Test
    fun `getPlayerByAccount returns correct player`() {
        val player = makePlayer(entityId = 1L, accountId = 55L)
        manager.tryAddPlayer(player)
        val retrieved = manager.getPlayerByAccount(55L)
        assertNotNull(retrieved)
        assertEquals(1L, retrieved.entityId)
    }

    @Test
    fun `getPlayerByCharacter returns correct player`() {
        val player = makePlayer(entityId = 1L, characterId = 300L)
        manager.tryAddPlayer(player)
        val retrieved = manager.getPlayerByCharacter(300L)
        assertNotNull(retrieved)
        assertEquals(1L, retrieved.entityId)
    }

    @Test
    fun `removePlayer cleans up all lookups`() {
        val player = makePlayer(entityId = 5L, accountId = 10L, characterId = 20L)
        manager.tryAddPlayer(player)
        val removed = manager.removePlayer(5L)
        assertNotNull(removed)
        assertNull(manager.getPlayer(5L))
        assertNull(manager.getPlayerByAccount(10L))
        assertNull(manager.getPlayerByCharacter(20L))
    }

    @Test
    fun `removePlayer returns null for non-existent entity`() {
        assertNull(manager.removePlayer(999L))
    }

    @Test
    fun `addMonster and getMonster`() {
        val monster = makeMonster()
        manager.addMonster(monster)
        val retrieved = manager.getMonster(monster.entityId)
        assertNotNull(retrieved)
        assertEquals("TestMonster", retrieved.name)
    }

    @Test
    fun `removeMonster removes the monster`() {
        val monster = makeMonster(entityId = 1_000_005L)
        manager.addMonster(monster)
        val removed = manager.removeMonster(1_000_005L)
        assertNotNull(removed)
        assertNull(manager.getMonster(1_000_005L))
    }

    @Test
    fun `getPlayerCount reflects additions and removals`() {
        val mgr = EntityManager()
        assertEquals(0, mgr.getPlayerCount())
        mgr.tryAddPlayer(makePlayer(entityId = 1L))
        assertEquals(1, mgr.getPlayerCount())
        mgr.tryAddPlayer(makePlayer(entityId = 2L, accountId = 101L, characterId = 201L))
        assertEquals(2, mgr.getPlayerCount())
        mgr.removePlayer(1L)
        assertEquals(1, mgr.getPlayerCount())
    }

    @Test
    fun `getMonsterCount reflects additions and removals`() {
        val mgr = EntityManager()
        assertEquals(0, mgr.getMonsterCount())
        mgr.addMonster(makeMonster(entityId = 1_000_000L))
        assertEquals(1, mgr.getMonsterCount())
        mgr.removeMonster(1_000_000L)
        assertEquals(0, mgr.getMonsterCount())
    }

    @Test
    fun `isPlayer and isMonster return correct values`() {
        val mgr = EntityManager()
        val player = makePlayer(entityId = 1L)
        val monster = makeMonster(entityId = 1_000_000L)
        mgr.tryAddPlayer(player)
        mgr.addMonster(monster)

        assertTrue(mgr.isPlayer(1L))
        assertFalse(mgr.isMonster(1L))
        assertTrue(mgr.isMonster(1_000_000L))
        assertFalse(mgr.isPlayer(1_000_000L))
    }

    @Test
    fun `getAllPlayers returns all registered players`() {
        val mgr = EntityManager()
        mgr.tryAddPlayer(makePlayer(entityId = 1L, accountId = 100L, characterId = 200L))
        mgr.tryAddPlayer(makePlayer(entityId = 2L, accountId = 101L, characterId = 201L))
        assertEquals(2, mgr.getAllPlayers().size)
    }

    @Test
    fun `getAllMonsters returns all registered monsters`() {
        val mgr = EntityManager()
        mgr.addMonster(makeMonster(entityId = 1_000_000L))
        mgr.addMonster(makeMonster(entityId = 1_000_001L))
        assertEquals(2, mgr.getAllMonsters().size)
    }

    @Test
    fun `getPlayerBySessionToken returns player when token is set`() {
        val player = makePlayer(entityId = 1L, accountId = 100L, characterId = 200L)
        val token = 987654321L
        val playerWithToken = player.copy(sessionTokenLong = token)
        manager.tryAddPlayer(playerWithToken)
        val retrieved = manager.getPlayerBySessionToken(token)
        assertNotNull(retrieved)
        assertEquals(1L, retrieved.entityId)
    }

    @Test
    fun `getPlayerBySessionToken returns null for unknown token`() {
        assertNull(manager.getPlayerBySessionToken(999L))
    }

    @Test
    fun `getPlayerBySessionToken returns null when token is zero`() {
        val player = makePlayer(entityId = 1L, accountId = 100L, characterId = 200L)
        // sessionTokenLong defaults to 0
        manager.tryAddPlayer(player)
        assertNull(manager.getPlayerBySessionToken(0L))
    }

    @Test
    fun `removePlayer cleans up session token lookup`() {
        val token = 123456L
        val player = makePlayer(entityId = 5L, accountId = 10L, characterId = 20L)
            .copy(sessionTokenLong = token)
        manager.tryAddPlayer(player)
        assertNotNull(manager.getPlayerBySessionToken(token))
        manager.removePlayer(5L)
        assertNull(manager.getPlayerBySessionToken(token))
    }

    @Test
    fun `tryAddPlayer rejects duplicate account`() {
        val player1 = makePlayer(entityId = 1L, accountId = 100L, characterId = 200L)
        val player2 = makePlayer(entityId = 2L, accountId = 100L, characterId = 201L)
        assertTrue(manager.tryAddPlayer(player1))
        assertFalse(manager.tryAddPlayer(player2))
        assertEquals(1, manager.getPlayerCount())
    }
}
