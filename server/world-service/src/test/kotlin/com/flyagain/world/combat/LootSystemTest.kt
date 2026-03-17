package com.flyagain.world.combat

import com.flyagain.common.grpc.LootTableRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LootSystemTest {

    private val lootSystem = LootSystem()

    private fun makeLootTableRecord(
        id: Int = 1,
        monsterId: Int = 100,
        itemId: Int = 500,
        dropChance: Float = 0.5f,
        minAmount: Int = 1,
        maxAmount: Int = 1
    ): LootTableRecord {
        return LootTableRecord.newBuilder()
            .setId(id)
            .setMonsterId(monsterId)
            .setItemId(itemId)
            .setDropChance(dropChance)
            .setMinAmount(minAmount)
            .setMaxAmount(maxAmount)
            .build()
    }

    // --- Empty loot table / unknown monster ---

    @Test
    fun `rollLoot for unknown monster returns empty list`() {
        val result = lootSystem.rollLoot(9999)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `rollLoot after loading entries for different monster returns empty`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(monsterId = 100, itemId = 1, dropChance = 1.0f)
        ))
        val result = lootSystem.rollLoot(200) // different monster
        assertTrue(result.isEmpty())
    }

    // --- 100% drop chance always drops ---

    @Test
    fun `100 percent drop chance always drops`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(monsterId = 1, itemId = 10, dropChance = 1.0f, minAmount = 1, maxAmount = 1)
        ))
        // Run many times to be statistically confident
        repeat(100) {
            val drops = lootSystem.rollLoot(1)
            assertEquals(1, drops.size, "100% drop should always produce exactly 1 drop")
            assertEquals(10, drops[0].itemId)
            assertEquals(1, drops[0].amount)
        }
    }

    // --- 0% drop chance never drops ---

    @Test
    fun `0 percent drop chance never drops`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(monsterId = 1, itemId = 10, dropChance = 0.0f, minAmount = 1, maxAmount = 5)
        ))
        repeat(100) {
            val drops = lootSystem.rollLoot(1)
            assertTrue(drops.isEmpty(), "0% drop should never produce a drop")
        }
    }

    // --- Amount range respected ---

    @Test
    fun `drop amount is within min and max range`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(monsterId = 1, itemId = 10, dropChance = 1.0f, minAmount = 3, maxAmount = 7)
        ))
        val amounts = mutableSetOf<Int>()
        repeat(200) {
            val drops = lootSystem.rollLoot(1)
            assertEquals(1, drops.size)
            val amount = drops[0].amount
            assertTrue(amount in 3..7, "Amount $amount should be in [3, 7]")
            amounts.add(amount)
        }
        // With 200 rolls across range 3..7, we should see at least min and max
        assertTrue(amounts.contains(3), "Should produce minimum amount at least once")
        assertTrue(amounts.contains(7), "Should produce maximum amount at least once")
    }

    @Test
    fun `drop amount with equal min and max always produces that amount`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(monsterId = 1, itemId = 10, dropChance = 1.0f, minAmount = 5, maxAmount = 5)
        ))
        repeat(50) {
            val drops = lootSystem.rollLoot(1)
            assertEquals(1, drops.size)
            assertEquals(5, drops[0].amount)
        }
    }

    // --- Multiple entries per monster all roll independently ---

    @Test
    fun `multiple entries per monster all roll independently`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(id = 1, monsterId = 1, itemId = 10, dropChance = 1.0f, minAmount = 1, maxAmount = 1),
            makeLootTableRecord(id = 2, monsterId = 1, itemId = 20, dropChance = 1.0f, minAmount = 1, maxAmount = 1),
            makeLootTableRecord(id = 3, monsterId = 1, itemId = 30, dropChance = 1.0f, minAmount = 1, maxAmount = 1)
        ))
        val drops = lootSystem.rollLoot(1)
        assertEquals(3, drops.size, "All 3 entries with 100% chance should drop")
        val itemIds = drops.map { it.itemId }.toSet()
        assertTrue(itemIds.contains(10))
        assertTrue(itemIds.contains(20))
        assertTrue(itemIds.contains(30))
    }

    @Test
    fun `multiple entries with mixed drop chances produce partial drops`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(id = 1, monsterId = 1, itemId = 10, dropChance = 1.0f, minAmount = 1, maxAmount = 1),
            makeLootTableRecord(id = 2, monsterId = 1, itemId = 20, dropChance = 0.0f, minAmount = 1, maxAmount = 1),
            makeLootTableRecord(id = 3, monsterId = 1, itemId = 30, dropChance = 1.0f, minAmount = 1, maxAmount = 1)
        ))
        repeat(50) {
            val drops = lootSystem.rollLoot(1)
            assertEquals(2, drops.size, "Only 100% entries should drop, 0% should not")
            val itemIds = drops.map { it.itemId }.toSet()
            assertTrue(itemIds.contains(10))
            assertTrue(itemIds.contains(30))
            assertTrue(!itemIds.contains(20))
        }
    }

    // --- Gold calculation scales with level ---

    @Test
    fun `calculateGoldDrop returns non-negative value`() {
        repeat(100) {
            val gold = lootSystem.calculateGoldDrop(1)
            assertTrue(gold >= 0, "Gold should be non-negative, got $gold")
        }
    }

    @Test
    fun `calculateGoldDrop minimum value is level times 3`() {
        // Formula: level * 3 + random(0, level * 2)
        // Minimum is level * 3 + 0 = level * 3
        val level = 10
        repeat(100) {
            val gold = lootSystem.calculateGoldDrop(level)
            assertTrue(gold >= level * 3, "Gold $gold should be >= ${level * 3} for level $level")
        }
    }

    @Test
    fun `calculateGoldDrop maximum value is level times 5`() {
        // Formula: level * 3 + random(0, level * 2)
        // Maximum is level * 3 + level * 2 = level * 5
        // random(0, level*2) is exclusive upper bound, so max is level * 3 + level * 2 - 1 = level * 5 - 1
        // But if level * 2 == 0 (level = 0), random part is 0.
        val level = 10
        repeat(100) {
            val gold = lootSystem.calculateGoldDrop(level)
            // Upper bound: level * 3 + (level * 2 - 1) when level * 2 > 0
            assertTrue(gold < level * 5, "Gold $gold should be < ${level * 5} for level $level")
        }
    }

    @Test
    fun `calculateGoldDrop scales with level`() {
        // Higher level should generally produce more gold
        var lowLevelTotal = 0L
        var highLevelTotal = 0L
        val iterations = 500
        repeat(iterations) {
            lowLevelTotal += lootSystem.calculateGoldDrop(5)
            highLevelTotal += lootSystem.calculateGoldDrop(50)
        }
        val lowAvg = lowLevelTotal.toDouble() / iterations
        val highAvg = highLevelTotal.toDouble() / iterations
        assertTrue(highAvg > lowAvg, "Level 50 avg gold ($highAvg) should be > level 5 avg gold ($lowAvg)")
    }

    @Test
    fun `calculateGoldDrop for level 0 returns 0`() {
        // level * 3 + random(0, 0) = 0 + 0 = 0
        repeat(50) {
            val gold = lootSystem.calculateGoldDrop(0)
            assertEquals(0, gold, "Level 0 should always produce 0 gold")
        }
    }

    // --- loadLootTables behavior ---

    @Test
    fun `loadLootTables groups entries by monster ID`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(id = 1, monsterId = 1, itemId = 10, dropChance = 1.0f),
            makeLootTableRecord(id = 2, monsterId = 1, itemId = 20, dropChance = 1.0f),
            makeLootTableRecord(id = 3, monsterId = 2, itemId = 30, dropChance = 1.0f)
        ))
        val monster1Drops = lootSystem.rollLoot(1)
        assertEquals(2, monster1Drops.size)

        val monster2Drops = lootSystem.rollLoot(2)
        assertEquals(1, monster2Drops.size)
        assertEquals(30, monster2Drops[0].itemId)
    }

    @Test
    fun `loadLootTables clears previous data`() {
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(monsterId = 1, itemId = 10, dropChance = 1.0f)
        ))
        assertEquals(1, lootSystem.rollLoot(1).size)

        // Reload with different data
        lootSystem.loadLootTables(listOf(
            makeLootTableRecord(monsterId = 2, itemId = 20, dropChance = 1.0f)
        ))
        // Old monster should now have no loot
        assertTrue(lootSystem.rollLoot(1).isEmpty())
        assertEquals(1, lootSystem.rollLoot(2).size)
    }
}
