# Phase 1.5: Combat Completion — Death/Loot/XP + Client Combat UI

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete Phase 1.5 by implementing server-side death resolution (XP, loot, player death) and client-side combat UI (tab-targeting, target frame, auto-attack, damage numbers).

**Architecture:** Server-authoritative combat. On monster kill, the server awards XP (with level-up), rolls loot (direct-to-inventory), and broadcasts events. The client adds targeting via click/tab, a target frame HUD, auto-attack toggle, and floating damage numbers. All combat messages use existing proto definitions.

**Tech Stack:** Kotlin/Netty (server), GDScript/Godot 4 (client), Protobuf (serialization), PostgreSQL/Flyway (migrations)

---

## Task 1: Server — XP System

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/combat/XpSystem.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/combat/XpSystemTest.kt`

**Step 1: Write XpSystem tests**

```kotlin
// XpSystemTest.kt
package com.flyagain.world.combat

import com.flyagain.world.entity.PlayerEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class XpSystemTest {

    private val xpSystem = XpSystem()

    @Test
    fun `xpToNextLevel calculates correctly for level 1`() {
        assertEquals(100L, xpSystem.xpRequiredForLevel(2))
    }

    @Test
    fun `xpToNextLevel scales with level`() {
        val xp5 = xpSystem.xpRequiredForLevel(5)
        val xp10 = xpSystem.xpRequiredForLevel(10)
        assertTrue(xp10 > xp5, "Higher levels require more XP")
    }

    @Test
    fun `awardXp returns no level-up when not enough xp`() {
        val player = createPlayer(level = 1, xp = 0)
        val result = xpSystem.awardXp(player, 50)
        assertEquals(50L, player.xp)
        assertFalse(result.leveledUp)
        assertEquals(1, player.level)
    }

    @Test
    fun `awardXp triggers level-up when threshold reached`() {
        val player = createPlayer(level = 1, xp = 90)
        val result = xpSystem.awardXp(player, 20)
        assertTrue(result.leveledUp)
        assertEquals(2, player.level)
        assertEquals(10L, player.xp) // overflow XP carried over
    }

    @Test
    fun `awardXp grants 3 stat points on level-up`() {
        val player = createPlayer(level = 1, xp = 0, statPoints = 0)
        val xpNeeded = xpSystem.xpRequiredForLevel(2)
        xpSystem.awardXp(player, xpNeeded.toInt())
        assertEquals(3, player.statPoints)
    }

    @Test
    fun `awardXp fully heals on level-up`() {
        val player = createPlayer(level = 1, xp = 0, hp = 50, mp = 20)
        val xpNeeded = xpSystem.xpRequiredForLevel(2)
        xpSystem.awardXp(player, xpNeeded.toInt())
        assertEquals(player.maxHp, player.hp)
        assertEquals(player.maxMp, player.mp)
    }

    @Test
    fun `awardXp can skip multiple levels`() {
        val player = createPlayer(level = 1, xp = 0)
        xpSystem.awardXp(player, 999999)
        assertTrue(player.level > 2, "Should level up multiple times")
    }

    @Test
    fun `awardXp marks player dirty`() {
        val player = createPlayer(level = 1, xp = 0)
        player.dirty = false
        xpSystem.awardXp(player, 10)
        assertTrue(player.dirty)
    }

    @Test
    fun `level cap at 200 prevents further leveling`() {
        val player = createPlayer(level = 200, xp = 0)
        val result = xpSystem.awardXp(player, 999999)
        assertEquals(200, player.level)
        assertFalse(result.leveledUp)
    }

    private fun createPlayer(
        level: Int = 1, xp: Long = 0L, statPoints: Int = 0,
        hp: Int = 100, mp: Int = 50
    ): PlayerEntity {
        return PlayerEntity(
            entityId = 1L, characterId = "char-1", accountId = "acc-1",
            name = "TestPlayer", characterClass = 0,
            x = 0f, y = 0f, z = 0f,
            level = level, xp = xp, statPoints = statPoints,
            hp = hp, maxHp = 100, mp = mp, maxMp = 50
        )
    }
}
```

**Step 2: Run tests — expect FAIL (class not found)**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.combat.XpSystemTest" --no-daemon`

**Step 3: Implement XpSystem**

```kotlin
// XpSystem.kt
package com.flyagain.world.combat

import com.flyagain.world.entity.PlayerEntity
import org.slf4j.LoggerFactory
import kotlin.math.floor
import kotlin.math.pow

class XpSystem {

    private val logger = LoggerFactory.getLogger(XpSystem::class.java)

    companion object {
        const val MAX_LEVEL = 200
        const val STAT_POINTS_PER_LEVEL = 3
    }

    data class XpResult(
        val xpGained: Long,
        val totalXp: Long,
        val xpToNextLevel: Long,
        val currentLevel: Int,
        val leveledUp: Boolean,
        val levelsGained: Int = 0
    )

    /**
     * XP required to reach the given level (from level - 1).
     * Formula: 100 * level^1.5 (rounded down).
     * Level 2 = 283, Level 5 = 1118, Level 10 = 3162, Level 15 = 5809
     */
    fun xpRequiredForLevel(level: Int): Long {
        if (level <= 1) return 0L
        return floor(100.0 * level.toDouble().pow(1.5)).toLong()
    }

    /**
     * Award XP to a player. Handles level-up (possibly multiple), stat point
     * grants, HP/MP full heal, and recalculation of xpToNextLevel.
     */
    fun awardXp(player: PlayerEntity, amount: Int): XpResult {
        if (player.level >= MAX_LEVEL) {
            return XpResult(
                xpGained = 0, totalXp = player.xp,
                xpToNextLevel = 0, currentLevel = player.level,
                leveledUp = false
            )
        }

        player.xp += amount
        player.markDirty()

        var leveledUp = false
        var levelsGained = 0
        var xpNeeded = xpRequiredForLevel(player.level + 1)

        while (player.level < MAX_LEVEL && player.xp >= xpNeeded) {
            player.xp -= xpNeeded
            player.level++
            player.statPoints += STAT_POINTS_PER_LEVEL
            levelsGained++
            leveledUp = true

            // Recalculate stats based on new level
            recalculateStats(player)

            // Full heal on level-up
            player.hp = player.maxHp
            player.mp = player.maxMp

            logger.info("Player {} leveled up to {} (class={})", player.name, player.level, player.characterClass)

            xpNeeded = xpRequiredForLevel(player.level + 1)
        }

        player.xpToNextLevel = if (player.level >= MAX_LEVEL) 0L else xpRequiredForLevel(player.level + 1)

        return XpResult(
            xpGained = amount.toLong(),
            totalXp = player.xp,
            xpToNextLevel = player.xpToNextLevel,
            currentLevel = player.level,
            leveledUp = leveledUp,
            levelsGained = levelsGained
        )
    }

    /**
     * Recalculate max HP/MP based on class and level.
     * Base values increase per level; class determines scaling.
     */
    private fun recalculateStats(player: PlayerEntity) {
        // Base stats + level scaling (simple formula, can be expanded later)
        val baseHp = when (player.characterClass) {
            0 -> 120  // Warrior — tankiest
            1 -> 80   // Mage — lowest HP
            2 -> 90   // Assassin
            3 -> 100  // Cleric
            else -> 100
        }
        val baseMp = when (player.characterClass) {
            0 -> 30   // Warrior — lowest MP
            1 -> 80   // Mage — highest MP
            2 -> 50   // Assassin
            3 -> 70   // Cleric
            else -> 50
        }
        player.maxHp = baseHp + (player.level * 15) + (player.sta * 5)
        player.maxMp = baseMp + (player.level * 8) + (player.int * 3)
    }
}
```

**Step 4: Run tests — expect PASS**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.combat.XpSystemTest" --no-daemon`

**Step 5: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/combat/XpSystem.kt \
        server/world-service/src/test/kotlin/com/flyagain/world/combat/XpSystemTest.kt
git commit -m "feat: add XP system with level-up, stat recalculation, and tests"
```

---

## Task 2: Server — Loot System

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/combat/LootSystem.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/combat/LootSystemTest.kt`

**Step 1: Write LootSystem tests**

```kotlin
// LootSystemTest.kt
package com.flyagain.world.combat

import com.flyagain.common.grpc.Internal
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Random

class LootSystemTest {

    private lateinit var lootSystem: LootSystem

    @BeforeEach
    fun setup() {
        lootSystem = LootSystem()
    }

    @Test
    fun `rollLoot returns empty when no loot table for monster`() {
        val drops = lootSystem.rollLoot(999)
        assertTrue(drops.isEmpty())
    }

    @Test
    fun `rollLoot returns items when drop chance is 1_0`() {
        val entry = buildLootEntry(monsterId = 1, itemId = 10, dropChance = 1.0f, minAmount = 1, maxAmount = 1)
        lootSystem.loadLootTables(listOf(entry))
        val drops = lootSystem.rollLoot(1)
        assertEquals(1, drops.size)
        assertEquals(10, drops[0].itemId)
    }

    @Test
    fun `rollLoot returns nothing when drop chance is 0`() {
        val entry = buildLootEntry(monsterId = 1, itemId = 10, dropChance = 0.0f, minAmount = 1, maxAmount = 1)
        lootSystem.loadLootTables(listOf(entry))
        val drops = lootSystem.rollLoot(1)
        assertTrue(drops.isEmpty())
    }

    @Test
    fun `rollLoot respects amount range`() {
        val entry = buildLootEntry(monsterId = 1, itemId = 10, dropChance = 1.0f, minAmount = 2, maxAmount = 5)
        lootSystem.loadLootTables(listOf(entry))
        val drops = lootSystem.rollLoot(1)
        assertEquals(1, drops.size)
        assertTrue(drops[0].amount in 2..5)
    }

    @Test
    fun `rollLoot handles multiple entries per monster`() {
        val entries = listOf(
            buildLootEntry(monsterId = 1, itemId = 10, dropChance = 1.0f, minAmount = 1, maxAmount = 1),
            buildLootEntry(monsterId = 1, itemId = 20, dropChance = 1.0f, minAmount = 1, maxAmount = 1)
        )
        lootSystem.loadLootTables(entries)
        val drops = lootSystem.rollLoot(1)
        assertEquals(2, drops.size)
    }

    @Test
    fun `gold drop scales with monster level`() {
        val gold = lootSystem.calculateGoldDrop(level = 10)
        assertTrue(gold > 0)
        val goldLow = lootSystem.calculateGoldDrop(level = 1)
        assertTrue(gold > goldLow, "Higher level should give more gold")
    }

    private fun buildLootEntry(
        monsterId: Int, itemId: Int, dropChance: Float,
        minAmount: Int, maxAmount: Int
    ): Internal.LootTableRecord {
        return Internal.LootTableRecord.newBuilder()
            .setMonsterId(monsterId)
            .setItemId(itemId)
            .setDropChance(dropChance)
            .setMinAmount(minAmount)
            .setMaxAmount(maxAmount)
            .build()
    }
}
```

**Step 2: Run tests — expect FAIL**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.combat.LootSystemTest" --no-daemon`

**Step 3: Implement LootSystem**

```kotlin
// LootSystem.kt
package com.flyagain.world.combat

import com.flyagain.common.grpc.Internal
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadLocalRandom

class LootSystem {

    private val logger = LoggerFactory.getLogger(LootSystem::class.java)

    data class LootDrop(val itemId: Int, val amount: Int)

    // monsterId -> list of loot entries
    private val lootTables = HashMap<Int, MutableList<LootEntry>>()

    private data class LootEntry(
        val itemId: Int,
        val dropChance: Float,
        val minAmount: Int,
        val maxAmount: Int
    )

    fun loadLootTables(entries: List<Internal.LootTableRecord>) {
        lootTables.clear()
        for (entry in entries) {
            lootTables.getOrPut(entry.monsterId) { mutableListOf() }.add(
                LootEntry(entry.itemId, entry.dropChance, entry.minAmount, entry.maxAmount)
            )
        }
        logger.info("Loaded loot tables for {} monsters ({} entries total)",
            lootTables.size, entries.size)
    }

    /**
     * Roll loot for a killed monster. Each entry is rolled independently.
     */
    fun rollLoot(monsterDefinitionId: Int): List<LootDrop> {
        val entries = lootTables[monsterDefinitionId] ?: return emptyList()
        val rng = ThreadLocalRandom.current()
        val drops = mutableListOf<LootDrop>()

        for (entry in entries) {
            if (rng.nextFloat() < entry.dropChance) {
                val amount = if (entry.minAmount == entry.maxAmount) {
                    entry.minAmount
                } else {
                    rng.nextInt(entry.minAmount, entry.maxAmount + 1)
                }
                drops.add(LootDrop(entry.itemId, amount))
            }
        }
        return drops
    }

    /**
     * Calculate gold drop based on monster level.
     * Formula: level * 3 + random(0, level * 2)
     */
    fun calculateGoldDrop(level: Int): Int {
        val rng = ThreadLocalRandom.current()
        val base = level * 3
        val bonus = if (level > 0) rng.nextInt(0, level * 2 + 1) else 0
        return base + bonus
    }
}
```

**Step 4: Run tests — expect PASS**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.combat.LootSystemTest" --no-daemon`

**Step 5: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/combat/LootSystem.kt \
        server/world-service/src/test/kotlin/com/flyagain/world/combat/LootSystemTest.kt
git commit -m "feat: add loot system with drop chance rolling and gold calculation"
```

---

## Task 3: Server — Death Resolution Handler

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/combat/DeathHandler.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/combat/DeathHandlerTest.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/network/BroadcastService.kt` (add broadcastXpGain, broadcastEntityStatsUpdate)
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/gameloop/GameLoop.kt` (integrate death handling)
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/di/WorldServiceModule.kt` (register new services)
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/WorldServiceMain.kt` (load loot tables at startup)

**Step 1: Add broadcast methods to BroadcastService**

Add to `BroadcastService.kt` after `broadcastEntityDeath()`:

```kotlin
/**
 * Send XP gain notification to a specific player.
 */
fun sendXpGain(player: PlayerEntity, xpResult: XpSystem.XpResult) {
    val msg = XpGainMessage.newBuilder()
        .setXpGained(xpResult.xpGained)
        .setTotalXp(xpResult.totalXp)
        .setXpToNextLevel(xpResult.xpToNextLevel)
        .setCurrentLevel(xpResult.currentLevel)
        .setLeveledUp(xpResult.leveledUp)
        .build()
    val packet = Packet(Opcode.XP_GAIN_VALUE, msg.toByteArray())
    sendToPlayer(player, packet)
}

/**
 * Broadcast stats update to all nearby players (after level-up).
 */
fun broadcastEntityStatsUpdate(channel: ZoneChannel, player: PlayerEntity) {
    val msg = EntityStatsUpdate.newBuilder()
        .setEntityId(player.entityId)
        .setLevel(player.level)
        .setHp(player.hp)
        .setMaxHp(player.maxHp)
        .setMp(player.mp)
        .setMaxMp(player.maxMp)
        .setStr(player.str)
        .setSta(player.sta)
        .setDex(player.dex)
        .setInt(player.int)
        .build()
    val packet = Packet(Opcode.ENTITY_STATS_UPDATE_VALUE, msg.toByteArray())
    sendToNearby(channel, player.x, player.z, -1, packet)
}

/**
 * Send gold update to a specific player.
 */
fun sendGoldUpdate(player: PlayerEntity) {
    val msg = GoldUpdate.newBuilder()
        .setGold(player.gold)
        .build()
    val packet = Packet(Opcode.GOLD_UPDATE_VALUE, msg.toByteArray())
    sendToPlayer(player, packet)
}
```

Also change `sendToNearby` from `private` to `internal` so `broadcastEntityStatsUpdate` can use it (or move broadcast methods accordingly — they already call sendToNearby which is private; the new methods should work since they're in the same class).

**Step 2: Write DeathHandler tests**

```kotlin
// DeathHandlerTest.kt
package com.flyagain.world.combat

import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeathHandlerTest {

    private lateinit var xpSystem: XpSystem
    private lateinit var lootSystem: LootSystem
    private lateinit var broadcastService: BroadcastService
    private lateinit var entityManager: EntityManager
    private lateinit var deathHandler: DeathHandler

    @BeforeEach
    fun setup() {
        xpSystem = XpSystem()
        lootSystem = LootSystem()
        broadcastService = mockk(relaxed = true)
        entityManager = mockk(relaxed = true)
        deathHandler = DeathHandler(xpSystem, lootSystem, broadcastService, entityManager)
    }

    @Test
    fun `handleMonsterDeath transitions monster to DEAD state`() {
        val monster = createMonster()
        val player = createPlayer()
        val channel = mockk<ZoneChannel>(relaxed = true)

        deathHandler.handleMonsterDeath(monster, player, channel)

        assertEquals(AIState.DEAD, monster.aiState)
        assertTrue(monster.deathTime > 0)
    }

    @Test
    fun `handleMonsterDeath awards XP to killer`() {
        val monster = createMonster(xpReward = 50)
        val player = createPlayer(level = 1, xp = 0)
        val channel = mockk<ZoneChannel>(relaxed = true)

        deathHandler.handleMonsterDeath(monster, player, channel)

        assertEquals(50L, player.xp)
        verify { broadcastService.sendXpGain(player, any()) }
    }

    @Test
    fun `handleMonsterDeath awards gold to killer`() {
        val monster = createMonster(level = 5)
        val player = createPlayer(gold = 100)
        val channel = mockk<ZoneChannel>(relaxed = true)

        deathHandler.handleMonsterDeath(monster, player, channel)

        assertTrue(player.gold > 100, "Player should receive gold")
    }

    @Test
    fun `handleMonsterDeath clears monster target`() {
        val monster = createMonster()
        monster.targetEntityId = 1L
        val player = createPlayer()
        val channel = mockk<ZoneChannel>(relaxed = true)

        deathHandler.handleMonsterDeath(monster, player, channel)

        assertNull(monster.targetEntityId)
    }

    @Test
    fun `handleMonsterDeath stops player auto-attack on target`() {
        val monster = createMonster()
        val player = createPlayer()
        player.targetEntityId = monster.entityId
        player.autoAttacking = true
        val channel = mockk<ZoneChannel>(relaxed = true)

        deathHandler.handleMonsterDeath(monster, player, channel)

        assertFalse(player.autoAttacking)
    }

    @Test
    fun `handlePlayerDeath respawns player at Aerheim`() {
        val player = createPlayer(hp = 0)
        val channel = mockk<ZoneChannel>(relaxed = true)

        deathHandler.handlePlayerDeath(player, channel)

        assertTrue(player.hp > 0, "Player should be healed")
        assertEquals(player.maxHp, player.hp)
    }

    private fun createMonster(
        xpReward: Int = 15, level: Int = 2
    ): MonsterEntity {
        return MonsterEntity(
            entityId = 1_000_001L, definitionId = 1, name = "Slime",
            x = 500f, y = 0f, z = 500f, spawnX = 500f, spawnY = 0f, spawnZ = 500f,
            hp = 0, maxHp = 50, attack = 8, defense = 3, level = level,
            xpReward = xpReward, aggroRange = 5f, attackRange = 2f,
            attackSpeedMs = 2500, moveSpeed = 3f
        )
    }

    private fun createPlayer(
        level: Int = 1, xp: Long = 0L, gold: Long = 0L, hp: Int = 100
    ): PlayerEntity {
        return PlayerEntity(
            entityId = 1L, characterId = "char-1", accountId = "acc-1",
            name = "TestPlayer", characterClass = 0,
            x = 500f, y = 0f, z = 500f,
            level = level, xp = xp, gold = gold, hp = hp, maxHp = 100
        )
    }
}
```

**Step 3: Run tests — expect FAIL**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.combat.DeathHandlerTest" --no-daemon`

**Step 4: Implement DeathHandler**

```kotlin
// DeathHandler.kt
package com.flyagain.world.combat

import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import org.slf4j.LoggerFactory

class DeathHandler(
    private val xpSystem: XpSystem,
    private val lootSystem: LootSystem,
    private val broadcastService: BroadcastService,
    private val entityManager: EntityManager
) {

    private val logger = LoggerFactory.getLogger(DeathHandler::class.java)

    /**
     * Handle monster death: transition to DEAD, award XP, roll loot, award gold.
     */
    fun handleMonsterDeath(monster: MonsterEntity, killer: PlayerEntity, channel: ZoneChannel) {
        // 1. Transition monster to DEAD state
        monster.aiState = AIState.DEAD
        monster.deathTime = System.currentTimeMillis()
        monster.targetEntityId = null
        monster.hp = 0

        // 2. Stop auto-attack if killer was targeting this monster
        if (killer.targetEntityId == monster.entityId) {
            killer.autoAttacking = false
        }

        // 3. Award XP
        val xpResult = xpSystem.awardXp(killer, monster.xpReward)
        broadcastService.sendXpGain(killer, xpResult)
        logger.debug("Player {} gained {} XP from killing {} (level now {})",
            killer.name, monster.xpReward, monster.name, xpResult.currentLevel)

        // 4. Broadcast stats update on level-up
        if (xpResult.leveledUp) {
            broadcastService.broadcastEntityStatsUpdate(channel, killer)
            logger.info("Player {} leveled up to {} (+{} levels)",
                killer.name, xpResult.currentLevel, xpResult.levelsGained)
        }

        // 5. Award gold
        val gold = lootSystem.calculateGoldDrop(monster.level)
        if (gold > 0) {
            killer.gold += gold
            killer.markDirty()
            broadcastService.sendGoldUpdate(killer)
        }

        // 6. Roll and award loot (direct to inventory — MVP)
        val drops = lootSystem.rollLoot(monster.definitionId)
        for (drop in drops) {
            logger.debug("Loot drop: itemId={} amount={} for player {}",
                drop.itemId, drop.amount, killer.name)
            // TODO Phase 1.6: Add items to player inventory via InventoryManager
        }
    }

    /**
     * Handle player death: respawn at Aerheim with full HP/MP.
     * No item/XP loss in MVP.
     */
    fun handlePlayerDeath(player: PlayerEntity, channel: ZoneChannel) {
        logger.info("Player {} died, respawning at Aerheim", player.name)

        // Full heal
        player.hp = player.maxHp
        player.mp = player.maxMp

        // Stop combat
        player.autoAttacking = false
        player.targetEntityId = null

        player.markDirty()

        // TODO: Zone change to Aerheim if not already there
        // For now, just heal in place (full zone-change respawn requires more work)
    }
}
```

**Step 5: Run tests — expect PASS**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.combat.DeathHandlerTest" --no-daemon`

**Step 6: Integrate into GameLoop**

Modify `GameLoop.kt`:
- Add `deathHandler: DeathHandler` to constructor parameters
- In `processAutoAttacks()`: after `broadcastService.broadcastDamageEvent(channel, damageResult)`, check `damageResult.targetKilled` and call death handler
- In `updateMonsterAI()`: same check for monster-vs-player kills

Modify `WorldServiceModule.kt`:
- Register `XpSystem`, `LootSystem`, `DeathHandler` as singletons
- Add `deathHandler` to `GameLoop` constructor

Modify `WorldServiceMain.kt`:
- Load loot tables at startup: `lootSystem.loadLootTables(gameDataStub.getAllLootTables(...).entriesList)`

**Step 7: Run all world-service tests**

Run: `cd server && ./gradlew :world-service:test --no-daemon`

**Step 8: Commit**

```bash
git add -A server/world-service/
git commit -m "feat: add death handler with XP/gold/loot integration into game loop"
```

---

## Task 4: Server — DB Seed Data (Items + Loot Tables)

**Files:**
- Create: `server/database-service/src/main/resources/db/migration/V11__seed_items_and_loot_tables.sql`

**Step 1: Write migration**

```sql
-- V11__seed_items_and_loot_tables.sql
-- Seed item definitions and loot tables for Green Plains monsters

-- ============================================================
-- Item Definitions
-- ============================================================

-- Weapons (type=0)
INSERT INTO item_definitions (name, type, subtype, level_req, class_req, rarity, base_attack, base_defense, buy_price, sell_price, description)
VALUES
    ('Wooden Sword',  0, 0, 1,  0, 0, 5,  0, 10,   5,   'A basic wooden training sword.'),
    ('Iron Sword',    0, 0, 5,  0, 1, 12, 0, 100,  50,  'A sturdy iron blade.'),
    ('Steel Sword',   0, 0, 10, 0, 2, 22, 0, 500,  250, 'A finely crafted steel sword.');

-- Armor (type=1)
INSERT INTO item_definitions (name, type, subtype, level_req, class_req, rarity, base_attack, base_defense, buy_price, sell_price, description)
VALUES
    ('Leather Armor',  1, 0, 1,  NULL, 0, 0, 3,  15,   7,   'Simple leather protection.'),
    ('Chain Armor',    1, 0, 5,  NULL, 1, 0, 8,  120,  60,  'Interlocking metal rings.'),
    ('Plate Armor',    1, 0, 10, NULL, 2, 0, 15, 600,  300, 'Heavy plate armor.');

-- Consumables (type=3, stackable)
INSERT INTO item_definitions (name, type, subtype, level_req, class_req, rarity, base_hp, base_mp, buy_price, sell_price, stackable, max_stack, description)
VALUES
    ('Health Potion',  3, 0, 1, NULL, 0, 50, 0,  5,  2,  TRUE, 20, 'Restores 50 HP.'),
    ('Mana Potion',    3, 0, 1, NULL, 0, 0,  30, 8,  3,  TRUE, 20, 'Restores 30 MP.');

-- ============================================================
-- Loot Tables (monster_id references monster_definitions from V10)
-- ============================================================

-- Slime (id=1): Health Potion (30%), Leather Armor (5%)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES
    (1, 7, 0.30, 1, 2),   -- Health Potion
    (1, 4, 0.05, 1, 1);   -- Leather Armor

-- Forest Mushroom (id=2): Mana Potion (25%), Health Potion (20%)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES
    (2, 8, 0.25, 1, 2),   -- Mana Potion
    (2, 7, 0.20, 1, 1);   -- Health Potion

-- Wild Boar (id=3): Wooden Sword (15%), Leather Armor (10%), Health Potion (25%)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES
    (3, 1, 0.15, 1, 1),   -- Wooden Sword
    (3, 4, 0.10, 1, 1),   -- Leather Armor
    (3, 7, 0.25, 1, 2);   -- Health Potion

-- Forest Wolf (id=4): Iron Sword (10%), Chain Armor (8%), Health Potion (30%)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES
    (4, 2, 0.10, 1, 1),   -- Iron Sword
    (4, 5, 0.08, 1, 1),   -- Chain Armor
    (4, 7, 0.30, 1, 3);   -- Health Potion

-- Stone Golem (id=5): Steel Sword (15%), Plate Armor (10%), Iron Sword (20%), Health/Mana Potion (40%)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES
    (5, 3, 0.15, 1, 1),   -- Steel Sword
    (5, 6, 0.10, 1, 1),   -- Plate Armor
    (5, 2, 0.20, 1, 1),   -- Iron Sword
    (5, 7, 0.40, 2, 5),   -- Health Potion
    (5, 8, 0.40, 2, 3);   -- Mana Potion
```

**Step 2: Verify migration runs**

Run: `cd server && ./gradlew :database-service:build --no-daemon`

**Step 3: Commit**

```bash
git add server/database-service/src/main/resources/db/migration/V11__seed_items_and_loot_tables.sql
git commit -m "feat: add V11 migration with item definitions and loot tables for Green Plains"
```

---

## Task 5: Client — Proto Encoder/Decoder for Combat Messages

**Files:**
- Modify: `client/scripts/proto/ProtoEncoder.gd` (add combat encoders)
- Modify: `client/scripts/proto/ProtoDecoder.gd` (add combat decoders)

**Step 1: Add combat encoders to ProtoEncoder.gd**

Add at the end of ProtoEncoder.gd:

```gdscript
## ---- Combat Messages ----

## Encodes a SelectTargetRequest { int64 target_entity_id = 1 }
static func encode_select_target_request(target_entity_id: int) -> PackedByteArray:
    var buf := PackedByteArray()
    buf.append_array(_int64_field(1, target_entity_id))
    return buf


## Encodes a ToggleAutoAttackRequest { bool enable = 1; int64 target_entity_id = 2 }
static func encode_toggle_auto_attack(enable: bool, target_entity_id: int) -> PackedByteArray:
    var buf := PackedByteArray()
    buf.append_array(_bool_field(1, enable))
    buf.append_array(_int64_field(2, target_entity_id))
    return buf


## Encodes a UseSkillRequest { int32 skill_id = 1; int64 target_entity_id = 2 }
static func encode_use_skill_request(skill_id: int, target_entity_id: int) -> PackedByteArray:
    var buf := PackedByteArray()
    buf.append_array(_int32_field(1, skill_id))
    buf.append_array(_int64_field(2, target_entity_id))
    return buf
```

**Step 2: Add combat decoders to ProtoDecoder.gd**

Add at the end of ProtoDecoder.gd (before the helper methods section):

```gdscript
## ---- Combat Message Decoders ----

## Decodes SelectTargetResponse.
func decode_select_target_response() -> Dictionary:
    var result := {
        "success": false,
        "target_entity_id": 0,
        "target_hp": 0,
        "target_max_hp": 0,
        "target_name": "",
        "target_level": 0,
    }
    while _has_bytes():
        var tag := _next_tag()
        if tag.is_empty(): break
        match tag[0]:
            1: result["success"] = _read_varint() != 0
            2: result["target_entity_id"] = _read_varint()
            3: result["target_hp"] = _read_varint()
            4: result["target_max_hp"] = _read_varint()
            5: result["target_name"] = _read_string()
            6: result["target_level"] = _read_varint()
            _: _skip(tag[1])
    return result


## Decodes DamageEvent.
func decode_damage_event() -> Dictionary:
    var result := {
        "attacker_entity_id": 0,
        "target_entity_id": 0,
        "damage": 0,
        "is_critical": false,
        "target_current_hp": 0,
    }
    while _has_bytes():
        var tag := _next_tag()
        if tag.is_empty(): break
        match tag[0]:
            1: result["attacker_entity_id"] = _read_varint()
            2: result["target_entity_id"] = _read_varint()
            3: result["damage"] = _read_varint()
            4: result["is_critical"] = _read_varint() != 0
            5: result["target_current_hp"] = _read_varint()
            _: _skip(tag[1])
    return result


## Decodes EntityDeath.
func decode_entity_death() -> Dictionary:
    var result := {
        "entity_id": 0,
        "killer_entity_id": 0,
    }
    while _has_bytes():
        var tag := _next_tag()
        if tag.is_empty(): break
        match tag[0]:
            1: result["entity_id"] = _read_varint()
            2: result["killer_entity_id"] = _read_varint()
            _: _skip(tag[1])
    return result


## Decodes XpGainMessage.
func decode_xp_gain() -> Dictionary:
    var result := {
        "xp_gained": 0,
        "total_xp": 0,
        "xp_to_next_level": 0,
        "current_level": 0,
        "leveled_up": false,
    }
    while _has_bytes():
        var tag := _next_tag()
        if tag.is_empty(): break
        match tag[0]:
            1: result["xp_gained"] = _read_varint()
            2: result["total_xp"] = _read_varint()
            3: result["xp_to_next_level"] = _read_varint()
            4: result["current_level"] = _read_varint()
            5: result["leveled_up"] = _read_varint() != 0
            _: _skip(tag[1])
    return result


## Decodes ToggleAutoAttackResponse.
func decode_toggle_auto_attack_response() -> Dictionary:
    var result := {
        "auto_attacking": false,
        "target_entity_id": 0,
    }
    while _has_bytes():
        var tag := _next_tag()
        if tag.is_empty(): break
        match tag[0]:
            1: result["auto_attacking"] = _read_varint() != 0
            2: result["target_entity_id"] = _read_varint()
            _: _skip(tag[1])
    return result


## Decodes EntityStatsUpdate.
func decode_entity_stats_update() -> Dictionary:
    var result := {
        "entity_id": 0,
        "level": 0,
        "hp": 0,
        "max_hp": 0,
        "mp": 0,
        "max_mp": 0,
        "str": 0,
        "sta": 0,
        "dex": 0,
        "int_": 0,
    }
    while _has_bytes():
        var tag := _next_tag()
        if tag.is_empty(): break
        match tag[0]:
            1:  result["entity_id"] = _read_varint()
            2:  result["level"] = _read_varint()
            3:  result["hp"] = _read_varint()
            4:  result["max_hp"] = _read_varint()
            5:  result["mp"] = _read_varint()
            6:  result["max_mp"] = _read_varint()
            7:  result["str"] = _read_varint()
            8:  result["sta"] = _read_varint()
            9:  result["dex"] = _read_varint()
            10: result["int_"] = _read_varint()
            _: _skip(tag[1])
    return result
```

**Step 3: Commit**

```bash
git add client/scripts/proto/ProtoEncoder.gd client/scripts/proto/ProtoDecoder.gd
git commit -m "feat: add proto encoder/decoder for all combat messages"
```

---

## Task 6: Client — NetworkManager Combat Signals + Dispatch

**Files:**
- Modify: `client/autoloads/NetworkManager.gd` (add combat signals, dispatch handlers, send methods)
- Modify: `client/autoloads/GameState.gd` (add target tracking state)
- Modify: `client/scripts/network/PacketProtocol.gd` (add missing combat opcode constants if needed)

**Step 1: Add combat signals and state to GameState.gd**

Add after line 48 (`var player_gold: int = 0`):

```gdscript
var player_xp_to_next_level: int = 100

# ---- Target state ----
var selected_target_id: int = 0
var selected_target_name: String = ""
var selected_target_level: int = 0
var selected_target_hp: int = 0
var selected_target_max_hp: int = 0
var auto_attack_active: bool = false
```

Add resets in `reset()` method:

```gdscript
player_xp_to_next_level = 100
selected_target_id     = 0
selected_target_name   = ""
selected_target_level  = 0
selected_target_hp     = 0
selected_target_max_hp = 0
auto_attack_active     = false
```

**Step 2: Add combat signals + dispatch to NetworkManager.gd**

Add new signals after existing world-phase signals (~line 41):

```gdscript
signal select_target_response(data: Dictionary)
signal damage_event(data: Dictionary)
signal entity_death(data: Dictionary)
signal xp_gained(data: Dictionary)
signal auto_attack_response(data: Dictionary)
signal entity_stats_updated(data: Dictionary)
signal gold_updated(data: Dictionary)
```

Add dispatch cases in `_dispatch_world_frame()` before the `_:` default case:

```gdscript
PacketProtocol.OPCODE_SELECT_TARGET:
    var data := ProtoDecoder.new(payload).decode_select_target_response()
    print("[NET] SELECT_TARGET: success=%s target=%d name=%s" % [
        data.get("success", false), data.get("target_entity_id", 0), data.get("target_name", "")])
    select_target_response.emit(data)
PacketProtocol.OPCODE_DAMAGE_EVENT:
    var data := ProtoDecoder.new(payload).decode_damage_event()
    damage_event.emit(data)
PacketProtocol.OPCODE_ENTITY_DEATH:
    var data := ProtoDecoder.new(payload).decode_entity_death()
    print("[NET] ENTITY_DEATH: entity=%d killer=%d" % [
        data.get("entity_id", 0), data.get("killer_entity_id", 0)])
    entity_death.emit(data)
PacketProtocol.OPCODE_XP_GAIN:
    var data := ProtoDecoder.new(payload).decode_xp_gain()
    print("[NET] XP_GAIN: +%d xp (level=%d leveled_up=%s)" % [
        data.get("xp_gained", 0), data.get("current_level", 0), data.get("leveled_up", false)])
    xp_gained.emit(data)
PacketProtocol.OPCODE_TOGGLE_AUTO_ATTACK:
    var data := ProtoDecoder.new(payload).decode_toggle_auto_attack_response()
    auto_attack_response.emit(data)
PacketProtocol.OPCODE_ENTITY_STATS_UPDATE:
    var data := ProtoDecoder.new(payload).decode_entity_stats_update()
    entity_stats_updated.emit(data)
PacketProtocol.OPCODE_GOLD_UPDATE:
    var data := ProtoDecoder.new(payload).decode_gold_update()
    gold_updated.emit(data)
```

Add send methods after `send_movement_input()`:

```gdscript
func send_select_target(target_entity_id: int) -> void:
    var payload := ProtoEncoder.encode_select_target_request(target_entity_id)
    _send_world(PacketProtocol.OPCODE_SELECT_TARGET, payload)


func send_toggle_auto_attack(enable: bool, target_entity_id: int) -> void:
    var payload := ProtoEncoder.encode_toggle_auto_attack(enable, target_entity_id)
    _send_world(PacketProtocol.OPCODE_TOGGLE_AUTO_ATTACK, payload)


func send_use_skill(skill_id: int, target_entity_id: int) -> void:
    var payload := ProtoEncoder.encode_use_skill_request(skill_id, target_entity_id)
    _send_world(PacketProtocol.OPCODE_USE_SKILL, payload)
```

**Step 3: Add missing PacketProtocol opcode constants**

Check PacketProtocol.gd and add any missing opcodes:

```gdscript
# Combat (0x0201 - 0x0206)
const OPCODE_SELECT_TARGET      := 0x0201
const OPCODE_USE_SKILL          := 0x0202
const OPCODE_DAMAGE_EVENT       := 0x0203
const OPCODE_ENTITY_DEATH       := 0x0204
const OPCODE_XP_GAIN            := 0x0205
const OPCODE_TOGGLE_AUTO_ATTACK := 0x0206

# Entity (0x0301 - 0x0303)
const OPCODE_ENTITY_STATS_UPDATE := 0x0303

# Inventory (gold)
const OPCODE_GOLD_UPDATE := 0x0407
```

**Step 4: Commit**

```bash
git add client/autoloads/GameState.gd client/autoloads/NetworkManager.gd client/scripts/network/PacketProtocol.gd
git commit -m "feat: add combat network signals, dispatch handlers, and send methods"
```

---

## Task 7: Client — Tab-Targeting System

**Files:**
- Modify: `client/scenes/game/GameWorld.gd` (connect combat signals, handle targeting)
- Modify: `client/scenes/game/PlayerCharacter.gd` (click-to-target, Tab cycling)
- Modify: `client/scenes/game/RemoteEntity.gd` (selection highlight, HP update)
- Modify: `client/scripts/world/EntityFactory.gd` (nearby entity queries)

**Step 1: Add selection highlight to RemoteEntity.gd**

Add to RemoteEntity.gd:

```gdscript
var _selected: bool = false
var _selection_ring: MeshInstance3D = null

func set_selected(selected: bool) -> void:
    _selected = selected
    if _selection_ring:
        _selection_ring.visible = selected

func update_hp(new_hp: int) -> void:
    hp = new_hp
    if hp <= 0:
        set_selected(false)

func _ready() -> void:
    _update_name_label()
    _apply_appearance()
    _create_selection_ring()
    if _mesh:
        _mesh_base_y = _mesh.position.y
    if _name_label:
        _label_base_y = _name_label.position.y

func _create_selection_ring() -> void:
    _selection_ring = MeshInstance3D.new()
    var torus := TorusMesh.new()
    torus.inner_radius = 0.6
    torus.outer_radius = 0.8
    torus.rings = 16
    torus.ring_segments = 16
    _selection_ring.mesh = torus
    var mat := StandardMaterial3D.new()
    mat.albedo_color = Color(1.0, 0.85, 0.0, 0.7)
    mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    _selection_ring.material_override = mat
    _selection_ring.position.y = 0.05
    _selection_ring.visible = false
    add_child(_selection_ring)
```

**Step 2: Add entity query methods to EntityFactory.gd**

```gdscript
## Returns all monster entity IDs sorted by distance from player position.
func get_nearby_monsters(player_pos: Vector3) -> Array:
    var monsters := []
    for eid in _entities:
        var entity: RemoteEntity = _entities[eid]
        if entity.entity_type == WorldConstants.ENTITY_TYPE_MONSTER and entity.hp > 0:
            monsters.append({"entity_id": eid, "distance": player_pos.distance_to(entity.global_position)})
    monsters.sort_custom(func(a, b): return a["distance"] < b["distance"])
    return monsters


## Returns the RemoteEntity at the given screen position via raycast, or null.
func get_entity_at_screen_pos(camera: Camera3D, screen_pos: Vector2) -> RemoteEntity:
    var from := camera.project_ray_origin(screen_pos)
    var to := from + camera.project_ray_normal(screen_pos) * 200.0
    var space := camera.get_world_3d().direct_space_state
    var query := PhysicsRayQueryParameters3D.create(from, to)
    query.collision_mask = 0xFFFFFFFF
    var result := space.intersect_ray(query)
    if result.is_empty():
        return null
    var collider = result["collider"]
    if collider is RemoteEntity:
        return collider
    # Check parent (collision shape is child of RemoteEntity)
    if collider.get_parent() is RemoteEntity:
        return collider.get_parent()
    return null
```

**Step 3: Add targeting input to PlayerCharacter.gd**

Modify `_unhandled_input()` to add right-click targeting and Tab cycling:

```gdscript
func _unhandled_input(event: InputEvent) -> void:
    # Right-click: target entity
    if event is InputEventMouseButton \
            and event.button_index == MOUSE_BUTTON_RIGHT \
            and event.pressed:
        _try_target_entity(event.position)
        return

    # Left-click: click-to-move (existing behavior)
    if event is InputEventMouseButton \
            and event.button_index == MOUSE_BUTTON_LEFT \
            and event.pressed \
            and not _is_flying \
            and not _camera_pivot.is_rotating():
        _try_click_to_move(event.position)

    # Tab: cycle targets
    if event is InputEventKey and event.pressed and event.keycode == KEY_TAB:
        _cycle_target()

    # F: toggle auto-attack
    if event is InputEventKey and event.pressed and event.keycode == KEY_F:
        _toggle_auto_attack()

    # Escape: clear target
    if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
        _clear_target()
```

Add targeting helper methods to PlayerCharacter.gd:

```gdscript
signal target_selected(entity_id: int)
signal target_cleared()
signal auto_attack_toggled(enable: bool, target_id: int)

var _current_target_index: int = -1

func _try_target_entity(screen_pos: Vector2) -> void:
    var camera := _camera_pivot.get_camera()
    if not camera:
        return
    var entity_factory = get_parent().get_node_or_null("_entity_factory_ref")
    # GameWorld will handle the actual raycasting and selection
    # Emit signal for GameWorld to process
    var from := camera.project_ray_origin(screen_pos)
    var to := from + camera.project_ray_normal(screen_pos) * 200.0
    var space := get_world_3d().direct_space_state
    var query := PhysicsRayQueryParameters3D.create(from, to)
    query.collision_mask = 0xFFFFFFFF
    query.exclude = [get_rid()]
    var result := space.intersect_ray(query)
    if result.is_empty():
        return
    var collider = result["collider"]
    var entity: RemoteEntity = null
    if collider is RemoteEntity:
        entity = collider
    elif collider.get_parent() is RemoteEntity:
        entity = collider.get_parent()
    if entity and entity.hp > 0:
        target_selected.emit(entity.entity_id)


func _cycle_target() -> void:
    # GameWorld handles this via signal
    target_selected.emit(-1)  # -1 = cycle to next


func _toggle_auto_attack() -> void:
    auto_attack_toggled.emit(
        not GameState.auto_attack_active,
        GameState.selected_target_id
    )


func _clear_target() -> void:
    target_cleared.emit()
```

**Step 4: Connect signals in GameWorld.gd**

Add combat signal connections and handlers to GameWorld.gd:

```gdscript
# In _setup_signal_connections() or _ready():
NetworkManager.select_target_response.connect(_on_select_target_response)
NetworkManager.damage_event.connect(_on_damage_event)
NetworkManager.entity_death.connect(_on_entity_death)
NetworkManager.xp_gained.connect(_on_xp_gained)
NetworkManager.auto_attack_response.connect(_on_auto_attack_response)
NetworkManager.entity_stats_updated.connect(_on_entity_stats_updated)

# Player signals
_player.target_selected.connect(_on_player_target_selected)
_player.target_cleared.connect(_on_player_target_cleared)
_player.auto_attack_toggled.connect(_on_player_auto_attack_toggled)


func _on_player_target_selected(entity_id: int) -> void:
    if entity_id == -1:
        # Cycle to next monster
        var monsters := _entity_factory.get_nearby_monsters(_player.global_position)
        if monsters.is_empty():
            return
        _current_cycle_index = (_current_cycle_index + 1) % monsters.size()
        var target_eid: int = monsters[_current_cycle_index]["entity_id"]
        NetworkManager.send_select_target(target_eid)
    else:
        NetworkManager.send_select_target(entity_id)

var _current_cycle_index: int = -1

func _on_player_target_cleared() -> void:
    NetworkManager.send_select_target(0)
    _deselect_current_target()
    _current_cycle_index = -1

func _on_player_auto_attack_toggled(enable: bool, target_id: int) -> void:
    if target_id > 0:
        NetworkManager.send_toggle_auto_attack(enable, target_id)

func _on_select_target_response(data: Dictionary) -> void:
    _deselect_current_target()
    if data.get("success", false):
        var eid: int = data.get("target_entity_id", 0)
        GameState.selected_target_id = eid
        GameState.selected_target_name = data.get("target_name", "")
        GameState.selected_target_level = data.get("target_level", 0)
        GameState.selected_target_hp = data.get("target_hp", 0)
        GameState.selected_target_max_hp = data.get("target_max_hp", 0)
        # Highlight entity
        var entity := _entity_factory.get_entity(eid)
        if entity:
            entity.set_selected(true)
    else:
        GameState.selected_target_id = 0

func _deselect_current_target() -> void:
    if GameState.selected_target_id > 0:
        var old := _entity_factory.get_entity(GameState.selected_target_id)
        if old:
            old.set_selected(false)
    GameState.selected_target_id = 0
    GameState.selected_target_name = ""

func _on_damage_event(data: Dictionary) -> void:
    var target_eid: int = data.get("target_entity_id", 0)
    var target_hp: int = data.get("target_current_hp", 0)
    # Update entity HP
    var entity := _entity_factory.get_entity(target_eid)
    if entity:
        entity.update_hp(target_hp)
    # Update target frame if this is our target
    if target_eid == GameState.selected_target_id:
        GameState.selected_target_hp = target_hp
    # Spawn damage number (Task 9)
    _spawn_damage_number(data)

func _on_entity_death(data: Dictionary) -> void:
    var eid: int = data.get("entity_id", 0)
    # Clear target if our target died
    if eid == GameState.selected_target_id:
        _deselect_current_target()
        GameState.auto_attack_active = false

func _on_xp_gained(data: Dictionary) -> void:
    GameState.player_xp = data.get("total_xp", 0)
    GameState.player_xp_to_next_level = data.get("xp_to_next_level", 0)
    if data.get("leveled_up", false):
        GameState.player_level = data.get("current_level", 0)

func _on_auto_attack_response(data: Dictionary) -> void:
    GameState.auto_attack_active = data.get("auto_attacking", false)

func _on_entity_stats_updated(data: Dictionary) -> void:
    var eid: int = data.get("entity_id", 0)
    if eid == GameState.my_entity_id:
        GameState.player_level = data.get("level", GameState.player_level)
        GameState.player_hp = data.get("hp", GameState.player_hp)
        GameState.player_max_hp = data.get("max_hp", GameState.player_max_hp)
        GameState.player_mp = data.get("mp", GameState.player_mp)
        GameState.player_max_mp = data.get("max_mp", GameState.player_max_mp)
```

**Step 5: Commit**

```bash
git add client/scenes/game/RemoteEntity.gd client/scripts/world/EntityFactory.gd \
        client/scenes/game/PlayerCharacter.gd client/scenes/game/GameWorld.gd
git commit -m "feat: add tab-targeting with click, Tab cycle, F auto-attack, Escape clear"
```

---

## Task 8: Client — Target Frame UI

**Files:**
- Create: `client/scenes/ui/game_hud/TargetFrame.gd`
- Modify: `client/scenes/game/GameWorld.gd` (add target frame to HUD)

**Step 1: Create TargetFrame.gd**

```gdscript
## TargetFrame.gd
## Displays target name, level, and HP bar at top-center of screen.
extends PanelContainer

var _name_label: Label
var _level_label: Label
var _hp_bar: ProgressBar
var _hp_label: Label

func _ready() -> void:
    visible = false
    custom_minimum_size = Vector2(280, 60)
    _build_ui()

func _build_ui() -> void:
    var vbox := VBoxContainer.new()
    vbox.add_theme_constant_override("separation", 2)

    var top_row := HBoxContainer.new()
    _name_label = Label.new()
    _name_label.add_theme_font_size_override("font_size", 14)
    _name_label.add_theme_color_override("font_color", Color(1.0, 0.4, 0.4))
    _name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    top_row.add_child(_name_label)

    _level_label = Label.new()
    _level_label.add_theme_font_size_override("font_size", 12)
    _level_label.add_theme_color_override("font_color", Color(0.8, 0.8, 0.8))
    top_row.add_child(_level_label)

    vbox.add_child(top_row)

    var hp_row := HBoxContainer.new()
    _hp_bar = ProgressBar.new()
    _hp_bar.custom_minimum_size = Vector2(200, 18)
    _hp_bar.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    _hp_bar.show_percentage = false
    # Style the bar red
    var bar_style := StyleBoxFlat.new()
    bar_style.bg_color = Color(0.8, 0.15, 0.15)
    bar_style.corner_radius_top_left = 3
    bar_style.corner_radius_top_right = 3
    bar_style.corner_radius_bottom_left = 3
    bar_style.corner_radius_bottom_right = 3
    _hp_bar.add_theme_stylebox_override("fill", bar_style)
    var bar_bg := StyleBoxFlat.new()
    bar_bg.bg_color = Color(0.15, 0.15, 0.15)
    bar_bg.corner_radius_top_left = 3
    bar_bg.corner_radius_top_right = 3
    bar_bg.corner_radius_bottom_left = 3
    bar_bg.corner_radius_bottom_right = 3
    _hp_bar.add_theme_stylebox_override("background", bar_bg)
    hp_row.add_child(_hp_bar)

    _hp_label = Label.new()
    _hp_label.add_theme_font_size_override("font_size", 12)
    _hp_label.custom_minimum_size = Vector2(70, 0)
    _hp_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
    hp_row.add_child(_hp_label)

    vbox.add_child(hp_row)
    add_child(vbox)

    # Panel styling
    var panel_style := StyleBoxFlat.new()
    panel_style.bg_color = Color(0.05, 0.05, 0.1, 0.85)
    panel_style.border_color = Color(0.3, 0.3, 0.4)
    panel_style.border_width_bottom = 1
    panel_style.border_width_top = 1
    panel_style.border_width_left = 1
    panel_style.border_width_right = 1
    panel_style.content_margin_left = 10
    panel_style.content_margin_right = 10
    panel_style.content_margin_top = 6
    panel_style.content_margin_bottom = 6
    panel_style.corner_radius_top_left = 4
    panel_style.corner_radius_top_right = 4
    panel_style.corner_radius_bottom_left = 4
    panel_style.corner_radius_bottom_right = 4
    add_theme_stylebox_override("panel", panel_style)


func _process(_delta: float) -> void:
    if GameState.selected_target_id > 0:
        if not visible:
            visible = true
        _name_label.text = GameState.selected_target_name
        _level_label.text = "Lv.%d" % GameState.selected_target_level
        _hp_bar.max_value = GameState.selected_target_max_hp
        _hp_bar.value = GameState.selected_target_hp
        _hp_label.text = "%d / %d" % [GameState.selected_target_hp, GameState.selected_target_max_hp]
    else:
        if visible:
            visible = false
```

**Step 2: Add TargetFrame to GameWorld HUD**

In GameWorld.gd, where the HUD CanvasLayer is created, add:

```gdscript
# Target frame (top center)
var target_frame := preload("res://scenes/ui/game_hud/TargetFrame.gd").new()
var target_container := CenterContainer.new()
target_container.anchor_left = 0.0
target_container.anchor_right = 1.0
target_container.anchor_top = 0.0
target_container.offset_top = 10
target_container.size_flags_horizontal = Control.SIZE_EXPAND_FILL
target_container.add_child(target_frame)
hud_root.add_child(target_container)
```

**Step 3: Commit**

```bash
git add client/scenes/ui/game_hud/TargetFrame.gd client/scenes/game/GameWorld.gd
git commit -m "feat: add target frame UI with name, level, and HP bar"
```

---

## Task 9: Client — Floating Damage Numbers

**Files:**
- Create: `client/scenes/game/DamageNumber.gd`
- Modify: `client/scenes/game/GameWorld.gd` (spawn damage numbers on DamageEvent)

**Step 1: Create DamageNumber.gd**

```gdscript
## DamageNumber.gd
## Floating 3D text that shows damage dealt, animates upward and fades out.
extends Label3D

const FLOAT_SPEED := 2.0
const LIFETIME := 1.5
const SPREAD := 0.5

var _elapsed: float = 0.0
var _velocity: Vector3

func _ready() -> void:
    billboard = BaseMaterial3D.BILLBOARD_ENABLED
    no_depth_test = true
    font_size = 32
    outline_size = 4
    outline_modulate = Color(0, 0, 0, 0.8)
    # Random horizontal spread
    var rng_x := randf_range(-SPREAD, SPREAD)
    var rng_z := randf_range(-SPREAD, SPREAD)
    _velocity = Vector3(rng_x, FLOAT_SPEED, rng_z)


func setup(damage: int, is_critical: bool, is_self_damage: bool) -> void:
    text = str(damage)
    if is_critical:
        text = str(damage) + "!"
        modulate = Color(1.0, 0.9, 0.2)  # Yellow for crits
        font_size = 42
    elif is_self_damage:
        modulate = Color(1.0, 0.3, 0.3)  # Red for damage taken
    else:
        modulate = Color(1.0, 1.0, 1.0)  # White for normal


func _process(delta: float) -> void:
    _elapsed += delta
    if _elapsed >= LIFETIME:
        queue_free()
        return
    # Float upward
    position += _velocity * delta
    _velocity.y *= 0.97  # Slow down
    # Fade out in second half
    var fade_start := LIFETIME * 0.5
    if _elapsed > fade_start:
        var t := (_elapsed - fade_start) / (LIFETIME - fade_start)
        modulate.a = 1.0 - t
```

**Step 2: Add damage number spawning to GameWorld.gd**

```gdscript
func _spawn_damage_number(data: Dictionary) -> void:
    var target_eid: int = data.get("target_entity_id", 0)
    var damage: int = data.get("damage", 0)
    var is_critical: bool = data.get("is_critical", false)
    var is_self: bool = (target_eid == GameState.my_entity_id)

    # Find world position of target
    var world_pos: Vector3
    if target_eid == GameState.my_entity_id and _player:
        world_pos = _player.global_position + Vector3(0, 2.5, 0)
    else:
        var entity := _entity_factory.get_entity(target_eid)
        if entity:
            world_pos = entity.global_position + Vector3(0, 2.5, 0)
        else:
            return

    var dmg_num := DamageNumber.new()
    dmg_num.setup(damage, is_critical, is_self)
    dmg_num.position = world_pos
    add_child(dmg_num)
```

**Step 3: Commit**

```bash
git add client/scenes/game/DamageNumber.gd client/scenes/game/GameWorld.gd
git commit -m "feat: add floating damage numbers with crit highlighting and fade animation"
```

---

## Task 10: Client — Monster Visual Shapes

**Files:**
- Modify: `client/scenes/game/RemoteEntity.gd` (distinct shapes per monster name)

**Step 1: Update _apply_appearance() in RemoteEntity.gd**

Replace the monster case in `_apply_appearance()`:

```gdscript
WorldConstants.ENTITY_TYPE_MONSTER:
    var monster_mesh := _get_monster_mesh(entity_name)
    if monster_mesh:
        _mesh.mesh = monster_mesh["mesh"]
        mat.albedo_color = monster_mesh["color"]
        _mesh.scale = monster_mesh.get("scale", Vector3(1, 1, 1))
    else:
        mat.albedo_color = Color(0.8, 0.2, 0.2)
        _mesh.scale = Vector3(1.2, 1.2, 1.2)
```

Add monster mesh helper:

```gdscript
func _get_monster_mesh(name: String) -> Dictionary:
    match name:
        "Slime":
            var m := SphereMesh.new()
            m.radius = 0.5
            m.height = 1.0
            return {"mesh": m, "color": Color(0.3, 0.9, 0.3), "scale": Vector3(1.0, 0.7, 1.0)}
        "Forest Mushroom":
            var m := CylinderMesh.new()
            m.top_radius = 0.6
            m.bottom_radius = 0.2
            m.height = 1.2
            return {"mesh": m, "color": Color(0.6, 0.35, 0.2), "scale": Vector3(1, 1, 1)}
        "Wild Boar":
            var m := BoxMesh.new()
            m.size = Vector3(1.2, 0.8, 1.6)
            return {"mesh": m, "color": Color(0.5, 0.2, 0.15), "scale": Vector3(1, 1, 1)}
        "Forest Wolf":
            var m := PrismMesh.new()
            m.size = Vector3(0.8, 0.9, 1.4)
            return {"mesh": m, "color": Color(0.55, 0.55, 0.5), "scale": Vector3(1, 1, 1)}
        "Stone Golem":
            var m := BoxMesh.new()
            m.size = Vector3(1.5, 2.0, 1.5)
            return {"mesh": m, "color": Color(0.45, 0.42, 0.4), "scale": Vector3(1.3, 1.3, 1.3)}
    return {}
```

**Step 2: Commit**

```bash
git add client/scenes/game/RemoteEntity.gd
git commit -m "feat: add distinct monster visual shapes per type (Slime, Mushroom, Boar, Wolf, Golem)"
```

---

## Task 11: Server — Run Full Test Suite + Fix Issues

**Step 1: Run all server tests**

Run: `cd server && ./gradlew test --no-daemon`

Expected: All tests pass. Fix any compilation or test failures from the integration changes.

**Step 2: Verify Koin module test**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.di.WorldServiceModuleTest" --no-daemon`

The module test should verify all new dependencies resolve correctly.

**Step 3: Commit fixes if needed**

```bash
git add -A server/
git commit -m "fix: resolve test failures from combat system integration"
```

---

## Task 12: Final Integration Commit + Update Implementation Phases

**Step 1: Update docs/IMPLEMENTATION_PHASES.md**

Mark completed items in section 1.5:

- [x] DeathHandler
- [x] XP + Level-Up system
- [x] LootSystem (direct-to-inventory MVP)
- [x] Client: Tab-Targeting
- [x] Client: Target Frame UI
- [x] Client: Auto-Attack (F key)
- [x] Client: Damage Numbers

**Step 2: Final commit**

```bash
git add docs/IMPLEMENTATION_PHASES.md
git commit -m "docs: update Phase 1.5 progress — combat system server+client complete"
```
