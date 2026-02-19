package com.flyagain.world

import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombatEngineTest {

    private val entityManager = EntityManager()
    private val engine = CombatEngine(entityManager)

    private fun makePlayer(
        entityId: Long = 1L,
        str: Int = 20,
        sta: Int = 10,
        level: Int = 5,
        hp: Int = 500
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = 100L,
            accountId = 200L,
            name = "Warrior",
            characterClass = 1,
            x = 0f, y = 0f, z = 0f,
            str = str, sta = sta, level = level,
            hp = hp, maxHp = hp
        )
    }

    private fun makeMonster(
        entityId: Long = 1_000_000L,
        hp: Int = 100,
        attack: Int = 15,
        defense: Int = 5
    ): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = "Slime",
            x = 0f, y = 0f, z = 0f,
            spawnX = 0f, spawnY = 0f, spawnZ = 0f,
            hp = hp, maxHp = hp,
            attack = attack, defense = defense, level = 1,
            xpReward = 50, aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 1500, moveSpeed = 3f
        )
    }

    @Test
    fun `calculatePlayerVsMonster deals at least MIN_DAMAGE`() {
        val player = makePlayer(str = 1, level = 1)  // low attack
        val monster = makeMonster(defense = 100)       // high defense
        // Run multiple times to account for randomness
        repeat(20) {
            val result = engine.calculatePlayerVsMonster(player, monster)
            assertTrue(result.damage >= CombatEngine.MIN_DAMAGE,
                "Damage should be at least ${CombatEngine.MIN_DAMAGE}, got ${result.damage}")
        }
    }

    @Test
    fun `calculatePlayerVsMonster reduces monster HP`() {
        val player = makePlayer(str = 20, level = 5)
        val monster = makeMonster(hp = 1000, defense = 5)
        val initialHp = monster.hp
        engine.calculatePlayerVsMonster(player, monster)
        assertTrue(monster.hp < initialHp, "Monster HP should decrease after being hit")
    }

    @Test
    fun `calculatePlayerVsMonster reports targetKilled when HP reaches 0`() {
        val player = makePlayer(str = 100, level = 50)
        val monster = makeMonster(hp = 1, defense = 0)
        val result = engine.calculatePlayerVsMonster(player, monster)
        assertTrue(result.targetKilled)
        assertEquals(0, monster.hp)
    }

    @Test
    fun `calculateMonsterVsPlayer reduces player HP`() {
        val monster = makeMonster(attack = 30)
        val player = makePlayer(hp = 500, sta = 5, level = 1)
        val initialHp = player.hp
        engine.calculateMonsterVsPlayer(monster, player)
        assertTrue(player.hp < initialHp, "Player HP should decrease after being hit")
    }

    @Test
    fun `calculateMonsterVsPlayer marks player dirty`() {
        val monster = makeMonster(attack = 30)
        val player = makePlayer(hp = 500)
        engine.calculateMonsterVsPlayer(monster, player)
        assertTrue(player.dirty, "Player should be marked dirty after taking damage")
    }

    @Test
    fun `calculatePlayerVsPlayer deals damage to defender`() {
        val attacker = makePlayer(entityId = 1L, str = 20, level = 5, hp = 500)
        val defender = makePlayer(entityId = 2L, str = 10, sta = 10, level = 3, hp = 500)
        val initialHp = defender.hp
        engine.calculatePlayerVsPlayer(attacker, defender)
        assertTrue(defender.hp < initialHp)
    }

    @Test
    fun `calculateSkillDamage factors in base damage and skill level`() {
        val player = makePlayer(str = 10, level = 1)
        val monster = makeMonster(hp = 10000, defense = 0)
        // baseDamage=50, skillLevel=3, damagePerLevel=10
        // skillAtk = (10*2+1) + 50 + (3*10) = 21 + 50 + 30 = 101
        // damage ~ 101 - 0 + random(-2,2) = ~99-103 (before crit)
        val result = engine.calculateSkillDamage(player, monster, baseDamage = 50, skillLevel = 3, damagePerLevel = 10)
        assertTrue(result.damage >= 90, "Skill damage should be substantial, got ${result.damage}")
    }

    @Test
    fun `canAutoAttack returns false when cooldown not elapsed`() {
        val now = System.currentTimeMillis()
        assertFalse(engine.canAutoAttack(now, CombatEngine.BASE_ATTACK_COOLDOWN_MS))
    }

    @Test
    fun `canAutoAttack returns true when cooldown elapsed`() {
        val longAgo = System.currentTimeMillis() - 5000L
        assertTrue(engine.canAutoAttack(longAgo, CombatEngine.BASE_ATTACK_COOLDOWN_MS))
    }

    @Test
    fun `DamageResult contains correct entity IDs`() {
        val player = makePlayer(entityId = 7L)
        val monster = makeMonster(entityId = 1_000_007L, hp = 10000)
        val result = engine.calculatePlayerVsMonster(player, monster)
        assertEquals(7L, result.attackerEntityId)
        assertEquals(1_000_007L, result.targetEntityId)
    }

    @Test
    fun `damage is always positive due to MIN_DAMAGE`() {
        // Even with extreme defense advantage
        val player = makePlayer(str = 0, level = 0)
        val monster = makeMonster(hp = 10000, defense = 9999)
        repeat(50) {
            val result = engine.calculatePlayerVsMonster(player, monster)
            assertTrue(result.damage > 0, "Damage must always be positive")
        }
    }

    private fun assertFalse(value: Boolean, message: String = "") {
        assertTrue(!value, message)
    }
}
