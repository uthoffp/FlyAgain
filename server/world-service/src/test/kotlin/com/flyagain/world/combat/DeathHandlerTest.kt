package com.flyagain.world.combat

import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeathHandlerTest {

    private val xpSystem = XpSystem()
    private val lootSystem = LootSystem()
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val entityManager = mockk<EntityManager>(relaxed = true)

    private val deathHandler = DeathHandler(xpSystem, lootSystem, broadcastService, entityManager)

    private fun makePlayer(
        entityId: Long = 1L,
        hp: Int = 500,
        maxHp: Int = 500,
        mp: Int = 200,
        maxMp: Int = 200,
        level: Int = 5,
        gold: Long = 100L,
        targetEntityId: Long? = null,
        autoAttacking: Boolean = false
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = "char-100",
            accountId = "acc-200",
            name = "TestPlayer",
            characterClass = 0,
            x = 100f, y = 0f, z = 100f,
            level = level,
            hp = hp, maxHp = maxHp,
            mp = mp, maxMp = maxMp,
            gold = gold,
            targetEntityId = targetEntityId,
            autoAttacking = autoAttacking
        )
    }

    private fun makeMonster(
        entityId: Long = 1_000_001L,
        hp: Int = 100,
        level: Int = 5,
        xpReward: Int = 50,
        definitionId: Int = 1
    ): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = definitionId,
            name = "TestMonster",
            x = 100f, y = 0f, z = 100f,
            spawnX = 100f, spawnY = 0f, spawnZ = 100f,
            hp = hp, maxHp = 100,
            attack = 15, defense = 5, level = level,
            xpReward = xpReward, aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 1500, moveSpeed = 3f
        )
    }

    private fun makeChannel(): ZoneChannel {
        return ZoneChannel(zoneId = 1, channelId = 1)
    }

    // ---- Monster Death Tests ----

    @Test
    fun `handleMonsterDeath transitions monster to DEAD state`() {
        val monster = makeMonster()
        val killer = makePlayer(targetEntityId = monster.entityId, autoAttacking = true)
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        assertEquals(AIState.DEAD, monster.aiState)
        assertEquals(0, monster.hp)
        assertNull(monster.targetEntityId)
        assertTrue(monster.deathTime > 0)
    }

    @Test
    fun `handleMonsterDeath awards XP to killer`() {
        val monster = makeMonster(xpReward = 75)
        val killer = makePlayer(level = 3)
        val initialXp = killer.xp
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        assertEquals(initialXp + 75L, killer.xp)
    }

    @Test
    fun `handleMonsterDeath sends XP gain broadcast`() {
        val monster = makeMonster(xpReward = 50)
        val killer = makePlayer()
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        verify(exactly = 1) { broadcastService.sendXpGain(killer, any()) }
    }

    @Test
    fun `handleMonsterDeath awards gold to killer`() {
        val monster = makeMonster(level = 10)
        val killer = makePlayer(gold = 100L)
        val initialGold = killer.gold
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        // Gold formula: level * 3 + random(0, level * 2)
        // For level 10: at least 30 gold
        assertTrue(killer.gold > initialGold, "Killer should receive gold. Was $initialGold, now ${killer.gold}")
    }

    @Test
    fun `handleMonsterDeath marks killer dirty after gold award`() {
        val monster = makeMonster()
        val killer = makePlayer()
        killer.dirty = false
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        assertTrue(killer.dirty, "Killer should be marked dirty after receiving gold")
    }

    @Test
    fun `handleMonsterDeath stops player auto-attack when target is the killed monster`() {
        val monster = makeMonster(entityId = 1_000_005L)
        val killer = makePlayer(targetEntityId = 1_000_005L, autoAttacking = true)
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        assertFalse(killer.autoAttacking, "Killer's auto-attack should stop when target dies")
    }

    @Test
    fun `handleMonsterDeath does not stop auto-attack when killer targets different entity`() {
        val monster = makeMonster(entityId = 1_000_005L)
        val killer = makePlayer(targetEntityId = 1_000_099L, autoAttacking = true)
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        assertTrue(killer.autoAttacking, "Auto-attack should remain active for a different target")
    }

    @Test
    fun `handleMonsterDeath clears monster target`() {
        val monster = makeMonster()
        monster.targetEntityId = 1L
        val killer = makePlayer()
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        assertNull(monster.targetEntityId, "Monster's target should be cleared on death")
    }

    @Test
    fun `handleMonsterDeath broadcasts stats update on level-up`() {
        // Create a player just below level up threshold
        val killer = makePlayer(level = 1)
        killer.xp = 0L
        killer.xpToNextLevel = XpSystem.xpToNextLevel(2) // XP needed for level 2

        // Monster gives enough XP to level up
        val monster = makeMonster(xpReward = killer.xpToNextLevel.toInt() + 1)
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        assertEquals(2, killer.level, "Killer should have leveled up")
        verify(exactly = 1) { broadcastService.broadcastEntityStatsUpdate(channel, killer) }
    }

    @Test
    fun `handleMonsterDeath does not broadcast stats update when no level-up`() {
        val killer = makePlayer(level = 5)
        killer.xp = 0L
        killer.xpToNextLevel = 10_000L // needs a lot of XP

        val monster = makeMonster(xpReward = 10) // small XP reward
        val channel = makeChannel()

        deathHandler.handleMonsterDeath(monster, killer, channel)

        verify(exactly = 0) { broadcastService.broadcastEntityStatsUpdate(any(), any()) }
    }

    // ---- Player Death Tests ----

    @Test
    fun `handlePlayerDeath heals player to full HP and MP`() {
        val player = makePlayer(hp = 0, maxHp = 500, mp = 10, maxMp = 200)
        val channel = makeChannel()

        deathHandler.handlePlayerDeath(player, channel)

        assertEquals(500, player.hp, "Player should be healed to max HP")
        assertEquals(200, player.mp, "Player should be healed to max MP")
    }

    @Test
    fun `handlePlayerDeath stops combat`() {
        val player = makePlayer(autoAttacking = true, targetEntityId = 1_000_001L)
        val channel = makeChannel()

        deathHandler.handlePlayerDeath(player, channel)

        assertFalse(player.autoAttacking, "Auto-attacking should be stopped on death")
        assertNull(player.targetEntityId, "Target should be cleared on death")
    }

    @Test
    fun `handlePlayerDeath marks player dirty`() {
        val player = makePlayer()
        player.dirty = false
        val channel = makeChannel()

        deathHandler.handlePlayerDeath(player, channel)

        assertTrue(player.dirty, "Player should be marked dirty after death resolution")
    }
}
