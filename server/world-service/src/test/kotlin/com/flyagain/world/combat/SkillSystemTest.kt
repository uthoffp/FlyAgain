package com.flyagain.world.combat

import com.flyagain.common.grpc.SkillDefinitionRecord
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillSystemTest {

    private val entityManager = EntityManager()
    private val combatEngine = CombatEngine(entityManager)
    private val skillSystem = SkillSystem(entityManager, combatEngine)

    private fun makeSkillDef(
        id: Int = 1,
        name: String = "Fireball",
        mpCost: Int = 20,
        cooldownMs: Int = 3000,
        baseDamage: Int = 50,
        damagePerLevel: Int = 10,
        rangeUnits: Float = 15f
    ): SkillDefinitionRecord {
        return SkillDefinitionRecord.newBuilder()
            .setId(id)
            .setName(name)
            .setClassReq(1)
            .setLevelReq(1)
            .setMaxLevel(5)
            .setMpCost(mpCost)
            .setCooldownMs(cooldownMs)
            .setBaseDamage(baseDamage)
            .setDamagePerLevel(damagePerLevel)
            .setRangeUnits(rangeUnits)
            .setDescription("Test skill")
            .build()
    }

    private fun makePlayer(
        entityId: Long = 1L,
        hp: Int = 500,
        mp: Int = 100,
        x: Float = 0f,
        z: Float = 0f
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = entityId + 100,
            accountId = entityId + 200,
            name = "Player$entityId",
            characterClass = 1,
            x = x, y = 0f, z = z,
            hp = hp, maxHp = hp,
            mp = mp, maxMp = mp,
            str = 10, sta = 10, level = 5
        )
    }

    private fun makeMonster(
        entityId: Long = 1_000_000L,
        hp: Int = 500,
        x: Float = 5f,
        z: Float = 0f
    ): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = "Slime",
            x = x, y = 0f, z = z,
            spawnX = x, spawnY = 0f, spawnZ = z,
            hp = hp, maxHp = hp,
            attack = 10, defense = 5, level = 1,
            xpReward = 50, aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 1500, moveSpeed = 3f
        )
    }

    @Test
    fun `loadSkillDefinitions stores skills`() {
        val skill = makeSkillDef(id = 1)
        skillSystem.loadSkillDefinitions(listOf(skill))
        assertNotNull(skillSystem.getSkillDefinition(1))
    }

    @Test
    fun `loadSkillDefinitions clears previous definitions`() {
        skillSystem.loadSkillDefinitions(listOf(makeSkillDef(id = 1)))
        skillSystem.loadSkillDefinitions(listOf(makeSkillDef(id = 2)))
        assertNull(skillSystem.getSkillDefinition(1))
        assertNotNull(skillSystem.getSkillDefinition(2))
    }

    @Test
    fun `getAllSkillDefinitions returns all loaded skills`() {
        skillSystem.loadSkillDefinitions(listOf(
            makeSkillDef(id = 1, name = "Fireball"),
            makeSkillDef(id = 2, name = "Ice Bolt")
        ))
        assertEquals(2, skillSystem.getAllSkillDefinitions().size)
    }

    @Test
    fun `setPlayerSkills and useSkill success`() {
        val skill = makeSkillDef(id = 1, mpCost = 10, rangeUnits = 20f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 3))

        val monster = makeMonster(hp = 1000)
        entityManager.addMonster(monster)

        val result = skillSystem.useSkill(player, 1, monster.entityId)
        assertTrue(result is SkillSystem.SkillResult.Success)
        assertTrue(result.damageResult.damage >= CombatEngine.MIN_DAMAGE)
    }

    @Test
    fun `useSkill deducts MP`() {
        val skill = makeSkillDef(id = 1, mpCost = 25, rangeUnits = 20f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))

        val monster = makeMonster(hp = 1000)
        entityManager.addMonster(monster)

        skillSystem.useSkill(player, 1, monster.entityId)
        assertEquals(75, player.mp)
    }

    @Test
    fun `useSkill sets cooldown`() {
        val skill = makeSkillDef(id = 1, mpCost = 10, cooldownMs = 5000, rangeUnits = 20f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))

        val monster = makeMonster(hp = 1000)
        entityManager.addMonster(monster)

        val before = System.currentTimeMillis()
        skillSystem.useSkill(player, 1, monster.entityId)

        val cooldownExpiry = player.skillCooldowns[1]
        assertNotNull(cooldownExpiry)
        assertTrue(cooldownExpiry >= before + 5000)
    }

    @Test
    fun `useSkill marks player dirty`() {
        val skill = makeSkillDef(id = 1, mpCost = 10, rangeUnits = 20f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))

        val monster = makeMonster(hp = 1000)
        entityManager.addMonster(monster)

        skillSystem.useSkill(player, 1, monster.entityId)
        assertTrue(player.dirty)
    }

    @Test
    fun `useSkill fails for unknown skill`() {
        val player = makePlayer()
        val result = skillSystem.useSkill(player, 999, 1_000_000L)
        assertTrue(result is SkillSystem.SkillResult.Error)
        assertTrue(result.reason.contains("not found"))
    }

    @Test
    fun `useSkill fails when player does not have skill`() {
        val skill = makeSkillDef(id = 1)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer()
        // No skills set for player
        val result = skillSystem.useSkill(player, 1, 1_000_000L)
        assertTrue(result is SkillSystem.SkillResult.Error)
        assertTrue(result.reason.contains("does not have"))
    }

    @Test
    fun `useSkill fails when not enough MP`() {
        val skill = makeSkillDef(id = 1, mpCost = 200)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 50) // not enough
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))

        val result = skillSystem.useSkill(player, 1, 1_000_000L)
        assertTrue(result is SkillSystem.SkillResult.Error)
        assertTrue(result.reason.contains("Not enough MP"))
    }

    @Test
    fun `useSkill fails when on cooldown`() {
        val skill = makeSkillDef(id = 1, mpCost = 10, cooldownMs = 60000, rangeUnits = 20f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))
        // Set cooldown far in the future
        player.skillCooldowns[1] = System.currentTimeMillis() + 60000

        val monster = makeMonster(hp = 1000)
        entityManager.addMonster(monster)

        val result = skillSystem.useSkill(player, 1, monster.entityId)
        assertTrue(result is SkillSystem.SkillResult.Error)
        assertTrue(result.reason.contains("cooldown"))
    }

    @Test
    fun `useSkill fails for dead target`() {
        val skill = makeSkillDef(id = 1, mpCost = 10, rangeUnits = 20f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))

        val monster = makeMonster(hp = 0) // dead
        entityManager.addMonster(monster)

        val result = skillSystem.useSkill(player, 1, monster.entityId)
        assertTrue(result is SkillSystem.SkillResult.Error)
        assertTrue(result.reason.contains("Invalid or dead"))
    }

    @Test
    fun `useSkill fails for non-existent target`() {
        val skill = makeSkillDef(id = 1, mpCost = 10, rangeUnits = 20f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))

        val result = skillSystem.useSkill(player, 1, 9999L) // non-existent
        assertTrue(result is SkillSystem.SkillResult.Error)
        assertTrue(result.reason.contains("Invalid or dead"))
    }

    @Test
    fun `useSkill fails when target out of range`() {
        val skill = makeSkillDef(id = 1, mpCost = 10, rangeUnits = 5f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100, x = 0f, z = 0f)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))

        val monster = makeMonster(hp = 1000, x = 100f, z = 100f) // far away
        entityManager.addMonster(monster)

        val result = skillSystem.useSkill(player, 1, monster.entityId)
        assertTrue(result is SkillSystem.SkillResult.Error)
        assertTrue(result.reason.contains("out of range"))
    }

    @Test
    fun `removePlayerSkills prevents skill use`() {
        val skill = makeSkillDef(id = 1, mpCost = 10, rangeUnits = 20f)
        skillSystem.loadSkillDefinitions(listOf(skill))

        val player = makePlayer(mp = 100)
        skillSystem.setPlayerSkills(player.characterId, mapOf(1 to 1))
        skillSystem.removePlayerSkills(player.characterId)

        val monster = makeMonster(hp = 1000)
        entityManager.addMonster(monster)

        val result = skillSystem.useSkill(player, 1, monster.entityId)
        assertTrue(result is SkillSystem.SkillResult.Error)
        assertTrue(result.reason.contains("does not have"))
    }
}
