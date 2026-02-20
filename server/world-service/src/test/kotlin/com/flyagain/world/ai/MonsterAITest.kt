package com.flyagain.world.ai

import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.zone.ZoneChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonsterAITest {

    private val entityManager = EntityManager()
    private val combatEngine = CombatEngine(entityManager)
    private val monsterAI = MonsterAI(entityManager, combatEngine)

    private fun makePlayer(
        entityId: Long = 1L,
        x: Float = 0f,
        z: Float = 0f,
        hp: Int = 500
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = entityId + 100,
            accountId = entityId + 200,
            name = "Player$entityId",
            characterClass = 1,
            x = x, y = 0f, z = z,
            hp = hp, maxHp = hp,
            sta = 10, level = 5
        )
    }

    private fun makeMonster(
        entityId: Long = 1_000_000L,
        x: Float = 100f,
        z: Float = 100f,
        hp: Int = 100,
        aggroRange: Float = 15f,
        attackRange: Float = 2f,
        leashDistance: Float = 50f,
        moveSpeed: Float = 3f
    ): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = "Monster$entityId",
            x = x, y = 0f, z = z,
            spawnX = x, spawnY = 0f, spawnZ = z,
            hp = hp, maxHp = hp,
            attack = 15, defense = 5, level = 1,
            xpReward = 50, aggroRange = aggroRange, attackRange = attackRange,
            attackSpeedMs = 1500, moveSpeed = moveSpeed,
            leashDistance = leashDistance
        )
    }

    private fun setupChannel(
        monster: MonsterEntity,
        player: PlayerEntity? = null
    ): ZoneChannel {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        channel.addMonster(monster)
        if (player != null) {
            entityManager.tryAddPlayer(player)
            channel.addPlayer(player)
        }
        return channel
    }

    // --- IDLE state tests ---

    @Test
    fun `IDLE monster aggros on nearby player within range`() {
        val monster = makeMonster(x = 100f, z = 100f, aggroRange = 20f)
        val player = makePlayer(entityId = 1L, x = 110f, z = 100f) // distance = 10
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.AGGRO, monster.aiState)
        assertEquals(player.entityId, monster.targetEntityId)
    }

    @Test
    fun `IDLE monster ignores player outside aggro range`() {
        val monster = makeMonster(x = 100f, z = 100f, aggroRange = 5f)
        val player = makePlayer(entityId = 1L, x = 200f, z = 200f) // far away
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.IDLE, monster.aiState)
        assertEquals(null, monster.targetEntityId)
    }

    @Test
    fun `IDLE monster ignores dead player`() {
        val monster = makeMonster(x = 100f, z = 100f, aggroRange = 20f)
        val player = makePlayer(entityId = 1L, x = 105f, z = 100f, hp = 0)
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.IDLE, monster.aiState)
    }

    // --- AGGRO state tests ---

    @Test
    fun `AGGRO monster transitions to ATTACK when in range`() {
        val monster = makeMonster(x = 100f, z = 100f, attackRange = 5f)
        monster.aiState = AIState.AGGRO
        val player = makePlayer(entityId = 1L, x = 101f, z = 100f) // distance = 1
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.ATTACK, monster.aiState)
    }

    @Test
    fun `AGGRO monster moves toward target`() {
        val monster = makeMonster(x = 100f, z = 100f, attackRange = 2f, moveSpeed = 10f)
        monster.aiState = AIState.AGGRO
        val player = makePlayer(entityId = 1L, x = 120f, z = 100f) // distance = 20
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        val oldX = monster.x
        monsterAI.updateChannel(channel, 1000) // 1 second tick

        assertTrue(monster.x > oldX, "Monster should move toward player (x increased from $oldX to ${monster.x})")
    }

    @Test
    fun `AGGRO monster returns when target is dead`() {
        val monster = makeMonster(x = 100f, z = 100f)
        monster.aiState = AIState.AGGRO
        val player = makePlayer(entityId = 1L, x = 105f, z = 100f, hp = 0)
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.RETURN, monster.aiState)
        assertEquals(null, monster.targetEntityId)
    }

    @Test
    fun `AGGRO monster returns when target no longer exists`() {
        val monster = makeMonster(x = 100f, z = 100f)
        monster.aiState = AIState.AGGRO
        monster.targetEntityId = 999L // non-existent player
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        channel.addMonster(monster)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.RETURN, monster.aiState)
        assertEquals(null, monster.targetEntityId)
    }

    @Test
    fun `AGGRO monster returns when leash exceeded`() {
        // Monster at spawn (100, 100), leash = 50
        val monster = makeMonster(x = 100f, z = 100f, leashDistance = 50f)
        // Move monster far from spawn
        monster.x = 200f
        monster.z = 200f
        monster.aiState = AIState.AGGRO
        val player = makePlayer(entityId = 1L, x = 210f, z = 200f, hp = 500)
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.RETURN, monster.aiState)
        assertEquals(null, monster.targetEntityId)
    }

    // --- ATTACK state tests ---

    @Test
    fun `ATTACK monster deals damage on cooldown`() {
        val monster = makeMonster(x = 100f, z = 100f, attackRange = 5f)
        monster.aiState = AIState.ATTACK
        monster.lastAttackTime = 0L // long ago
        val player = makePlayer(entityId = 1L, x = 101f, z = 100f, hp = 500)
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        val result = monsterAI.updateChannel(channel, 50)

        assertTrue(result.damageEvents.isNotEmpty(), "Should produce damage events")
        assertEquals(player.entityId, result.damageEvents[0].targetEntityId)
    }

    @Test
    fun `ATTACK monster does not deal damage during cooldown`() {
        val monster = makeMonster(x = 100f, z = 100f, attackRange = 5f)
        monster.aiState = AIState.ATTACK
        monster.lastAttackTime = System.currentTimeMillis() // just attacked
        val player = makePlayer(entityId = 1L, x = 101f, z = 100f, hp = 500)
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        val result = monsterAI.updateChannel(channel, 50)

        assertTrue(result.damageEvents.isEmpty(), "Should not deal damage during cooldown")
    }

    @Test
    fun `ATTACK monster switches to AGGRO when target moves out of range`() {
        val monster = makeMonster(x = 100f, z = 100f, attackRange = 2f)
        monster.aiState = AIState.ATTACK
        // Player is far beyond attack range * 1.2
        val player = makePlayer(entityId = 1L, x = 120f, z = 100f, hp = 500)
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.AGGRO, monster.aiState)
    }

    @Test
    fun `ATTACK monster returns when target dies`() {
        val monster = makeMonster(x = 100f, z = 100f, attackRange = 5f)
        monster.aiState = AIState.ATTACK
        val player = makePlayer(entityId = 1L, x = 101f, z = 100f, hp = 0)
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.RETURN, monster.aiState)
    }

    @Test
    fun `ATTACK monster returns when leash exceeded`() {
        val monster = makeMonster(x = 100f, z = 100f, attackRange = 5f, leashDistance = 50f)
        monster.x = 200f
        monster.z = 200f
        monster.aiState = AIState.ATTACK
        val player = makePlayer(entityId = 1L, x = 201f, z = 200f, hp = 500)
        monster.targetEntityId = player.entityId
        val channel = setupChannel(monster, player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.RETURN, monster.aiState)
    }

    // --- RETURN state tests ---

    @Test
    fun `RETURN monster moves toward spawn`() {
        // Monster spawned at (100, 100), currently at (120, 100)
        val monster = makeMonster(x = 100f, z = 100f, moveSpeed = 10f)
        monster.x = 120f
        monster.aiState = AIState.RETURN

        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        channel.addMonster(monster)

        val oldX = monster.x
        monsterAI.updateChannel(channel, 1000) // 1 second

        assertTrue(monster.x < oldX, "Monster should move back toward spawn (x decreased from $oldX to ${monster.x})")
    }

    @Test
    fun `RETURN monster heals and becomes IDLE at spawn`() {
        val monster = makeMonster(x = 100f, z = 100f)
        // Place monster very close to spawn
        monster.x = 100.5f
        monster.z = 100.5f
        monster.hp = 10
        monster.aiState = AIState.RETURN

        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        channel.addMonster(monster)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.IDLE, monster.aiState)
        assertEquals(monster.maxHp, monster.hp)
        assertEquals(monster.spawnX, monster.x)
        assertEquals(monster.spawnZ, monster.z)
    }

    // --- DEAD state tests ---

    @Test
    fun `DEAD monster respawns after timer`() {
        val monster = makeMonster(x = 100f, z = 100f)
        monster.hp = 0
        monster.aiState = AIState.DEAD
        monster.deathTime = System.currentTimeMillis() - 60_000 // died 60s ago
        // Move monster away from spawn to verify respawn resets position
        monster.x = 500f
        monster.z = 500f

        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        channel.addMonster(monster)

        val result = monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.IDLE, monster.aiState)
        assertEquals(monster.maxHp, monster.hp)
        assertEquals(monster.spawnX, monster.x)
        assertEquals(monster.spawnZ, monster.z)
        assertTrue(result.respawnedMonsters.contains(monster))
    }

    @Test
    fun `DEAD monster does not respawn before timer`() {
        val monster = makeMonster(x = 100f, z = 100f)
        monster.hp = 0
        monster.aiState = AIState.DEAD
        monster.deathTime = System.currentTimeMillis() // just died

        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        channel.addMonster(monster)

        val result = monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.DEAD, monster.aiState)
        assertEquals(0, monster.hp)
        assertTrue(result.respawnedMonsters.isEmpty())
    }

    // --- Integration / multi-monster tests ---

    @Test
    fun `updateChannel processes multiple monsters`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)

        val m1 = makeMonster(entityId = 1_000_000L, x = 100f, z = 100f, aggroRange = 5f)
        val m2 = makeMonster(entityId = 1_000_001L, x = 200f, z = 200f, aggroRange = 5f)
        channel.addMonster(m1)
        channel.addMonster(m2)

        val player = makePlayer(entityId = 1L, x = 102f, z = 100f) // near m1, far from m2
        entityManager.tryAddPlayer(player)
        channel.addPlayer(player)

        monsterAI.updateChannel(channel, 50)

        assertEquals(AIState.AGGRO, m1.aiState, "m1 should aggro on nearby player")
        assertEquals(AIState.IDLE, m2.aiState, "m2 should stay idle (player too far)")
    }

    @Test
    fun `AITickResult collects damage and respawn events`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)

        // Attacking monster
        val attacker = makeMonster(entityId = 1_000_000L, x = 100f, z = 100f, attackRange = 5f)
        attacker.aiState = AIState.ATTACK
        attacker.lastAttackTime = 0L
        val player = makePlayer(entityId = 1L, x = 101f, z = 100f, hp = 500)
        attacker.targetEntityId = player.entityId
        entityManager.tryAddPlayer(player)
        channel.addPlayer(player)
        channel.addMonster(attacker)

        // Dead monster ready to respawn
        val dead = makeMonster(entityId = 1_000_001L, x = 200f, z = 200f)
        dead.hp = 0
        dead.aiState = AIState.DEAD
        dead.deathTime = System.currentTimeMillis() - 60_000
        channel.addMonster(dead)

        val result = monsterAI.updateChannel(channel, 50)

        assertFalse(result.damageEvents.isEmpty(), "Should have damage events from attacking monster")
        assertFalse(result.respawnedMonsters.isEmpty(), "Should have respawned monster")
    }
}
