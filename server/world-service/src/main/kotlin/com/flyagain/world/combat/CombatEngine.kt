package com.flyagain.world.combat

import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.random.Random

/**
 * Core combat engine for damage calculations and combat resolution.
 * All damage is computed server-side; the client only renders the results.
 *
 * Base damage formula: damage = atk - def + random(-2, +2)
 * Critical hit: 10% chance, damage *= 1.5
 * Minimum damage is always 1 (no zero-damage hits).
 */
class CombatEngine(
    private val entityManager: EntityManager
) {

    private val logger = LoggerFactory.getLogger(CombatEngine::class.java)

    companion object {
        const val CRIT_CHANCE = 0.10
        const val CRIT_MULTIPLIER = 1.5
        const val MIN_DAMAGE = 1
        const val BASE_ATTACK_COOLDOWN_MS = 1500L
        const val AUTO_ATTACK_RANGE = 3.0f
    }

    /**
     * Result of a damage calculation.
     */
    data class DamageResult(
        val damage: Int,
        val isCritical: Boolean,
        val targetKilled: Boolean,
        val targetEntityId: Long,
        val attackerEntityId: Long
    )

    /**
     * Calculate damage from a player attacking a monster.
     */
    fun calculatePlayerVsMonster(player: PlayerEntity, monster: MonsterEntity): DamageResult {
        val atk = player.getAttackPower()
        val def = monster.defense
        return calculateDamage(atk, def, player.entityId, monster.entityId, monster)
    }

    /**
     * Calculate damage from a monster attacking a player.
     */
    fun calculateMonsterVsPlayer(monster: MonsterEntity, player: PlayerEntity): DamageResult {
        val atk = monster.attack
        val def = player.getDefense()
        return calculateDamage(atk, def, monster.entityId, player.entityId, player)
    }

    /**
     * Calculate damage from a player attacking another player.
     * TODO: Implement PvP-specific damage modifiers in Phase 3.
     */
    fun calculatePlayerVsPlayer(attacker: PlayerEntity, defender: PlayerEntity): DamageResult {
        val atk = attacker.getAttackPower()
        val def = defender.getDefense()
        return calculateDamage(atk, def, attacker.entityId, defender.entityId, defender)
    }

    /**
     * Calculate skill damage from a player.
     * @param baseDamage The skill's base damage value.
     * @param skillLevel The player's level in this skill.
     * @param damagePerLevel Additional damage per skill level.
     */
    fun calculateSkillDamage(
        player: PlayerEntity,
        target: MonsterEntity,
        baseDamage: Int,
        skillLevel: Int,
        damagePerLevel: Int
    ): DamageResult {
        val skillAtk = player.getAttackPower() + baseDamage + (skillLevel * damagePerLevel)
        val def = target.defense
        return calculateDamage(skillAtk, def, player.entityId, target.entityId, target)
    }

    /**
     * Core damage formula.
     * damage = atk - def + random(-2, +2)
     * Critical: if random < 0.10, damage *= 1.5
     * Minimum damage = 1
     */
    private fun calculateDamage(
        atk: Int,
        def: Int,
        attackerEntityId: Long,
        targetEntityId: Long,
        target: Any
    ): DamageResult {
        var rawDamage = atk - def + Random.nextInt(-2, 3)

        val isCritical = Random.nextDouble() < CRIT_CHANCE
        if (isCritical) {
            rawDamage = (rawDamage * CRIT_MULTIPLIER).toInt()
        }

        val finalDamage = max(MIN_DAMAGE, rawDamage)
        var targetKilled = false

        // Apply damage to target
        when (target) {
            is MonsterEntity -> {
                target.hp = max(0, target.hp - finalDamage)
                targetKilled = target.hp <= 0
                if (targetKilled) {
                    logger.debug("Monster {} (entityId={}) killed by entityId={}",
                        target.name, target.entityId, attackerEntityId)
                }
            }
            is PlayerEntity -> {
                target.hp = max(0, target.hp - finalDamage)
                target.markDirty()
                targetKilled = target.hp <= 0
                if (targetKilled) {
                    logger.debug("Player {} (entityId={}) killed by entityId={}",
                        target.name, target.entityId, attackerEntityId)
                }
            }
        }

        return DamageResult(
            damage = finalDamage,
            isCritical = isCritical,
            targetKilled = targetKilled,
            targetEntityId = targetEntityId,
            attackerEntityId = attackerEntityId
        )
    }

    /**
     * Check if an attacker can perform an auto-attack (cooldown check).
     */
    fun canAutoAttack(lastAttackTime: Long, attackSpeedMs: Long = BASE_ATTACK_COOLDOWN_MS): Boolean {
        return System.currentTimeMillis() - lastAttackTime >= attackSpeedMs
    }

    /**
     * Process auto-attack for a player if conditions are met.
     * Returns a DamageResult if an attack occurred, null otherwise.
     */
    fun processAutoAttack(player: PlayerEntity): DamageResult? {
        if (!player.autoAttacking) return null
        val targetId = player.targetEntityId ?: return null

        if (!canAutoAttack(player.lastAttackTime)) return null

        // Check if target is a monster
        val monster = entityManager.getMonster(targetId)
        if (monster != null && monster.isAlive()) {
            // Range check (melee range = 3 units)
            val dx = player.x - monster.x
            val dz = player.z - monster.z
            val distSq = dx * dx + dz * dz
            if (distSq > AUTO_ATTACK_RANGE * AUTO_ATTACK_RANGE) {
                return null // Out of range, don't attack but keep auto-attack enabled
            }
            player.lastAttackTime = System.currentTimeMillis()
            return calculatePlayerVsMonster(player, monster)
        }

        // Target dead or gone — stop auto-attacking
        player.autoAttacking = false
        player.targetEntityId = null
        return null
    }
}
