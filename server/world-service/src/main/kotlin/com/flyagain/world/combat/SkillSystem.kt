package com.flyagain.world.combat

import com.flyagain.common.grpc.SkillDefinitionRecord
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Validates and processes skill usage.
 * All validation is server-authoritative: checks skill existence,
 * class requirement, MP cost, cooldown, and target range.
 */
class SkillSystem(
    private val entityManager: EntityManager,
    private val combatEngine: CombatEngine
) {

    private val logger = LoggerFactory.getLogger(SkillSystem::class.java)

    // Loaded skill definitions: skillId -> definition
    private val skillDefinitions = HashMap<Int, SkillDefinitionRecord>()

    // Player skill levels: characterId -> (skillId -> level)
    private val playerSkills = HashMap<Long, MutableMap<Int, Int>>()

    /**
     * Load skill definitions from game data (called at startup).
     */
    fun loadSkillDefinitions(skills: List<SkillDefinitionRecord>) {
        skillDefinitions.clear()
        for (skill in skills) {
            skillDefinitions[skill.id] = skill
        }
        logger.info("Loaded {} skill definitions", skillDefinitions.size)
    }

    /**
     * Set a player's known skills (loaded from database on enter world).
     */
    fun setPlayerSkills(characterId: Long, skills: Map<Int, Int>) {
        playerSkills[characterId] = skills.toMutableMap()
    }

    /**
     * Remove a player's skill data (on logout/disconnect).
     */
    fun removePlayerSkills(characterId: Long) {
        playerSkills.remove(characterId)
    }

    /**
     * Sealed class for skill validation results.
     */
    sealed class SkillResult {
        data class Success(val damageResult: CombatEngine.DamageResult) : SkillResult()
        data class Error(val reason: String) : SkillResult()
    }

    /**
     * Validate and execute a skill usage.
     * Checks:
     * 1. Skill exists in definitions
     * 2. Player has learned this skill
     * 3. Player has enough MP
     * 4. Skill is not on cooldown
     * 5. Target is valid and in range
     */
    fun useSkill(player: PlayerEntity, skillId: Int, targetEntityId: Long): SkillResult {
        // 1. Check skill exists
        val skillDef = skillDefinitions[skillId]
            ?: return SkillResult.Error("Skill not found: $skillId")

        // 2. Check player has the skill
        val skills = playerSkills[player.characterId]
        val playerSkillLevel = skills?.get(skillId)
            ?: return SkillResult.Error("Player does not have skill: ${skillDef.name}")

        // 3. Check MP cost
        if (player.mp < skillDef.mpCost) {
            return SkillResult.Error("Not enough MP (need ${skillDef.mpCost}, have ${player.mp})")
        }

        // 4. Check cooldown
        val now = System.currentTimeMillis()
        val cooldownExpiry = player.skillCooldowns[skillId] ?: 0L
        if (now < cooldownExpiry) {
            val remaining = cooldownExpiry - now
            return SkillResult.Error("Skill on cooldown (${remaining}ms remaining)")
        }

        // 5. Check target exists and is in range
        val targetMonster = entityManager.getMonster(targetEntityId)
        if (targetMonster == null || !targetMonster.isAlive()) {
            // TODO: Check for player targets in PvP (Phase 3)
            return SkillResult.Error("Invalid or dead target")
        }

        // Range check
        val dx = player.x - targetMonster.x
        val dz = player.z - targetMonster.z
        val distance = sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        if (distance > skillDef.rangeUnits) {
            return SkillResult.Error("Target out of range (distance=${"%.1f".format(distance)}, range=${skillDef.rangeUnits})")
        }

        // All checks passed - execute skill
        player.mp -= skillDef.mpCost
        player.skillCooldowns[skillId] = now + skillDef.cooldownMs
        player.markDirty()

        val damageResult = combatEngine.calculateSkillDamage(
            player = player,
            target = targetMonster,
            baseDamage = skillDef.baseDamage,
            skillLevel = playerSkillLevel,
            damagePerLevel = skillDef.damagePerLevel
        )

        logger.debug("Player {} used skill {} on monster {} for {} damage{}",
            player.name, skillDef.name, targetMonster.name, damageResult.damage,
            if (damageResult.isCritical) " (CRIT)" else "")

        return SkillResult.Success(damageResult)
    }

    /**
     * Get a skill definition by ID.
     */
    fun getSkillDefinition(skillId: Int): SkillDefinitionRecord? = skillDefinitions[skillId]

    /**
     * Get all skill definitions.
     */
    fun getAllSkillDefinitions(): Collection<SkillDefinitionRecord> = skillDefinitions.values
}
