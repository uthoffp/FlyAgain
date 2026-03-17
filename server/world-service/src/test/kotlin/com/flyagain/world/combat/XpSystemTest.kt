package com.flyagain.world.combat

import com.flyagain.world.entity.PlayerEntity
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XpSystemTest {

    private val xpSystem = XpSystem()

    // --- Helper ---

    /**
     * Creates a test PlayerEntity with configurable stats.
     * characterClass: 0=Warrior, 1=Mage, 2=Assassin, 3=Cleric
     */
    private fun makePlayer(
        entityId: Long = 1L,
        characterClass: Int = 0,
        level: Int = 1,
        xp: Long = 0L,
        xpToNextLevel: Long = xpCurve(2),
        statPoints: Int = 0,
        hp: Int = 200,
        maxHp: Int = 200,
        mp: Int = 50,
        maxMp: Int = 50,
        str: Int = 10,
        sta: Int = 10,
        dex: Int = 10,
        int: Int = 10
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = "${entityId + 100}",
            accountId = "${entityId + 200}",
            name = "TestPlayer",
            characterClass = characterClass,
            x = 0f, y = 0f, z = 0f,
            level = level,
            xp = xp,
            xpToNextLevel = xpToNextLevel,
            statPoints = statPoints,
            hp = hp,
            maxHp = maxHp,
            mp = mp,
            maxMp = maxMp,
            str = str,
            sta = sta,
            dex = dex,
            `int` = int
        )
    }

    /**
     * Mirror of the XP curve formula for test verification.
     * xpToNextLevel = floor(100 * level^1.5)
     */
    private fun xpCurve(level: Int): Long {
        return floor(100.0 * Math.pow(level.toDouble(), 1.5)).toLong()
    }

    // ========================================================================
    // XP Curve Calculation
    // ========================================================================

    @Test
    fun `xpToNextLevel for level 2 is floor of 100 times 2 to the 1_5`() {
        // level 2: floor(100 * 2^1.5) = floor(100 * 2.828...) = 282
        assertEquals(282L, XpSystem.xpToNextLevel(2))
    }

    @Test
    fun `xpToNextLevel for level 1 is 100`() {
        // level 1: floor(100 * 1^1.5) = 100
        assertEquals(100L, XpSystem.xpToNextLevel(1))
    }

    @Test
    fun `xpToNextLevel for level 10 is correct`() {
        // level 10: floor(100 * 10^1.5) = floor(100 * 31.622...) = 3162
        assertEquals(3162L, XpSystem.xpToNextLevel(10))
    }

    @Test
    fun `xpToNextLevel for level 100 is correct`() {
        // level 100: floor(100 * 100^1.5) = floor(100 * 1000) = 100000
        assertEquals(100_000L, XpSystem.xpToNextLevel(100))
    }

    @Test
    fun `xpToNextLevel for level 200 is correct`() {
        // level 200: floor(100 * 200^1.5) = floor(100 * 2828.427...) = 282842
        assertEquals(282_842L, XpSystem.xpToNextLevel(200))
    }

    // ========================================================================
    // Basic XP Award (no level-up)
    // ========================================================================

    @Test
    fun `awardXp adds XP without level-up when below threshold`() {
        val player = makePlayer(level = 1, xp = 0L, xpToNextLevel = xpCurve(2))
        val result = xpSystem.awardXp(player, 50)

        assertEquals(50L, player.xp)
        assertEquals(1, player.level)
        assertFalse(result.leveledUp)
        assertEquals(0, result.levelsGained)
        assertEquals(50L, result.xpGained)
        assertEquals(50L, result.totalXp)
        assertEquals(xpCurve(2), result.xpToNextLevel)
        assertEquals(1, result.currentLevel)
    }

    @Test
    fun `awardXp returns correct XpResult fields`() {
        val player = makePlayer(level = 5, xp = 100L, xpToNextLevel = xpCurve(6))
        val result = xpSystem.awardXp(player, 200)

        assertEquals(200L, result.xpGained)
        assertEquals(300L, result.totalXp)
        assertEquals(5, result.currentLevel)
        assertFalse(result.leveledUp)
    }

    // ========================================================================
    // Level-up Trigger
    // ========================================================================

    @Test
    fun `awardXp triggers level-up when XP reaches threshold`() {
        val threshold = xpCurve(2) // XP needed to reach level 2
        val player = makePlayer(level = 1, xp = 0L, xpToNextLevel = threshold, sta = 10, `int` = 10)
        val result = xpSystem.awardXp(player, threshold.toInt())

        assertEquals(2, player.level)
        assertTrue(result.leveledUp)
        assertEquals(1, result.levelsGained)
        assertEquals(2, result.currentLevel)
        // Overflow XP should be 0 (exact threshold)
        assertEquals(0L, player.xp)
        // xpToNextLevel should now be for level 3
        assertEquals(xpCurve(3), player.xpToNextLevel)
    }

    @Test
    fun `awardXp triggers level-up when XP exceeds threshold`() {
        val threshold = xpCurve(2) // 282
        val player = makePlayer(level = 1, xp = 0L, xpToNextLevel = threshold, sta = 10, `int` = 10)
        val result = xpSystem.awardXp(player, threshold.toInt() + 50)

        assertEquals(2, player.level)
        assertTrue(result.leveledUp)
        assertEquals(1, result.levelsGained)
        // Overflow: 282 + 50 - 282 = 50
        assertEquals(50L, player.xp)
        assertEquals(xpCurve(3), player.xpToNextLevel)
    }

    // ========================================================================
    // Stat Points on Level-up
    // ========================================================================

    @Test
    fun `awardXp grants stat points on level-up`() {
        val threshold = xpCurve(2)
        val player = makePlayer(level = 1, xp = 0L, xpToNextLevel = threshold, statPoints = 0)
        xpSystem.awardXp(player, threshold.toInt())

        assertEquals(XpSystem.STAT_POINTS_PER_LEVEL, player.statPoints)
    }

    @Test
    fun `awardXp grants cumulative stat points on multi-level-up`() {
        // Give enough XP to jump from level 1 to level 4 (3 level-ups)
        val xpForLevel2 = xpCurve(2) // 282
        val xpForLevel3 = xpCurve(3) // 519
        val xpForLevel4 = xpCurve(4) // 800
        val totalNeeded = xpForLevel2 + xpForLevel3 + xpForLevel4

        val player = makePlayer(level = 1, xp = 0L, xpToNextLevel = xpCurve(2), statPoints = 0)
        xpSystem.awardXp(player, totalNeeded.toInt())

        assertEquals(4, player.level)
        assertEquals(XpSystem.STAT_POINTS_PER_LEVEL * 3, player.statPoints)
    }

    // ========================================================================
    // Full Heal on Level-up
    // ========================================================================

    @Test
    fun `awardXp fully heals HP and MP on level-up`() {
        val threshold = xpCurve(2)
        // Warrior: baseHp=120, baseMp=30
        // After level-up to 2: maxHp = 120 + (2*15) + (10*5) = 120+30+50 = 200
        //                      maxMp = 30 + (2*8) + (10*3) = 30+16+30 = 76
        val player = makePlayer(
            characterClass = 0, // Warrior
            level = 1,
            xp = 0L,
            xpToNextLevel = threshold,
            hp = 50,    // damaged
            maxHp = 185, // will be recalculated
            mp = 10,    // low MP
            maxMp = 46,  // will be recalculated
            sta = 10,
            `int` = 10
        )
        xpSystem.awardXp(player, threshold.toInt())

        // After level-up: level=2, maxHp=200, maxMp=76
        assertEquals(200, player.maxHp)
        assertEquals(76, player.maxMp)
        assertEquals(player.maxHp, player.hp, "HP should be fully healed on level-up")
        assertEquals(player.maxMp, player.mp, "MP should be fully healed on level-up")
    }

    // ========================================================================
    // Multi-level-up
    // ========================================================================

    @Test
    fun `awardXp handles multi-level-up correctly`() {
        // Level 1 -> level 3 (2 level-ups)
        val xpForLevel2 = xpCurve(2) // 282
        val xpForLevel3 = xpCurve(3) // 519
        val totalNeeded = xpForLevel2 + xpForLevel3 // 801
        val extraXp = 100L

        val player = makePlayer(
            characterClass = 0, // Warrior
            level = 1,
            xp = 0L,
            xpToNextLevel = xpCurve(2),
            statPoints = 0,
            sta = 10,
            `int` = 10
        )
        val result = xpSystem.awardXp(player, (totalNeeded + extraXp).toInt())

        assertEquals(3, player.level)
        assertEquals(2, result.levelsGained)
        assertTrue(result.leveledUp)
        assertEquals(extraXp, player.xp)
        assertEquals(xpCurve(4), player.xpToNextLevel)
        assertEquals(XpSystem.STAT_POINTS_PER_LEVEL * 2, player.statPoints)
    }

    @Test
    fun `awardXp from non-zero starting XP with multi-level-up`() {
        // Start at level 1 with 200 XP already. Need 282 total for level 2.
        // Remaining to level 2 = 282 - 200 = 82
        // After leveling to 2, need 519 for level 3
        val player = makePlayer(
            level = 1,
            xp = 200L,
            xpToNextLevel = xpCurve(2), // 282
            statPoints = 5,
            sta = 10,
            `int` = 10
        )
        // Award 82 + 519 + 50 = 651 XP
        val result = xpSystem.awardXp(player, 651)

        assertEquals(3, player.level)
        assertEquals(2, result.levelsGained)
        assertTrue(result.leveledUp)
        assertEquals(50L, player.xp)
        assertEquals(5 + XpSystem.STAT_POINTS_PER_LEVEL * 2, player.statPoints)
    }

    // ========================================================================
    // Dirty Flag Marking
    // ========================================================================

    @Test
    fun `awardXp marks player dirty when XP is awarded`() {
        val player = makePlayer(level = 1, xp = 0L)
        player.dirty = false
        xpSystem.awardXp(player, 50)

        assertTrue(player.dirty, "Player should be marked dirty after receiving XP")
    }

    @Test
    fun `awardXp marks player dirty on level-up`() {
        val threshold = xpCurve(2)
        val player = makePlayer(level = 1, xp = 0L, xpToNextLevel = threshold)
        player.dirty = false
        xpSystem.awardXp(player, threshold.toInt())

        assertTrue(player.dirty, "Player should be marked dirty after level-up")
    }

    // ========================================================================
    // Level Cap (200)
    // ========================================================================

    @Test
    fun `awardXp does not exceed level cap of 200`() {
        val player = makePlayer(
            level = 200,
            xp = 0L,
            xpToNextLevel = 0L, // at cap, no next level
            statPoints = 50,
            sta = 10,
            `int` = 10
        )
        val result = xpSystem.awardXp(player, 999_999)

        assertEquals(XpSystem.MAX_LEVEL, player.level)
        assertFalse(result.leveledUp)
        assertEquals(0, result.levelsGained)
        assertEquals(200, result.currentLevel)
    }

    @Test
    fun `awardXp stops at level cap during multi-level-up`() {
        // Player at level 199, give massive XP
        val player = makePlayer(
            level = 199,
            xp = 0L,
            xpToNextLevel = xpCurve(200),
            statPoints = 0,
            sta = 10,
            `int` = 10
        )
        // Give enough XP to theoretically go past 200
        xpSystem.awardXp(player, 10_000_000)

        assertEquals(XpSystem.MAX_LEVEL, player.level)
        assertEquals(XpSystem.STAT_POINTS_PER_LEVEL, player.statPoints) // only 1 level-up
    }

    @Test
    fun `awardXp at cap does not add XP`() {
        val player = makePlayer(level = 200, xp = 0L, xpToNextLevel = 0L)
        xpSystem.awardXp(player, 1000)

        assertEquals(0L, player.xp, "XP should not increase at level cap")
    }

    // ========================================================================
    // recalculateStats — per-class HP/MP
    // ========================================================================

    @Test
    fun `recalculateStats Warrior at level 10 with base stats`() {
        // Warrior: baseHp=120, baseMp=30
        // maxHp = 120 + (10*15) + (10*5) = 120+150+50 = 320
        // maxMp = 30 + (10*8) + (10*3) = 30+80+30 = 140
        val player = makePlayer(characterClass = 0, level = 10, sta = 10, `int` = 10)
        xpSystem.recalculateStats(player)

        assertEquals(320, player.maxHp)
        assertEquals(140, player.maxMp)
    }

    @Test
    fun `recalculateStats Mage at level 10 with base stats`() {
        // Mage: baseHp=80, baseMp=80
        // maxHp = 80 + (10*15) + (10*5) = 80+150+50 = 280
        // maxMp = 80 + (10*8) + (10*3) = 80+80+30 = 190
        val player = makePlayer(characterClass = 1, level = 10, sta = 10, `int` = 10)
        xpSystem.recalculateStats(player)

        assertEquals(280, player.maxHp)
        assertEquals(190, player.maxMp)
    }

    @Test
    fun `recalculateStats Assassin at level 10 with base stats`() {
        // Assassin: baseHp=90, baseMp=50
        // maxHp = 90 + (10*15) + (10*5) = 90+150+50 = 290
        // maxMp = 50 + (10*8) + (10*3) = 50+80+30 = 160
        val player = makePlayer(characterClass = 2, level = 10, sta = 10, `int` = 10)
        xpSystem.recalculateStats(player)

        assertEquals(290, player.maxHp)
        assertEquals(160, player.maxMp)
    }

    @Test
    fun `recalculateStats Cleric at level 10 with base stats`() {
        // Cleric: baseHp=100, baseMp=70
        // maxHp = 100 + (10*15) + (10*5) = 100+150+50 = 300
        // maxMp = 70 + (10*8) + (10*3) = 70+80+30 = 180
        val player = makePlayer(characterClass = 3, level = 10, sta = 10, `int` = 10)
        xpSystem.recalculateStats(player)

        assertEquals(300, player.maxHp)
        assertEquals(180, player.maxMp)
    }

    @Test
    fun `recalculateStats accounts for high stats`() {
        // Warrior at level 50 with sta=50, int=30
        // maxHp = 120 + (50*15) + (50*5) = 120+750+250 = 1120
        // maxMp = 30 + (50*8) + (30*3) = 30+400+90 = 520
        val player = makePlayer(characterClass = 0, level = 50, sta = 50, `int` = 30)
        xpSystem.recalculateStats(player)

        assertEquals(1120, player.maxHp)
        assertEquals(520, player.maxMp)
    }

    @Test
    fun `recalculateStats at level 1 with minimum stats`() {
        // Warrior at level 1 with sta=1, int=1
        // maxHp = 120 + (1*15) + (1*5) = 120+15+5 = 140
        // maxMp = 30 + (1*8) + (1*3) = 30+8+3 = 41
        val player = makePlayer(characterClass = 0, level = 1, sta = 1, `int` = 1)
        xpSystem.recalculateStats(player)

        assertEquals(140, player.maxHp)
        assertEquals(41, player.maxMp)
    }

    // ========================================================================
    // Constants verification
    // ========================================================================

    @Test
    fun `MAX_LEVEL constant is 200`() {
        assertEquals(200, XpSystem.MAX_LEVEL)
    }

    @Test
    fun `STAT_POINTS_PER_LEVEL constant is 3`() {
        assertEquals(3, XpSystem.STAT_POINTS_PER_LEVEL)
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Test
    fun `awardXp with zero amount does not change state`() {
        val player = makePlayer(level = 5, xp = 100L)
        player.dirty = false
        val result = xpSystem.awardXp(player, 0)

        assertEquals(100L, player.xp)
        assertEquals(5, player.level)
        assertEquals(0L, result.xpGained)
        assertFalse(result.leveledUp)
        // Zero XP should not mark dirty
        assertFalse(player.dirty)
    }

    @Test
    fun `awardXp with negative amount is treated as zero`() {
        val player = makePlayer(level = 5, xp = 100L)
        player.dirty = false
        val result = xpSystem.awardXp(player, -50)

        assertEquals(100L, player.xp)
        assertEquals(5, player.level)
        assertFalse(result.leveledUp)
        assertFalse(player.dirty)
    }

    @Test
    fun `xpToNextLevel result is stored on player after level-up`() {
        val threshold = xpCurve(2)
        val player = makePlayer(level = 1, xp = 0L, xpToNextLevel = threshold, sta = 10, `int` = 10)
        xpSystem.awardXp(player, threshold.toInt())

        assertEquals(xpCurve(3), player.xpToNextLevel,
            "Player's xpToNextLevel should be updated to the next level's requirement")
    }
}
