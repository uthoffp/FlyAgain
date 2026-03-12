package com.flyagain.world.combat

import com.flyagain.common.grpc.LootTableRecord
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadLocalRandom

/**
 * Handles loot generation when monsters are killed.
 * Each loot table entry is rolled independently using its drop chance.
 * Gold drops scale with the monster's level.
 *
 * Server-authoritative: all loot decisions happen server-side.
 */
class LootSystem {

    private val logger = LoggerFactory.getLogger(LootSystem::class.java)

    /**
     * Result of a successful loot roll for a single item.
     */
    data class LootDrop(val itemId: Int, val amount: Int)

    /**
     * Internal representation of a loot table entry.
     */
    private data class LootEntry(
        val itemId: Int,
        val dropChance: Float,
        val minAmount: Int,
        val maxAmount: Int
    )

    // Loot tables keyed by monster definition ID
    private val lootTables: HashMap<Int, MutableList<LootEntry>> = HashMap()

    /**
     * Load loot table definitions from game data (called at startup via gRPC).
     * Clears any previously loaded data and groups entries by monster ID.
     */
    fun loadLootTables(entries: List<LootTableRecord>) {
        lootTables.clear()
        for (record in entries) {
            val entry = LootEntry(
                itemId = record.itemId,
                dropChance = record.dropChance,
                minAmount = record.minAmount,
                maxAmount = record.maxAmount
            )
            lootTables.getOrPut(record.monsterId) { mutableListOf() }.add(entry)
        }
        logger.info("Loaded {} loot table entries for {} monsters", entries.size, lootTables.size)
    }

    /**
     * Roll loot for a killed monster. Each entry in the monster's loot table
     * is rolled independently: if random < dropChance, the item drops with
     * a random amount in [minAmount, maxAmount].
     *
     * @param monsterDefinitionId the monster type that was killed
     * @return list of loot drops (may be empty)
     */
    fun rollLoot(monsterDefinitionId: Int): List<LootDrop> {
        val entries = lootTables[monsterDefinitionId] ?: return emptyList()
        val random = ThreadLocalRandom.current()
        val drops = mutableListOf<LootDrop>()

        for (entry in entries) {
            if (random.nextFloat() < entry.dropChance) {
                val amount = if (entry.minAmount == entry.maxAmount) {
                    entry.minAmount
                } else {
                    // nextInt(origin, bound) returns [origin, bound)
                    random.nextInt(entry.minAmount, entry.maxAmount + 1)
                }
                drops.add(LootDrop(itemId = entry.itemId, amount = amount))
            }
        }

        return drops
    }

    /**
     * Calculate gold drop for a killed monster based on its level.
     * Formula: level * 3 + random(0, level * 2)
     *
     * @param level the monster's level
     * @return gold amount (non-negative)
     */
    fun calculateGoldDrop(level: Int): Int {
        val base = level * 3
        val randomRange = level * 2
        val bonus = if (randomRange > 0) {
            ThreadLocalRandom.current().nextInt(randomRange)
        } else {
            0
        }
        return base + bonus
    }
}
