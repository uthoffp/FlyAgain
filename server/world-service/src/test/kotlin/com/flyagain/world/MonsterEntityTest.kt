package com.flyagain.world

import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.MonsterEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonsterEntityTest {

    private fun makeMonster(
        hp: Int = 100,
        maxHp: Int = 100,
        aiState: AIState = AIState.IDLE,
        x: Float = 0f, y: Float = 0f, z: Float = 0f,
        spawnX: Float = 0f, spawnY: Float = 0f, spawnZ: Float = 0f
    ): MonsterEntity {
        return MonsterEntity(
            entityId = 1_000_000L,
            definitionId = 1,
            name = "Slime",
            x = x, y = y, z = z,
            spawnX = spawnX, spawnY = spawnY, spawnZ = spawnZ,
            hp = hp, maxHp = maxHp,
            attack = 10, defense = 5, level = 1,
            xpReward = 50, aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 1500, moveSpeed = 3f
        ).also { it.aiState = aiState }
    }

    @Test
    fun `isAlive returns true when hp above 0 and not DEAD`() {
        val monster = makeMonster(hp = 50)
        assertTrue(monster.isAlive())
    }

    @Test
    fun `isAlive returns false when hp is 0`() {
        val monster = makeMonster(hp = 0)
        assertFalse(monster.isAlive())
    }

    @Test
    fun `isAlive returns false when state is DEAD`() {
        val monster = makeMonster(hp = 100, aiState = AIState.DEAD)
        assertFalse(monster.isAlive())
    }

    @Test
    fun `distanceTo calculates 3D distance`() {
        val monster = makeMonster(x = 0f, y = 0f, z = 0f)
        // distance to (3, 4, 0) = sqrt(9 + 16 + 0) = 5.0
        assertEquals(5.0f, monster.distanceTo(3f, 4f, 0f), 0.001f)
    }

    @Test
    fun `distanceTo same position is zero`() {
        val monster = makeMonster(x = 10f, y = 20f, z = 30f)
        assertEquals(0.0f, monster.distanceTo(10f, 20f, 30f), 0.001f)
    }

    @Test
    fun `distanceToSpawn returns distance to spawn point`() {
        val monster = makeMonster(x = 30f, y = 0f, z = 40f, spawnX = 0f, spawnY = 0f, spawnZ = 0f)
        // distance = sqrt(900 + 0 + 1600) = sqrt(2500) = 50.0
        assertEquals(50.0f, monster.distanceToSpawn(), 0.001f)
    }

    @Test
    fun `canRespawn returns true when DEAD and timer elapsed`() {
        val monster = makeMonster(aiState = AIState.DEAD)
        monster.deathTime = 1000L
        // Default respawnMs is 30000
        assertTrue(monster.canRespawn(31001L))
    }

    @Test
    fun `canRespawn returns false when DEAD but timer not elapsed`() {
        val monster = makeMonster(aiState = AIState.DEAD)
        monster.deathTime = 1000L
        assertFalse(monster.canRespawn(2000L))
    }

    @Test
    fun `canRespawn returns false when not DEAD`() {
        val monster = makeMonster(aiState = AIState.IDLE)
        monster.deathTime = 0L
        assertFalse(monster.canRespawn(100000L))
    }

    @Test
    fun `default AI state is IDLE`() {
        val monster = MonsterEntity(
            entityId = 1L, definitionId = 1, name = "Test",
            x = 0f, y = 0f, z = 0f,
            spawnX = 0f, spawnY = 0f, spawnZ = 0f,
            hp = 100, maxHp = 100,
            attack = 10, defense = 5, level = 1,
            xpReward = 50, aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 1500, moveSpeed = 3f
        )
        assertEquals(AIState.IDLE, monster.aiState)
    }
}
