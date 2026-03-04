package com.flyagain.world.combat

import com.flyagain.world.entity.PlayerEntity
import org.slf4j.LoggerFactory
import kotlin.math.floor

/**
 * Handles experience point awards, level-up logic, and stat recalculation.
 * All XP calculations are server-authoritative.
 *
 * XP curve formula: xpToNextLevel = floor(100 * level^1.5)
 * where level = the next level the player is progressing toward.
 *
 * On level-up:
 * - Grant STAT_POINTS_PER_LEVEL stat points
 * - Recalculate maxHp and maxMp based on class
 * - Full heal (HP = maxHp, MP = maxMp)
 *
 * Level cap: MAX_LEVEL (200)
 */
class XpSystem {

    private val logger = LoggerFactory.getLogger(XpSystem::class.java)

    companion object {
        const val MAX_LEVEL = 200
        const val STAT_POINTS_PER_LEVEL = 3

        // Class IDs
        private const val CLASS_WARRIOR = 0
        private const val CLASS_MAGE = 1
        private const val CLASS_ASSASSIN = 2
        private const val CLASS_CLERIC = 3

        // Base HP per class
        private const val WARRIOR_BASE_HP = 120
        private const val MAGE_BASE_HP = 80
        private const val ASSASSIN_BASE_HP = 90
        private const val CLERIC_BASE_HP = 100

        // Base MP per class
        private const val WARRIOR_BASE_MP = 30
        private const val MAGE_BASE_MP = 80
        private const val ASSASSIN_BASE_MP = 50
        private const val CLERIC_BASE_MP = 70

        // Scaling factors
        private const val HP_PER_LEVEL = 15
        private const val HP_PER_STA = 5
        private const val MP_PER_LEVEL = 8
        private const val MP_PER_INT = 3

        /**
         * Calculate the XP required to advance from (level-1) to [level].
         * Formula: floor(100 * level^1.5)
         */
        fun xpToNextLevel(level: Int): Long {
            return floor(100.0 * Math.pow(level.toDouble(), 1.5)).toLong()
        }
    }

    /**
     * Result of an XP award operation.
     */
    data class XpResult(
        val xpGained: Long,
        val totalXp: Long,
        val xpToNextLevel: Long,
        val currentLevel: Int,
        val leveledUp: Boolean,
        val levelsGained: Int
    )

    /**
     * Award XP to a player. Handles level-up (including multi-level-up),
     * stat recalculation, full heal, and dirty flag marking.
     *
     * @param player The player to award XP to.
     * @param amount The amount of XP to award (must be positive to have effect).
     * @return An [XpResult] describing the outcome.
     */
    fun awardXp(player: PlayerEntity, amount: Int): XpResult {
        // Guard: zero or negative XP awards are no-ops
        if (amount <= 0) {
            return XpResult(
                xpGained = 0L,
                totalXp = player.xp,
                xpToNextLevel = player.xpToNextLevel,
                currentLevel = player.level,
                leveledUp = false,
                levelsGained = 0
            )
        }

        // Guard: player already at level cap
        if (player.level >= MAX_LEVEL) {
            return XpResult(
                xpGained = 0L,
                totalXp = player.xp,
                xpToNextLevel = player.xpToNextLevel,
                currentLevel = player.level,
                leveledUp = false,
                levelsGained = 0
            )
        }

        val xpGained = amount.toLong()
        player.xp += xpGained
        player.markDirty()

        var levelsGained = 0

        // Check for level-up(s) — loop handles multi-level-up
        while (player.level < MAX_LEVEL && player.xp >= player.xpToNextLevel) {
            // Consume the XP for this level
            player.xp -= player.xpToNextLevel
            player.level++
            levelsGained++

            // Grant stat points
            player.statPoints += STAT_POINTS_PER_LEVEL

            // Recalculate max HP/MP based on new level and class
            recalculateStats(player)

            // Full heal on level-up
            player.hp = player.maxHp
            player.mp = player.maxMp

            // Update xpToNextLevel for the next level
            if (player.level < MAX_LEVEL) {
                player.xpToNextLevel = xpToNextLevel(player.level + 1)
            } else {
                // At cap — no more leveling
                player.xpToNextLevel = 0L
                player.xp = 0L // Discard overflow XP at cap
            }

            logger.info("Player {} leveled up to {} (gained {} stat points)",
                player.name, player.level, STAT_POINTS_PER_LEVEL)
        }

        val leveledUp = levelsGained > 0

        return XpResult(
            xpGained = xpGained,
            totalXp = player.xp,
            xpToNextLevel = player.xpToNextLevel,
            currentLevel = player.level,
            leveledUp = leveledUp,
            levelsGained = levelsGained
        )
    }

    /**
     * Recalculate a player's maxHp and maxMp based on their class, level, and stats.
     *
     * Formulas:
     * - maxHp = baseHp + (level * 15) + (sta * 5)
     * - maxMp = baseMp + (level * 8) + (int * 3)
     *
     * Base values per class:
     * - Warrior:  baseHp=120, baseMp=30
     * - Mage:     baseHp=80,  baseMp=80
     * - Assassin:  baseHp=90,  baseMp=50
     * - Cleric:   baseHp=100, baseMp=70
     */
    fun recalculateStats(player: PlayerEntity) {
        val (baseHp, baseMp) = getClassBaseStats(player.characterClass)

        player.maxHp = baseHp + (player.level * HP_PER_LEVEL) + (player.sta * HP_PER_STA)
        player.maxMp = baseMp + (player.level * MP_PER_LEVEL) + (player.`int` * MP_PER_INT)
    }

    /**
     * Returns the (baseHp, baseMp) pair for a given character class.
     */
    private fun getClassBaseStats(characterClass: Int): Pair<Int, Int> {
        return when (characterClass) {
            CLASS_WARRIOR -> Pair(WARRIOR_BASE_HP, WARRIOR_BASE_MP)
            CLASS_MAGE -> Pair(MAGE_BASE_HP, MAGE_BASE_MP)
            CLASS_ASSASSIN -> Pair(ASSASSIN_BASE_HP, ASSASSIN_BASE_MP)
            CLASS_CLERIC -> Pair(CLERIC_BASE_HP, CLERIC_BASE_MP)
            else -> {
                logger.warn("Unknown character class {}, defaulting to Warrior base stats", characterClass)
                Pair(WARRIOR_BASE_HP, WARRIOR_BASE_MP)
            }
        }
    }
}
