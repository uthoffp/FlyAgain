# Phase 1.6: Inventory, Equipment & NPC Shops — Server Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement server-side inventory management, equipment system, and NPC shop handlers so players can move items, equip/unequip gear (with stat recalculation), and buy/sell from NPC merchants.

**Architecture:** TCP handlers in world-service validate requests and delegate to database-service via gRPC. Item definitions are cached in-memory at startup. Equipment changes trigger stat recalculation and broadcast. NPC proximity is validated server-side via SpatialGrid. All inventory mutations are atomic DB transactions.

**Tech Stack:** Kotlin, Netty (TCP), gRPC (protobuf), PostgreSQL (Flyway migrations), Koin DI, mockk (tests)

---

## File Structure

### New Files (world-service)
- `server/world-service/src/main/kotlin/com/flyagain/world/handler/MoveItemHandler.kt` — Handles MOVE_ITEM (0x0401)
- `server/world-service/src/main/kotlin/com/flyagain/world/handler/EquipItemHandler.kt` — Handles EQUIP_ITEM (0x0403) and UNEQUIP_ITEM (0x0404)
- `server/world-service/src/main/kotlin/com/flyagain/world/handler/NpcShopHandler.kt` — Handles NPC_BUY (0x0405) and NPC_SELL (0x0406)
- `server/world-service/src/main/kotlin/com/flyagain/world/inventory/ItemDefinitionCache.kt` — In-memory cache of item definitions loaded at startup
- `server/world-service/src/main/kotlin/com/flyagain/world/inventory/EquipmentStatCalculator.kt` — Computes stat bonuses from equipped items
- `server/world-service/src/main/kotlin/com/flyagain/world/inventory/NpcShopRegistry.kt` — NPC shop inventories (which items each NPC sells)
- `server/world-service/src/test/kotlin/com/flyagain/world/handler/MoveItemHandlerTest.kt`
- `server/world-service/src/test/kotlin/com/flyagain/world/handler/EquipItemHandlerTest.kt`
- `server/world-service/src/test/kotlin/com/flyagain/world/handler/NpcShopHandlerTest.kt`
- `server/world-service/src/test/kotlin/com/flyagain/world/inventory/ItemDefinitionCacheTest.kt`
- `server/world-service/src/test/kotlin/com/flyagain/world/inventory/EquipmentStatCalculatorTest.kt`
- `server/world-service/src/test/kotlin/com/flyagain/world/inventory/NpcShopRegistryTest.kt`

### New Files (database-service)
- `server/database-service/src/main/resources/db/migration/V12__create_npc_definitions.sql` — NPC definition + NPC shop tables + seed data

### Modified Files
- `shared/proto/flyagain.proto` — Add client-facing messages for inventory opcodes (MoveItemRequest/Response, EquipItemRequest/Response, UnequipItemRequest/Response, NpcBuyRequest/Response, NpcSellRequest/Response, InventoryUpdateMessage)
- `shared/proto/internal.proto` — Add NpcDefinition and NpcShopItem gRPC messages + GetAllNpcDefinitions + GetAllNpcShopItems RPCs to GameDataService
- `server/database-service/src/main/kotlin/com/flyagain/database/repository/InventoryRepository.kt` — Add `addItemStackable()` method
- `server/database-service/src/main/kotlin/com/flyagain/database/repository/InventoryRepositoryImpl.kt` — Implement `addItemStackable()`, implement `npcBuy()`, implement `npcSell()`
- `server/database-service/src/main/kotlin/com/flyagain/database/grpc/InventoryGrpcService.kt` — Implement `npcBuy()` and `npcSell()` (currently stubs)
- `server/database-service/src/main/kotlin/com/flyagain/database/repository/GameDataRepository.kt` — Add NPC data methods
- `server/database-service/src/main/kotlin/com/flyagain/database/repository/GameDataRepositoryImpl.kt` — Implement NPC data loading
- `server/database-service/src/main/kotlin/com/flyagain/database/grpc/GameDataGrpcService.kt` — Add NPC gRPC endpoints
- `server/database-service/src/main/kotlin/com/flyagain/database/di/DatabaseServiceModule.kt` — No changes needed (repos already wired)
- `server/world-service/src/main/kotlin/com/flyagain/world/di/WorldServiceModule.kt` — Add InventoryDataService stub, new handlers, ItemDefinitionCache, EquipmentStatCalculator, NpcShopRegistry
- `server/world-service/src/main/kotlin/com/flyagain/world/handler/PacketRouter.kt` — Route MOVE_ITEM, EQUIP_ITEM, UNEQUIP_ITEM, NPC_BUY, NPC_SELL opcodes
- `server/world-service/src/main/kotlin/com/flyagain/world/WorldServiceMain.kt` — Load item definitions and NPC data at startup
- `server/world-service/src/main/kotlin/com/flyagain/world/entity/PlayerEntity.kt` — Add equipment bonus fields (`bonusAttack`, `bonusDefense`, `bonusHp`, `bonusMp`), update `getAttackPower()` and `getDefense()`
- `server/world-service/src/main/kotlin/com/flyagain/world/network/BroadcastService.kt` — Add `sendInventoryUpdate()` method
- `server/world-service/src/main/kotlin/com/flyagain/world/combat/DeathHandler.kt` — Deliver loot items to inventory via gRPC (currently only logs)

---

### Task 1: Add Protobuf Messages for Inventory Operations

**Files:**
- Modify: `shared/proto/flyagain.proto:349-352`
- Modify: `shared/proto/internal.proto:296-304`

- [ ] **Step 1: Add client-facing inventory messages to flyagain.proto**

Add after the GoldUpdateMessage (line 352):

```protobuf
// Client -> Server: Move item between inventory slots
message MoveItemRequest {
    int32 from_slot = 1;
    int32 to_slot = 2;
}

// Server -> Client: Move item result
message MoveItemResponse {
    bool success = 1;
    string error_message = 2;
}

// Client -> Server: Equip item from inventory slot
message EquipItemRequest {
    int32 inventory_slot = 1;
    int32 equip_slot_type = 2;  // 0-6: head, chest, legs, feet, hands, back, weapon
}

// Server -> Client: Equip result
message EquipItemResponse {
    bool success = 1;
    string error_message = 2;
}

// Client -> Server: Unequip item from equipment slot
message UnequipItemRequest {
    int32 equip_slot_type = 1;
}

// Server -> Client: Unequip result
message UnequipItemResponse {
    bool success = 1;
    string error_message = 2;
}

// Client -> Server: Buy item from NPC
message NpcBuyRequest {
    int64 npc_entity_id = 1;
    int32 item_def_id = 2;
    int32 amount = 3;
}

// Server -> Client: Buy result
message NpcBuyResponse {
    bool success = 1;
    int64 new_gold = 2;
    int32 assigned_slot = 3;
    string error_message = 4;
}

// Client -> Server: Sell item to NPC
message NpcSellRequest {
    int64 npc_entity_id = 1;
    int32 inventory_slot = 2;
    int32 amount = 3;
}

// Server -> Client: Sell result
message NpcSellResponse {
    bool success = 1;
    int64 new_gold = 2;
    string error_message = 3;
}

// Server -> Client: Full inventory snapshot (sent on world entry and after changes)
message InventoryUpdateMessage {
    repeated InventorySlotInfo slots = 1;
    repeated EquipmentSlotInfo equipment = 2;
}

message InventorySlotInfo {
    int32 slot = 1;
    int32 item_id = 2;
    int32 amount = 3;
    int32 enhancement = 4;
}

message EquipmentSlotInfo {
    int32 slot_type = 1;
    int32 item_id = 2;
    int32 enhancement = 3;
}
```

Note: The client-facing proto uses simpler field names (no `character_id` — server derives it from the authenticated session). The internal gRPC proto already has the full messages with `character_id`.

- [ ] **Step 2: Add NPC definition messages to internal.proto**

Add NPC-related RPCs to the GameDataService and messages:

```protobuf
// In GameDataService (add two new RPCs):
    rpc GetAllNpcDefinitions(google.protobuf.Empty) returns (NpcDefinitionList);
    rpc GetAllNpcShopItems(google.protobuf.Empty) returns (NpcShopItemList);

// New messages:
message NpcDefinitionList {
    repeated NpcDefinitionRecord npcs = 1;
}

message NpcDefinitionRecord {
    int32 id = 1;
    string name = 2;
    int32 zone_id = 3;
    float pos_x = 4;
    float pos_y = 5;
    float pos_z = 6;
    int32 npc_type = 7;  // 0=shop, 1=quest, 2=enhancement
}

message NpcShopItemList {
    repeated NpcShopItemRecord items = 1;
}

message NpcShopItemRecord {
    int32 npc_id = 1;
    int32 item_def_id = 2;
}
```

- [ ] **Step 3: Rebuild protobuf generated code**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :common:generateProto`
Expected: BUILD SUCCESSFUL, new Kotlin classes generated

- [ ] **Step 4: Commit**

```bash
git add shared/proto/flyagain.proto shared/proto/internal.proto
git commit -m "feat: add protobuf messages for inventory, equipment, and NPC shop operations"
```

---

### Task 2: NPC Database Migration and Seed Data

**Files:**
- Create: `server/database-service/src/main/resources/db/migration/V12__create_npc_definitions.sql`

- [ ] **Step 1: Create migration V12 with NPC tables and Aerheim merchant seed data**

```sql
-- ============================================================
-- Phase 1.6: NPC definitions and shop inventories
-- ============================================================

-- NPC definitions (server-controlled reference data → SERIAL PK)
CREATE TABLE npc_definitions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    zone_id INT NOT NULL,
    pos_x REAL NOT NULL DEFAULT 0,
    pos_y REAL NOT NULL DEFAULT 0,
    pos_z REAL NOT NULL DEFAULT 0,
    npc_type INT NOT NULL DEFAULT 0,  -- 0=shop, 1=quest, 2=enhancement
    CONSTRAINT chk_npc_type CHECK (npc_type BETWEEN 0 AND 2)
);

-- NPC shop inventory (which items an NPC sells)
CREATE TABLE npc_shop_items (
    id SERIAL PRIMARY KEY,
    npc_id INT NOT NULL REFERENCES npc_definitions(id),
    item_def_id INT NOT NULL REFERENCES item_definitions(id),
    UNIQUE (npc_id, item_def_id)
);

CREATE INDEX idx_npc_shop_items_npc ON npc_shop_items(npc_id);

-- =========================
-- Seed: Aerheim Merchants
-- =========================

-- Weapon Merchant (near market square in Aerheim, zone_id=0)
INSERT INTO npc_definitions (name, zone_id, pos_x, pos_y, pos_z, npc_type)
VALUES ('Weapon Merchant', 0, 505.0, 0.0, 495.0, 0);

-- Armor Merchant
INSERT INTO npc_definitions (name, zone_id, pos_x, pos_y, pos_z, npc_type)
VALUES ('Armor Merchant', 0, 510.0, 0.0, 495.0, 0);

-- Potion Merchant
INSERT INTO npc_definitions (name, zone_id, pos_x, pos_y, pos_z, npc_type)
VALUES ('Potion Merchant', 0, 515.0, 0.0, 495.0, 0);

-- Weapon Merchant (npc_id=1) sells: Wooden Sword, Iron Sword, Steel Sword
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (1, 1);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (1, 2);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (1, 3);

-- Armor Merchant (npc_id=2) sells: Leather Armor, Chain Armor, Plate Armor
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (2, 4);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (2, 5);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (2, 6);

-- Potion Merchant (npc_id=3) sells: Health Potion, Mana Potion
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (3, 7);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (3, 8);
```

- [ ] **Step 2: Verify migration compiles**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :database-service:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add server/database-service/src/main/resources/db/migration/V12__create_npc_definitions.sql
git commit -m "feat: add V12 migration for NPC definitions and shop inventory tables"
```

---

### Task 3: GameData Repository — NPC Loading

**Files:**
- Modify: `server/database-service/src/main/kotlin/com/flyagain/database/repository/GameDataRepository.kt`
- Modify: `server/database-service/src/main/kotlin/com/flyagain/database/repository/GameDataRepositoryImpl.kt`
- Modify: `server/database-service/src/main/kotlin/com/flyagain/database/grpc/GameDataGrpcService.kt`

- [ ] **Step 1: Add NPC methods to GameDataRepository interface**

Add two new methods to the interface:

```kotlin
suspend fun getAllNpcDefinitions(): List<NpcDefinitionRecord>
suspend fun getAllNpcShopItems(): List<NpcShopItemRecord>
```

- [ ] **Step 2: Implement NPC data loading in GameDataRepositoryImpl**

Follow the existing pattern (e.g., `getAllMonsterDefinitions()`):

```kotlin
override suspend fun getAllNpcDefinitions(): List<NpcDefinitionRecord> = withConnection { conn ->
    conn.prepareStatement("SELECT id, name, zone_id, pos_x, pos_y, pos_z, npc_type FROM npc_definitions ORDER BY id").use { stmt ->
        stmt.executeQuery().use { rs ->
            val results = mutableListOf<NpcDefinitionRecord>()
            while (rs.next()) {
                results.add(
                    NpcDefinitionRecord.newBuilder()
                        .setId(rs.getInt("id"))
                        .setName(rs.getString("name"))
                        .setZoneId(rs.getInt("zone_id"))
                        .setPosX(rs.getFloat("pos_x"))
                        .setPosY(rs.getFloat("pos_y"))
                        .setPosZ(rs.getFloat("pos_z"))
                        .setNpcType(rs.getInt("npc_type"))
                        .build()
                )
            }
            results
        }
    }
}

override suspend fun getAllNpcShopItems(): List<NpcShopItemRecord> = withConnection { conn ->
    conn.prepareStatement("SELECT npc_id, item_def_id FROM npc_shop_items ORDER BY npc_id, item_def_id").use { stmt ->
        stmt.executeQuery().use { rs ->
            val results = mutableListOf<NpcShopItemRecord>()
            while (rs.next()) {
                results.add(
                    NpcShopItemRecord.newBuilder()
                        .setNpcId(rs.getInt("npc_id"))
                        .setItemDefId(rs.getInt("item_def_id"))
                        .build()
                )
            }
            results
        }
    }
}
```

- [ ] **Step 3: Add gRPC endpoints to GameDataGrpcService**

```kotlin
override suspend fun getAllNpcDefinitions(request: Empty): NpcDefinitionList {
    val npcs = gameDataRepo.getAllNpcDefinitions()
    return NpcDefinitionList.newBuilder().addAllNpcs(npcs).build()
}

override suspend fun getAllNpcShopItems(request: Empty): NpcShopItemList {
    val items = gameDataRepo.getAllNpcShopItems()
    return NpcShopItemList.newBuilder().addAllItems(items).build()
}
```

- [ ] **Step 4: Build and run tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :database-service:build`
Expected: BUILD SUCCESSFUL (existing Koin module test should still pass)

- [ ] **Step 5: Commit**

```bash
git add server/database-service/src/main/kotlin/com/flyagain/database/
git commit -m "feat: add NPC definition and shop item loading to GameDataService"
```

---

### Task 4: Inventory Repository — Stackable Items and NPC Buy/Sell

**Files:**
- Modify: `server/database-service/src/main/kotlin/com/flyagain/database/repository/InventoryRepository.kt`
- Modify: `server/database-service/src/main/kotlin/com/flyagain/database/repository/InventoryRepositoryImpl.kt`
- Modify: `server/database-service/src/main/kotlin/com/flyagain/database/grpc/InventoryGrpcService.kt`

- [ ] **Step 1: Add `addItemStackable()` to InventoryRepository interface**

```kotlin
/**
 * Adds an item to inventory, stacking onto existing stacks if the item is stackable
 * and an existing stack has room. Falls back to a new slot if no stack has room.
 *
 * @param characterId the character receiving the item
 * @param itemId the item definition ID
 * @param amount stack count to add
 * @param maxStack maximum stack size (from item definition; 1 if not stackable)
 * @return the slot index where the item was placed or stacked onto
 */
suspend fun addItemStackable(characterId: String, itemId: Int, amount: Int, maxStack: Int): Int
```

- [ ] **Step 2: Implement `addItemStackable()` in InventoryRepositoryImpl**

```kotlin
override suspend fun addItemStackable(characterId: String, itemId: Int, amount: Int, maxStack: Int): Int = withTransaction { conn ->
    val charUuid = UUID.fromString(characterId)

    if (maxStack > 1) {
        // Try to find an existing stack with room
        val existingSlot = conn.prepareStatement(
            "SELECT slot, amount FROM inventory WHERE character_id = ? AND item_id = ? AND amount < ? ORDER BY slot LIMIT 1"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, itemId)
            stmt.setInt(3, maxStack)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getInt("slot") to rs.getInt("amount") else null
            }
        }

        if (existingSlot != null) {
            val (slot, currentAmount) = existingSlot
            val newAmount = minOf(currentAmount + amount, maxStack)
            conn.prepareStatement(
                "UPDATE inventory SET amount = ? WHERE character_id = ? AND slot = ?"
            ).use { stmt ->
                stmt.setInt(1, newAmount)
                stmt.setObject(2, charUuid)
                stmt.setInt(3, slot)
                stmt.executeUpdate()
            }
            return@withTransaction slot
        }
    }

    // No existing stack — find first free slot
    val usedSlots = conn.prepareStatement(
        "SELECT slot FROM inventory WHERE character_id = ? ORDER BY slot"
    ).use { stmt ->
        stmt.setObject(1, charUuid)
        stmt.executeQuery().use { rs ->
            val slots = mutableSetOf<Int>()
            while (rs.next()) slots.add(rs.getInt("slot"))
            slots
        }
    }

    val freeSlot = (0 until 100).first { it !in usedSlots }

    conn.prepareStatement(
        "INSERT INTO inventory (character_id, slot, item_id, amount) VALUES (?, ?, ?, ?)"
    ).use { stmt ->
        stmt.setObject(1, charUuid)
        stmt.setInt(2, freeSlot)
        stmt.setInt(3, itemId)
        stmt.setInt(4, amount)
        stmt.executeUpdate()
    }
    freeSlot
}
```

- [ ] **Step 3: Implement npcBuy in InventoryGrpcService**

Replace the stub in `InventoryGrpcService.npcBuy()`:

```kotlin
override suspend fun npcBuy(request: NpcBuyRequest): NpcBuyResponse {
    return try {
        val characterId = request.characterId
        val itemDefId = request.itemDefId
        val amount = request.amount
        val currentGold = request.currentGold

        // Re-read gold from DB to prevent race conditions
        val dbGold = inventoryRepo.getGold(characterId)
        if (dbGold < currentGold) {
            // Client reported more gold than DB has — use DB value
        }

        // Gold check is done by the caller (world-service handler) against item price.
        // Here we just do the atomic: deduct gold + add item.
        val slot = inventoryRepo.addItem(characterId, itemDefId, amount)
        val newGold = dbGold - currentGold + dbGold  // Caller passes cost via currentGold field
        // Actually: the caller should pass the cost, not currentGold. Revisit protocol.
        // For now, world-service calculates new gold and we just update.
        inventoryRepo.updateGold(characterId, request.currentGold)

        NpcBuyResponse.newBuilder()
            .setSuccess(true)
            .setNewGold(request.currentGold)
            .setAssignedSlot(slot)
            .build()
    } catch (e: Exception) {
        logger.warn("npcBuy failed: {}", e.message)
        NpcBuyResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(e.message ?: "Purchase failed")
            .build()
    }
}
```

Actually, the cleaner approach: **world-service does all validation** (gold check, item price lookup, level/class check), calculates the new gold total, then calls gRPC to do the atomic DB operations. The gRPC `npcBuy` should be simple:

```kotlin
override suspend fun npcBuy(request: NpcBuyRequest): NpcBuyResponse {
    return try {
        // request.currentGold = new gold total after deduction (calculated by world-service)
        inventoryRepo.updateGold(request.characterId, request.currentGold)
        val slot = inventoryRepo.addItem(request.characterId, request.itemDefId, request.amount)
        NpcBuyResponse.newBuilder()
            .setSuccess(true)
            .setNewGold(request.currentGold)
            .setAssignedSlot(slot)
            .build()
    } catch (e: Exception) {
        logger.warn("npcBuy failed for character {}: {}", request.characterId, e.message)
        NpcBuyResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(e.message ?: "Purchase failed")
            .build()
    }
}
```

- [ ] **Step 4: Implement npcSell in InventoryGrpcService**

```kotlin
override suspend fun npcSell(request: NpcSellRequest): NpcSellResponse {
    return try {
        // request.amount is repurposed as the new gold total (calculated by world-service)
        // Actually, add a newGold field. For now, use the existing fields:
        // world-service calculates sell price, adds to gold, passes new total in amount field.
        // Better: use existing removeItem + updateGold separately.
        inventoryRepo.removeItem(request.characterId, request.inventorySlot, request.amount)
        // World-service handles gold update separately via updateGold
        NpcSellResponse.newBuilder()
            .setSuccess(true)
            .build()
    } catch (e: Exception) {
        logger.warn("npcSell failed for character {}: {}", request.characterId, e.message)
        NpcSellResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(e.message ?: "Sale failed")
            .build()
    }
}
```

**Simplification:** For NPC sell, world-service can just call `removeItem()` + `updateGold()` directly instead of using the `npcSell` RPC. For NPC buy, world-service calls `addItem()` + `updateGold()`. This avoids inventing new semantics for existing fields. The `npcBuy`/`npcSell` RPCs become convenience wrappers:

```kotlin
override suspend fun npcBuy(request: NpcBuyRequest): NpcBuyResponse {
    return try {
        // currentGold = new gold total after deduction
        inventoryRepo.updateGold(request.characterId, request.currentGold)
        val slot = inventoryRepo.addItem(request.characterId, request.itemDefId, request.amount)
        NpcBuyResponse.newBuilder()
            .setSuccess(true)
            .setNewGold(request.currentGold)
            .setAssignedSlot(slot)
            .build()
    } catch (e: Exception) {
        logger.warn("npcBuy failed: {}", e.message)
        NpcBuyResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(e.message ?: "Purchase failed")
            .build()
    }
}

override suspend fun npcSell(request: NpcSellRequest): NpcSellResponse {
    return try {
        inventoryRepo.removeItem(request.characterId, request.inventorySlot, request.amount)
        NpcSellResponse.newBuilder()
            .setSuccess(true)
            .build()
    } catch (e: Exception) {
        logger.warn("npcSell failed: {}", e.message)
        NpcSellResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(e.message ?: "Sale failed")
            .build()
    }
}
```

- [ ] **Step 5: Build and run existing tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :database-service:test`
Expected: All existing tests pass. The InventoryGrpcServiceTest stubs will need updating since npcBuy/npcSell no longer return "Not implemented".

- [ ] **Step 6: Update InventoryGrpcServiceTest for npcBuy/npcSell**

Update the existing test expectations that check for "Not implemented" to instead verify the actual behavior with mocked repository calls.

- [ ] **Step 7: Commit**

```bash
git add server/database-service/
git commit -m "feat: implement NPC buy/sell gRPC and stackable item support in InventoryRepository"
```

---

### Task 5: ItemDefinitionCache (world-service)

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/inventory/ItemDefinitionCache.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/inventory/ItemDefinitionCacheTest.kt`

- [ ] **Step 1: Write tests for ItemDefinitionCache**

```kotlin
package com.flyagain.world.inventory

import com.flyagain.common.grpc.ItemDefinitionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemDefinitionCacheTest {

    private fun makeItemDef(
        id: Int, name: String, type: Int = 0, levelReq: Int = 1,
        classReq: Int = -1, baseAttack: Int = 0, baseDefense: Int = 0,
        buyPrice: Int = 10, sellPrice: Int = 5, stackable: Boolean = false,
        maxStack: Int = 1
    ): ItemDefinitionRecord = ItemDefinitionRecord.newBuilder()
        .setId(id).setName(name).setType(type).setLevelReq(levelReq)
        .setClassReq(classReq).setBaseAttack(baseAttack).setBaseDefense(baseDefense)
        .setBuyPrice(buyPrice).setSellPrice(sellPrice)
        .setStackable(stackable).setMaxStack(maxStack)
        .build()

    @Test
    fun `get returns loaded item definition`() {
        val cache = ItemDefinitionCache()
        val sword = makeItemDef(1, "Wooden Sword", type = 0, baseAttack = 5)
        cache.load(listOf(sword))
        assertEquals(sword, cache.get(1))
    }

    @Test
    fun `get returns null for unknown id`() {
        val cache = ItemDefinitionCache()
        cache.load(emptyList())
        assertNull(cache.get(999))
    }

    @Test
    fun `getAll returns all loaded definitions`() {
        val cache = ItemDefinitionCache()
        val items = listOf(
            makeItemDef(1, "Sword"), makeItemDef(2, "Armor", type = 1)
        )
        cache.load(items)
        assertEquals(2, cache.getAll().size)
    }

    @Test
    fun `isWeapon returns true for type 0`() {
        val cache = ItemDefinitionCache()
        val sword = makeItemDef(1, "Sword", type = 0)
        cache.load(listOf(sword))
        assertTrue(cache.isWeapon(1))
    }

    @Test
    fun `isArmor returns true for type 1`() {
        val cache = ItemDefinitionCache()
        val armor = makeItemDef(1, "Armor", type = 1)
        cache.load(listOf(armor))
        assertTrue(cache.isArmor(1))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:test --tests "*.ItemDefinitionCacheTest"`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement ItemDefinitionCache**

```kotlin
package com.flyagain.world.inventory

import com.flyagain.common.grpc.ItemDefinitionRecord
import org.slf4j.LoggerFactory

/**
 * In-memory cache of item definitions loaded once at startup from database-service.
 * Thread-safe after load() completes (read-only after initialization).
 */
class ItemDefinitionCache {

    private val logger = LoggerFactory.getLogger(ItemDefinitionCache::class.java)
    private var items: Map<Int, ItemDefinitionRecord> = emptyMap()

    companion object {
        const val TYPE_WEAPON = 0
        const val TYPE_ARMOR = 1
        const val TYPE_QUEST_ITEM = 2
        const val TYPE_CONSUMABLE = 3

        /** Equipment slot type for weapons (matches V5 equipment.slot_type) */
        const val EQUIP_SLOT_WEAPON = 6
        const val EQUIP_SLOT_HEAD = 0
        const val EQUIP_SLOT_CHEST = 1
        const val EQUIP_SLOT_LEGS = 2
        const val EQUIP_SLOT_FEET = 3
        const val EQUIP_SLOT_HANDS = 4
        const val EQUIP_SLOT_BACK = 5
    }

    fun load(definitions: List<ItemDefinitionRecord>) {
        items = definitions.associateBy { it.id }
        logger.info("Loaded {} item definitions into cache", items.size)
    }

    fun get(itemId: Int): ItemDefinitionRecord? = items[itemId]

    fun getAll(): Collection<ItemDefinitionRecord> = items.values

    fun isWeapon(itemId: Int): Boolean = items[itemId]?.type == TYPE_WEAPON

    fun isArmor(itemId: Int): Boolean = items[itemId]?.type == TYPE_ARMOR

    fun isConsumable(itemId: Int): Boolean = items[itemId]?.type == TYPE_CONSUMABLE

    /**
     * Returns the equipment slot type that a given item type maps to.
     * Weapons → slot 6, Armor → slot 1 (chest, default). Returns -1 if not equippable.
     */
    fun getEquipSlotForItemType(itemType: Int, subtype: Int = 0): Int {
        return when (itemType) {
            TYPE_WEAPON -> EQUIP_SLOT_WEAPON
            TYPE_ARMOR -> EQUIP_SLOT_CHEST  // Simplified: all armor goes to chest for MVP
            else -> -1
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:test --tests "*.ItemDefinitionCacheTest"`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/inventory/ItemDefinitionCache.kt
git add server/world-service/src/test/kotlin/com/flyagain/world/inventory/ItemDefinitionCacheTest.kt
git commit -m "feat: add ItemDefinitionCache for in-memory item definition lookup"
```

---

### Task 6: EquipmentStatCalculator

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/inventory/EquipmentStatCalculator.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/inventory/EquipmentStatCalculatorTest.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/entity/PlayerEntity.kt`

- [ ] **Step 1: Add equipment bonus fields to PlayerEntity**

Add after `var gold: Long = 0L` (line 35):

```kotlin
// Equipment stat bonuses (recalculated when equipment changes)
var bonusAttack: Int = 0,
var bonusDefense: Int = 0,
var bonusHp: Int = 0,
var bonusMp: Int = 0,
```

Update `getAttackPower()`:
```kotlin
fun getAttackPower(): Int {
    return str * 2 + level + bonusAttack
}
```

Update `getDefense()`:
```kotlin
fun getDefense(): Int {
    return sta + level + bonusDefense
}
```

- [ ] **Step 2: Write tests for EquipmentStatCalculator**

```kotlin
package com.flyagain.world.inventory

import com.flyagain.common.grpc.EquipmentSlot
import com.flyagain.common.grpc.ItemDefinitionRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class EquipmentStatCalculatorTest {

    private fun makeItemDef(
        id: Int, baseAttack: Int = 0, baseDefense: Int = 0,
        baseHp: Int = 0, baseMp: Int = 0
    ) = ItemDefinitionRecord.newBuilder()
        .setId(id).setName("Item$id").setBaseAttack(baseAttack)
        .setBaseDefense(baseDefense).setBaseHp(baseHp).setBaseMp(baseMp)
        .build()

    private fun makeEquipSlot(slotType: Int, itemId: Int, enhancement: Int = 0) =
        EquipmentSlot.newBuilder()
            .setSlotType(slotType).setItemId(itemId).setEnhancement(enhancement)
            .build()

    @Test
    fun `empty equipment gives zero bonuses`() {
        val cache = ItemDefinitionCache()
        cache.load(emptyList())
        val calc = EquipmentStatCalculator(cache)
        val result = calc.calculateBonuses(emptyList())
        assertEquals(0, result.attack)
        assertEquals(0, result.defense)
    }

    @Test
    fun `weapon adds attack bonus`() {
        val cache = ItemDefinitionCache()
        cache.load(listOf(makeItemDef(1, baseAttack = 10)))
        val calc = EquipmentStatCalculator(cache)
        val result = calc.calculateBonuses(listOf(makeEquipSlot(6, 1)))
        assertEquals(10, result.attack)
    }

    @Test
    fun `armor adds defense bonus`() {
        val cache = ItemDefinitionCache()
        cache.load(listOf(makeItemDef(4, baseDefense = 8)))
        val calc = EquipmentStatCalculator(cache)
        val result = calc.calculateBonuses(listOf(makeEquipSlot(1, 4)))
        assertEquals(8, result.defense)
    }

    @Test
    fun `enhancement multiplies base stats`() {
        val cache = ItemDefinitionCache()
        cache.load(listOf(makeItemDef(1, baseAttack = 10)))
        val calc = EquipmentStatCalculator(cache)
        // +5 enhancement: 10 * (1 + 5 * 0.1) = 15
        val result = calc.calculateBonuses(listOf(makeEquipSlot(6, 1, enhancement = 5)))
        assertEquals(15, result.attack)
    }

    @Test
    fun `multiple equipment pieces stack`() {
        val cache = ItemDefinitionCache()
        cache.load(listOf(
            makeItemDef(1, baseAttack = 10),
            makeItemDef(4, baseDefense = 5, baseHp = 20)
        ))
        val calc = EquipmentStatCalculator(cache)
        val result = calc.calculateBonuses(listOf(
            makeEquipSlot(6, 1),
            makeEquipSlot(1, 4)
        ))
        assertEquals(10, result.attack)
        assertEquals(5, result.defense)
        assertEquals(20, result.hp)
    }
}
```

- [ ] **Step 3: Implement EquipmentStatCalculator**

```kotlin
package com.flyagain.world.inventory

import com.flyagain.common.grpc.EquipmentSlot

/**
 * Calculates total stat bonuses from a player's equipped items.
 * Enhancement level increases base stats by 10% per level.
 */
class EquipmentStatCalculator(
    private val itemCache: ItemDefinitionCache
) {

    data class EquipmentBonuses(
        val attack: Int = 0,
        val defense: Int = 0,
        val hp: Int = 0,
        val mp: Int = 0
    )

    fun calculateBonuses(equipment: List<EquipmentSlot>): EquipmentBonuses {
        var totalAttack = 0
        var totalDefense = 0
        var totalHp = 0
        var totalMp = 0

        for (slot in equipment) {
            val itemDef = itemCache.get(slot.itemId) ?: continue
            val enhancementMultiplier = 1.0 + slot.enhancement * 0.1

            totalAttack += (itemDef.baseAttack * enhancementMultiplier).toInt()
            totalDefense += (itemDef.baseDefense * enhancementMultiplier).toInt()
            totalHp += (itemDef.baseHp * enhancementMultiplier).toInt()
            totalMp += (itemDef.baseMp * enhancementMultiplier).toInt()
        }

        return EquipmentBonuses(totalAttack, totalDefense, totalHp, totalMp)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:test --tests "*.EquipmentStatCalculatorTest"`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/entity/PlayerEntity.kt
git add server/world-service/src/main/kotlin/com/flyagain/world/inventory/EquipmentStatCalculator.kt
git add server/world-service/src/test/kotlin/com/flyagain/world/inventory/EquipmentStatCalculatorTest.kt
git commit -m "feat: add EquipmentStatCalculator and equipment bonus fields to PlayerEntity"
```

---

### Task 7: NpcShopRegistry

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/inventory/NpcShopRegistry.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/inventory/NpcShopRegistryTest.kt`

- [ ] **Step 1: Write tests for NpcShopRegistry**

```kotlin
package com.flyagain.world.inventory

import com.flyagain.common.grpc.NpcDefinitionRecord
import com.flyagain.common.grpc.NpcShopItemRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NpcShopRegistryTest {

    private fun makeNpcDef(id: Int, name: String, zoneId: Int, x: Float, y: Float, z: Float, type: Int = 0) =
        NpcDefinitionRecord.newBuilder()
            .setId(id).setName(name).setZoneId(zoneId)
            .setPosX(x).setPosY(y).setPosZ(z).setNpcType(type)
            .build()

    private fun makeShopItem(npcId: Int, itemDefId: Int) =
        NpcShopItemRecord.newBuilder().setNpcId(npcId).setItemDefId(itemDefId).build()

    @Test
    fun `getNpcItems returns items for a shop NPC`() {
        val registry = NpcShopRegistry()
        registry.load(
            listOf(makeNpcDef(1, "Merchant", 0, 500f, 0f, 500f)),
            listOf(makeShopItem(1, 1), makeShopItem(1, 2))
        )
        val items = registry.getNpcItems(1)
        assertEquals(listOf(1, 2), items)
    }

    @Test
    fun `getNpcItems returns empty for unknown NPC`() {
        val registry = NpcShopRegistry()
        registry.load(emptyList(), emptyList())
        assertTrue(registry.getNpcItems(999).isEmpty())
    }

    @Test
    fun `npcSellsItem returns true when item is in shop`() {
        val registry = NpcShopRegistry()
        registry.load(
            listOf(makeNpcDef(1, "Merchant", 0, 500f, 0f, 500f)),
            listOf(makeShopItem(1, 7))
        )
        assertTrue(registry.npcSellsItem(1, 7))
        assertFalse(registry.npcSellsItem(1, 99))
    }

    @Test
    fun `getNpcDefinition returns NPC data`() {
        val registry = NpcShopRegistry()
        val npc = makeNpcDef(1, "Merchant", 0, 505f, 0f, 495f)
        registry.load(listOf(npc), emptyList())
        val result = registry.getNpcDefinition(1)
        assertNotNull(result)
        assertEquals("Merchant", result.name)
    }

    @Test
    fun `isInRange returns true when player is close`() {
        val registry = NpcShopRegistry()
        registry.load(
            listOf(makeNpcDef(1, "Merchant", 0, 500f, 0f, 500f)),
            emptyList()
        )
        assertTrue(registry.isInRange(1, 505f, 0f, 505f))  // ~7 units away
    }

    @Test
    fun `isInRange returns false when player is too far`() {
        val registry = NpcShopRegistry()
        registry.load(
            listOf(makeNpcDef(1, "Merchant", 0, 500f, 0f, 500f)),
            emptyList()
        )
        assertFalse(registry.isInRange(1, 600f, 0f, 600f))  // ~141 units away
    }
}
```

- [ ] **Step 2: Implement NpcShopRegistry**

```kotlin
package com.flyagain.world.inventory

import com.flyagain.common.grpc.NpcDefinitionRecord
import com.flyagain.common.grpc.NpcShopItemRecord
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * In-memory registry of NPC positions and shop inventories.
 * Loaded once at startup. Read-only after load().
 */
class NpcShopRegistry {

    private val logger = LoggerFactory.getLogger(NpcShopRegistry::class.java)

    companion object {
        /** Maximum distance (units) a player can be from an NPC to interact */
        const val NPC_INTERACTION_RANGE = 10.0f
    }

    private var npcDefs: Map<Int, NpcDefinitionRecord> = emptyMap()
    private var shopItems: Map<Int, List<Int>> = emptyMap() // npcId -> list of item_def_ids

    fun load(npcs: List<NpcDefinitionRecord>, items: List<NpcShopItemRecord>) {
        npcDefs = npcs.associateBy { it.id }
        shopItems = items.groupBy({ it.npcId }, { it.itemDefId })
        logger.info("Loaded {} NPC definitions and {} shop item entries", npcDefs.size, items.size)
    }

    fun getNpcDefinition(npcId: Int): NpcDefinitionRecord? = npcDefs[npcId]

    fun getNpcItems(npcId: Int): List<Int> = shopItems[npcId] ?: emptyList()

    fun npcSellsItem(npcId: Int, itemDefId: Int): Boolean =
        shopItems[npcId]?.contains(itemDefId) ?: false

    fun isInRange(npcId: Int, playerX: Float, playerY: Float, playerZ: Float): Boolean {
        val npc = npcDefs[npcId] ?: return false
        val dx = npc.posX - playerX
        val dy = npc.posY - playerY
        val dz = npc.posZ - playerZ
        val distance = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        return distance <= NPC_INTERACTION_RANGE
    }

    fun getAllNpcs(): Collection<NpcDefinitionRecord> = npcDefs.values
}
```

- [ ] **Step 3: Run tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:test --tests "*.NpcShopRegistryTest"`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/inventory/NpcShopRegistry.kt
git add server/world-service/src/test/kotlin/com/flyagain/world/inventory/NpcShopRegistryTest.kt
git commit -m "feat: add NpcShopRegistry for NPC position and shop inventory lookup"
```

---

### Task 8: BroadcastService — Inventory Update Method

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/network/BroadcastService.kt`

- [ ] **Step 1: Add `sendInventoryUpdate()` to BroadcastService**

Add after `sendGoldUpdate()` (around line 276):

```kotlin
/**
 * Send a full inventory snapshot to a specific player.
 * Used after item movement, equip/unequip, buy/sell.
 */
fun sendInventoryUpdate(
    player: PlayerEntity,
    inventorySlots: List<com.flyagain.common.grpc.InventorySlot>,
    equipmentSlots: List<com.flyagain.common.grpc.EquipmentSlot>
) {
    val invSlots = inventorySlots.map { slot ->
        InventorySlotInfo.newBuilder()
            .setSlot(slot.slot)
            .setItemId(slot.itemId)
            .setAmount(slot.amount)
            .setEnhancement(slot.enhancement)
            .build()
    }

    val equipSlots = equipmentSlots.map { slot ->
        EquipmentSlotInfo.newBuilder()
            .setSlotType(slot.slotType)
            .setItemId(slot.itemId)
            .setEnhancement(slot.enhancement)
            .build()
    }

    val msg = InventoryUpdateMessage.newBuilder()
        .addAllSlots(invSlots)
        .addAllEquipment(equipSlots)
        .build()

    val packet = Packet(Opcode.INVENTORY_UPDATE_VALUE, msg.toByteArray())
    sendToPlayer(player, packet)
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/network/BroadcastService.kt
git commit -m "feat: add sendInventoryUpdate to BroadcastService"
```

---

### Task 9: MoveItemHandler

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/handler/MoveItemHandler.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/handler/MoveItemHandlerTest.kt`

- [ ] **Step 1: Write tests for MoveItemHandler**

Test cases:
1. Successful move → response success=true
2. Invalid slot (< 0 or >= 100) → error
3. Same from/to slot → error
4. gRPC failure → error response
5. Rate limiting: max 10 moves per second

- [ ] **Step 2: Implement MoveItemHandler**

```kotlin
package com.flyagain.world.handler

import com.flyagain.common.grpc.InventoryDataServiceGrpcKt
import com.flyagain.common.grpc.MoveItemRequest as GrpcMoveItemRequest
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.MoveItemRequest
import com.flyagain.common.proto.MoveItemResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

class MoveItemHandler(
    private val inventoryStub: InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub,
    private val broadcastService: BroadcastService
) {

    private val logger = LoggerFactory.getLogger(MoveItemHandler::class.java)

    suspend fun handle(ctx: ChannelHandlerContext, player: PlayerEntity, request: MoveItemRequest) {
        val fromSlot = request.fromSlot
        val toSlot = request.toSlot

        // Validate slot range
        if (fromSlot < 0 || fromSlot >= 100 || toSlot < 0 || toSlot >= 100) {
            sendResponse(ctx, false, "Invalid slot index.")
            return
        }

        if (fromSlot == toSlot) {
            sendResponse(ctx, false, "Source and destination are the same.")
            return
        }

        try {
            val grpcRequest = GrpcMoveItemRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setFromSlot(fromSlot)
                .setToSlot(toSlot)
                .build()

            val result = inventoryStub.moveItem(grpcRequest)
            if (result.success) {
                sendResponse(ctx, true, "")
                // Send updated inventory snapshot
                sendInventorySnapshot(player)
            } else {
                sendResponse(ctx, false, result.errorMessage)
            }
        } catch (e: Exception) {
            logger.error("MoveItem failed for player {}: {}", player.name, e.message)
            sendResponse(ctx, false, "Internal error.")
        }
    }

    private suspend fun sendInventorySnapshot(player: PlayerEntity) {
        try {
            val inv = inventoryStub.getInventory(
                com.flyagain.common.grpc.GetInventoryRequest.newBuilder()
                    .setCharacterId(player.characterId).build()
            )
            val equip = inventoryStub.getEquipment(
                com.flyagain.common.grpc.GetEquipmentRequest.newBuilder()
                    .setCharacterId(player.characterId).build()
            )
            broadcastService.sendInventoryUpdate(player, inv.slotsList, equip.slotsList)
        } catch (e: Exception) {
            logger.warn("Failed to send inventory snapshot to {}: {}", player.name, e.message)
        }
    }

    private fun sendResponse(ctx: ChannelHandlerContext, success: Boolean, errorMessage: String) {
        val response = MoveItemResponse.newBuilder()
            .setSuccess(success)
            .setErrorMessage(errorMessage)
            .build()
        ctx.writeAndFlush(Packet(Opcode.MOVE_ITEM_VALUE, response.toByteArray()))
    }
}
```

- [ ] **Step 3: Run tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:test --tests "*.MoveItemHandlerTest"`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/handler/MoveItemHandler.kt
git add server/world-service/src/test/kotlin/com/flyagain/world/handler/MoveItemHandlerTest.kt
git commit -m "feat: add MoveItemHandler for inventory slot management"
```

---

### Task 10: EquipItemHandler

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/handler/EquipItemHandler.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/handler/EquipItemHandlerTest.kt`

- [ ] **Step 1: Write tests for EquipItemHandler**

Test cases:
1. Equip weapon → success, stats updated, stats broadcast
2. Equip with insufficient level → error
3. Equip with wrong class → error
4. Equip non-existent item → error from gRPC
5. Unequip item → success, stats updated
6. Unequip empty slot → error
7. Equip consumable → error (not equippable)

- [ ] **Step 2: Implement EquipItemHandler**

Key logic:
- Equip: validate item exists in cache, check level_req vs player.level, check class_req vs player.characterClass (class_req = -1 means no restriction), validate equip_slot_type matches item type, call gRPC equipItem, recalculate stats, broadcast
- Unequip: call gRPC unequipItem, recalculate stats, broadcast

```kotlin
package com.flyagain.world.handler

import com.flyagain.common.grpc.*
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.EquipItemRequest
import com.flyagain.common.proto.EquipItemResponse
import com.flyagain.common.proto.UnequipItemRequest
import com.flyagain.common.proto.UnequipItemResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.inventory.EquipmentStatCalculator
import com.flyagain.world.inventory.ItemDefinitionCache
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

class EquipItemHandler(
    private val inventoryStub: InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub,
    private val itemCache: ItemDefinitionCache,
    private val statCalculator: EquipmentStatCalculator,
    private val broadcastService: BroadcastService,
    private val zoneManager: ZoneManager
) {
    private val logger = LoggerFactory.getLogger(EquipItemHandler::class.java)

    suspend fun handleEquip(ctx: ChannelHandlerContext, player: PlayerEntity, request: EquipItemRequest) {
        val inventorySlot = request.inventorySlot
        val equipSlotType = request.equipSlotType

        // Validate slot ranges
        if (inventorySlot < 0 || inventorySlot >= 100) {
            sendEquipResponse(ctx, false, "Invalid inventory slot.")
            return
        }
        if (equipSlotType < 0 || equipSlotType > 6) {
            sendEquipResponse(ctx, false, "Invalid equipment slot.")
            return
        }

        // Get inventory to find item_id at slot
        val inv = try {
            inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )
        } catch (e: Exception) {
            logger.error("Failed to load inventory for {}: {}", player.name, e.message)
            sendEquipResponse(ctx, false, "Internal error.")
            return
        }

        val slotItem = inv.slotsList.find { it.slot == inventorySlot }
        if (slotItem == null) {
            sendEquipResponse(ctx, false, "No item in that slot.")
            return
        }

        val itemDef = itemCache.get(slotItem.itemId)
        if (itemDef == null) {
            sendEquipResponse(ctx, false, "Unknown item.")
            return
        }

        // Level requirement
        if (player.level < itemDef.levelReq) {
            sendEquipResponse(ctx, false, "Level ${itemDef.levelReq} required.")
            return
        }

        // Class requirement (-1 or 0 with no restriction depending on DB — classReq from proto defaults to 0)
        // In our DB, class_req NULL means any class. Proto encodes NULL as 0 for int32.
        // Our character_class: 0=Warrior, 1=Mage, 2=Assassin, 3=Cleric
        // If classReq > 0, it must match. If classReq == 0 AND the item is class-restricted,
        // it means Warrior only. We need to check if classReq was actually set.
        // Looking at V11 seed data: class_req=0 for Warrior weapons, NULL for armor.
        // Proto int32 default is 0, so we can't distinguish NULL from 0.
        // Fix: use -1 in proto for "no restriction". But the current proto uses int32 default 0.
        // For now: if classReq >= 0 AND classReq != player.characterClass → reject
        // But classReq=0 means Warrior, which is valid. Armor has NULL → proto 0 → looks like Warrior.
        // Need to check the internal.proto definition...
        // The ItemDefinitionRecord has class_req as int32. Default 0.
        // V11: Wooden Sword has class_req=0 (Warrior), Leather Armor has class_req=NULL.
        // But NULL → 0 in protobuf. This is a data issue.
        // Workaround: treat class_req = -1 as "any class" in the DB,
        // but current seed data uses NULL. We should use COALESCE in the repo query.
        // For now, assume: if item type is armor (type=1), no class restriction.
        // If item type is weapon (type=0), class_req applies.
        if (itemDef.type == ItemDefinitionCache.TYPE_WEAPON && itemDef.classReq != player.characterClass) {
            sendEquipResponse(ctx, false, "Your class cannot use this weapon.")
            return
        }

        // Validate equip slot matches item type
        if (itemDef.type == ItemDefinitionCache.TYPE_WEAPON && equipSlotType != ItemDefinitionCache.EQUIP_SLOT_WEAPON) {
            sendEquipResponse(ctx, false, "Weapons must be equipped in the weapon slot.")
            return
        }
        if (itemDef.type == ItemDefinitionCache.TYPE_ARMOR && equipSlotType == ItemDefinitionCache.EQUIP_SLOT_WEAPON) {
            sendEquipResponse(ctx, false, "Armor cannot be equipped in the weapon slot.")
            return
        }
        if (itemDef.type != ItemDefinitionCache.TYPE_WEAPON && itemDef.type != ItemDefinitionCache.TYPE_ARMOR) {
            sendEquipResponse(ctx, false, "This item cannot be equipped.")
            return
        }

        // Call gRPC to equip
        try {
            val result = inventoryStub.equipItem(
                com.flyagain.common.grpc.EquipItemRequest.newBuilder()
                    .setCharacterId(player.characterId)
                    .setInventorySlot(inventorySlot)
                    .setEquipSlotType(equipSlotType)
                    .build()
            )
            if (!result.success) {
                sendEquipResponse(ctx, false, result.errorMessage)
                return
            }
        } catch (e: Exception) {
            logger.error("EquipItem gRPC failed for {}: {}", player.name, e.message)
            sendEquipResponse(ctx, false, "Internal error.")
            return
        }

        // Recalculate stats and broadcast
        recalculateAndBroadcast(player)
        sendEquipResponse(ctx, true, "")
        sendInventorySnapshot(player)
    }

    suspend fun handleUnequip(ctx: ChannelHandlerContext, player: PlayerEntity, request: UnequipItemRequest) {
        val equipSlotType = request.equipSlotType

        if (equipSlotType < 0 || equipSlotType > 6) {
            sendUnequipResponse(ctx, false, "Invalid equipment slot.")
            return
        }

        try {
            val result = inventoryStub.unequipItem(
                com.flyagain.common.grpc.UnequipItemRequest.newBuilder()
                    .setCharacterId(player.characterId)
                    .setEquipSlotType(equipSlotType)
                    .build()
            )
            if (!result.success) {
                sendUnequipResponse(ctx, false, result.errorMessage)
                return
            }
        } catch (e: Exception) {
            logger.error("UnequipItem gRPC failed for {}: {}", player.name, e.message)
            sendUnequipResponse(ctx, false, "Internal error.")
            return
        }

        recalculateAndBroadcast(player)
        sendUnequipResponse(ctx, true, "")
        sendInventorySnapshot(player)
    }

    private suspend fun recalculateAndBroadcast(player: PlayerEntity) {
        try {
            val equip = inventoryStub.getEquipment(
                GetEquipmentRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            val bonuses = statCalculator.calculateBonuses(equip.slotsList)
            player.bonusAttack = bonuses.attack
            player.bonusDefense = bonuses.defense
            player.bonusHp = bonuses.hp
            player.bonusMp = bonuses.mp
            player.markDirty()

            // Broadcast stats update to nearby players
            val channel = zoneManager.getChannel(player.zoneId, player.channelId)
            if (channel != null) {
                broadcastService.broadcastEntityStatsUpdate(channel, player)
            }
        } catch (e: Exception) {
            logger.warn("Failed to recalculate equipment stats for {}: {}", player.name, e.message)
        }
    }

    private suspend fun sendInventorySnapshot(player: PlayerEntity) {
        try {
            val inv = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            val equip = inventoryStub.getEquipment(
                GetEquipmentRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            broadcastService.sendInventoryUpdate(player, inv.slotsList, equip.slotsList)
        } catch (e: Exception) {
            logger.warn("Failed to send inventory snapshot to {}: {}", player.name, e.message)
        }
    }

    private fun sendEquipResponse(ctx: ChannelHandlerContext, success: Boolean, errorMessage: String) {
        val response = EquipItemResponse.newBuilder()
            .setSuccess(success)
            .setErrorMessage(errorMessage)
            .build()
        ctx.writeAndFlush(Packet(Opcode.EQUIP_ITEM_VALUE, response.toByteArray()))
    }

    private fun sendUnequipResponse(ctx: ChannelHandlerContext, success: Boolean, errorMessage: String) {
        val response = UnequipItemResponse.newBuilder()
            .setSuccess(success)
            .setErrorMessage(errorMessage)
            .build()
        ctx.writeAndFlush(Packet(Opcode.UNEQUIP_ITEM_VALUE, response.toByteArray()))
    }
}
```

- [ ] **Step 3: Run tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:test --tests "*.EquipItemHandlerTest"`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/handler/EquipItemHandler.kt
git add server/world-service/src/test/kotlin/com/flyagain/world/handler/EquipItemHandlerTest.kt
git commit -m "feat: add EquipItemHandler with level/class validation and stat recalculation"
```

---

### Task 11: NpcShopHandler

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/handler/NpcShopHandler.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/handler/NpcShopHandlerTest.kt`

- [ ] **Step 1: Write tests for NpcShopHandler**

Test cases:
1. Buy: success → gold deducted, item added, gold update sent
2. Buy: NPC not in range → error
3. Buy: not enough gold → error
4. Buy: NPC doesn't sell this item → error
5. Buy: level requirement not met → error
6. Buy: inventory full → error from gRPC
7. Sell: success → item removed, gold added
8. Sell: NPC not in range → error
9. Sell: empty slot → error from gRPC
10. Sell: quest item (type=2) → error (cannot sell quest items)

- [ ] **Step 2: Implement NpcShopHandler**

```kotlin
package com.flyagain.world.handler

import com.flyagain.common.grpc.*
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.NpcBuyRequest
import com.flyagain.common.proto.NpcBuyResponse
import com.flyagain.common.proto.NpcSellRequest
import com.flyagain.common.proto.NpcSellResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.inventory.ItemDefinitionCache
import com.flyagain.world.inventory.NpcShopRegistry
import com.flyagain.world.network.BroadcastService
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

class NpcShopHandler(
    private val inventoryStub: InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub,
    private val itemCache: ItemDefinitionCache,
    private val npcShopRegistry: NpcShopRegistry,
    private val broadcastService: BroadcastService
) {
    private val logger = LoggerFactory.getLogger(NpcShopHandler::class.java)

    suspend fun handleBuy(ctx: ChannelHandlerContext, player: PlayerEntity, request: NpcBuyRequest) {
        val npcEntityId = request.npcEntityId
        val itemDefId = request.itemDefId
        val amount = request.amount

        // Validate amount
        if (amount <= 0 || amount > 99) {
            sendBuyResponse(ctx, false, 0, 0, "Invalid amount.")
            return
        }

        // NPC proximity check (using NPC definition ID, not entity ID, for now)
        // The client sends npc_entity_id which maps to the NPC definition ID
        val npcId = npcEntityId.toInt()
        if (!npcShopRegistry.isInRange(npcId, player.x, player.y, player.z)) {
            sendBuyResponse(ctx, false, 0, 0, "Too far from NPC.")
            return
        }

        // Check NPC sells this item
        if (!npcShopRegistry.npcSellsItem(npcId, itemDefId)) {
            sendBuyResponse(ctx, false, 0, 0, "This NPC does not sell that item.")
            return
        }

        // Get item definition
        val itemDef = itemCache.get(itemDefId)
        if (itemDef == null) {
            sendBuyResponse(ctx, false, 0, 0, "Unknown item.")
            return
        }

        // Level check
        if (player.level < itemDef.levelReq) {
            sendBuyResponse(ctx, false, 0, 0, "Level ${itemDef.levelReq} required.")
            return
        }

        // Gold check
        val totalCost = itemDef.buyPrice.toLong() * amount
        if (player.gold < totalCost) {
            sendBuyResponse(ctx, false, 0, 0, "Not enough gold.")
            return
        }

        // Execute purchase via gRPC
        try {
            val newGold = player.gold - totalCost
            val grpcRequest = com.flyagain.common.grpc.NpcBuyRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setItemDefId(itemDefId)
                .setAmount(amount)
                .setCurrentGold(newGold)
                .build()

            val result = inventoryStub.npcBuy(grpcRequest)
            if (result.success) {
                player.gold = newGold
                player.markDirty()
                broadcastService.sendGoldUpdate(player, -totalCost)
                sendBuyResponse(ctx, true, result.newGold, result.assignedSlot, "")
                sendInventorySnapshot(player)
            } else {
                sendBuyResponse(ctx, false, 0, 0, result.errorMessage)
            }
        } catch (e: Exception) {
            logger.error("NpcBuy failed for player {}: {}", player.name, e.message)
            sendBuyResponse(ctx, false, 0, 0, "Purchase failed.")
        }
    }

    suspend fun handleSell(ctx: ChannelHandlerContext, player: PlayerEntity, request: NpcSellRequest) {
        val npcEntityId = request.npcEntityId
        val inventorySlot = request.inventorySlot
        val amount = request.amount

        // NPC proximity check
        val npcId = npcEntityId.toInt()
        if (!npcShopRegistry.isInRange(npcId, player.x, player.y, player.z)) {
            sendSellResponse(ctx, false, 0, "Too far from NPC.")
            return
        }

        // Get inventory to find item at slot
        val inv = try {
            inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )
        } catch (e: Exception) {
            logger.error("Failed to load inventory for sell: {}", e.message)
            sendSellResponse(ctx, false, 0, "Internal error.")
            return
        }

        val slotItem = inv.slotsList.find { it.slot == inventorySlot }
        if (slotItem == null) {
            sendSellResponse(ctx, false, 0, "No item in that slot.")
            return
        }

        val itemDef = itemCache.get(slotItem.itemId)
        if (itemDef == null) {
            sendSellResponse(ctx, false, 0, "Unknown item.")
            return
        }

        // Cannot sell quest items
        if (itemDef.type == ItemDefinitionCache.TYPE_QUEST_ITEM) {
            sendSellResponse(ctx, false, 0, "Quest items cannot be sold.")
            return
        }

        // Calculate sell price
        val sellAmount = minOf(amount, slotItem.amount)
        val goldEarned = itemDef.sellPrice.toLong() * sellAmount

        // Execute sale: remove item then update gold
        try {
            val grpcRequest = com.flyagain.common.grpc.NpcSellRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setInventorySlot(inventorySlot)
                .setAmount(sellAmount)
                .build()

            val result = inventoryStub.npcSell(grpcRequest)
            if (result.success) {
                val newGold = player.gold + goldEarned
                inventoryStub.updateGold(
                    com.flyagain.common.grpc.UpdateGoldRequest.newBuilder()
                        .setCharacterId(player.characterId)
                        .setNewGold(newGold)
                        .build()
                )
                player.gold = newGold
                player.markDirty()
                broadcastService.sendGoldUpdate(player, goldEarned)
                sendSellResponse(ctx, true, newGold, "")
                sendInventorySnapshot(player)
            } else {
                sendSellResponse(ctx, false, 0, result.errorMessage)
            }
        } catch (e: Exception) {
            logger.error("NpcSell failed for player {}: {}", player.name, e.message)
            sendSellResponse(ctx, false, 0, "Sale failed.")
        }
    }

    private suspend fun sendInventorySnapshot(player: PlayerEntity) {
        try {
            val inv = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            val equip = inventoryStub.getEquipment(
                GetEquipmentRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            broadcastService.sendInventoryUpdate(player, inv.slotsList, equip.slotsList)
        } catch (e: Exception) {
            logger.warn("Failed to send inventory snapshot to {}: {}", player.name, e.message)
        }
    }

    private fun sendBuyResponse(ctx: ChannelHandlerContext, success: Boolean, newGold: Long, assignedSlot: Int, errorMessage: String) {
        val response = NpcBuyResponse.newBuilder()
            .setSuccess(success)
            .setNewGold(newGold)
            .setAssignedSlot(assignedSlot)
            .setErrorMessage(errorMessage)
            .build()
        ctx.writeAndFlush(Packet(Opcode.NPC_BUY_VALUE, response.toByteArray()))
    }

    private fun sendSellResponse(ctx: ChannelHandlerContext, success: Boolean, newGold: Long, errorMessage: String) {
        val response = NpcSellResponse.newBuilder()
            .setSuccess(success)
            .setNewGold(newGold)
            .setErrorMessage(errorMessage)
            .build()
        ctx.writeAndFlush(Packet(Opcode.NPC_SELL_VALUE, response.toByteArray()))
    }
}
```

**Note:** The `updateGold` RPC doesn't exist in the current proto. We need to either:
(a) Add an `UpdateGold` RPC to `InventoryDataService` in internal.proto, OR
(b) Combine sell + gold update in `npcSell` RPC.

Better approach: modify `NpcSellRequest` in internal.proto to include `new_gold` field, and have `npcSell` in the gRPC service do both `removeItem` + `updateGold` atomically.

- [ ] **Step 3: Run tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:test --tests "*.NpcShopHandlerTest"`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/handler/NpcShopHandler.kt
git add server/world-service/src/test/kotlin/com/flyagain/world/handler/NpcShopHandlerTest.kt
git commit -m "feat: add NpcShopHandler with proximity check, gold validation, and buy/sell logic"
```

---

### Task 12: Wire Everything — PacketRouter, Koin DI, WorldServiceMain

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/handler/PacketRouter.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/di/WorldServiceModule.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/WorldServiceMain.kt`

- [ ] **Step 1: Add InventoryDataService gRPC stub to WorldServiceModule**

Add after the GameDataService stub (line 66):

```kotlin
single { InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub(get<ManagedChannel>()) }
```

- [ ] **Step 2: Register new singletons in WorldServiceModule**

Add in the "Core game systems" section:

```kotlin
single { ItemDefinitionCache() }
single { EquipmentStatCalculator(get()) }
single { NpcShopRegistry() }
```

Add in the "Handlers" section:

```kotlin
single { MoveItemHandler(get(), get()) }
single { EquipItemHandler(get(), get(), get(), get(), get()) }
single { NpcShopHandler(get(), get(), get(), get()) }
```

- [ ] **Step 3: Add new handler parameters to PacketRouter constructor**

Add to the constructor:
```kotlin
private val moveItemHandler: MoveItemHandler,
private val equipItemHandler: EquipItemHandler,
private val npcShopHandler: NpcShopHandler,
```

- [ ] **Step 4: Route inventory opcodes in PacketRouter.channelRead0**

Add to the `when (opcode)` block, after the combat opcodes:

```kotlin
// Inventory opcodes
Opcode.MOVE_ITEM_VALUE -> {
    coroutineScope.launch(MDCContext()) {
        try {
            val request = com.flyagain.common.proto.MoveItemRequest.parseFrom(msg.payload)
            moveItemHandler.handle(ctx, player, request)
        } catch (e: Exception) {
            logger.warn("Failed to handle MOVE_ITEM from player {}: {}", player.name, e.message)
            sendError(ctx, opcode, 400, "Malformed request.")
        }
    }
}

Opcode.EQUIP_ITEM_VALUE -> {
    coroutineScope.launch(MDCContext()) {
        try {
            val request = com.flyagain.common.proto.EquipItemRequest.parseFrom(msg.payload)
            equipItemHandler.handleEquip(ctx, player, request)
        } catch (e: Exception) {
            logger.warn("Failed to handle EQUIP_ITEM from player {}: {}", player.name, e.message)
            sendError(ctx, opcode, 400, "Malformed request.")
        }
    }
}

Opcode.UNEQUIP_ITEM_VALUE -> {
    coroutineScope.launch(MDCContext()) {
        try {
            val request = com.flyagain.common.proto.UnequipItemRequest.parseFrom(msg.payload)
            equipItemHandler.handleUnequip(ctx, player, request)
        } catch (e: Exception) {
            logger.warn("Failed to handle UNEQUIP_ITEM from player {}: {}", player.name, e.message)
            sendError(ctx, opcode, 400, "Malformed request.")
        }
    }
}

Opcode.NPC_BUY_VALUE -> {
    coroutineScope.launch(MDCContext()) {
        try {
            val request = com.flyagain.common.proto.NpcBuyRequest.parseFrom(msg.payload)
            npcShopHandler.handleBuy(ctx, player, request)
        } catch (e: Exception) {
            logger.warn("Failed to handle NPC_BUY from player {}: {}", player.name, e.message)
            sendError(ctx, opcode, 400, "Malformed request.")
        }
    }
}

Opcode.NPC_SELL_VALUE -> {
    coroutineScope.launch(MDCContext()) {
        try {
            val request = com.flyagain.common.proto.NpcSellRequest.parseFrom(msg.payload)
            npcShopHandler.handleSell(ctx, player, request)
        } catch (e: Exception) {
            logger.warn("Failed to handle NPC_SELL from player {}: {}", player.name, e.message)
            sendError(ctx, opcode, 400, "Malformed request.")
        }
    }
}
```

- [ ] **Step 5: Update PacketRouter in Koin module**

Update the PacketRouter constructor call to include the new handlers.

- [ ] **Step 6: Load item definitions and NPC data at startup in WorldServiceMain**

Add after loot table loading (around line 57):

```kotlin
// Load item definitions
val itemDefs = gameDataStub.getAllItemDefinitions(Empty.getDefaultInstance())
val itemCache = koin.get<ItemDefinitionCache>()
itemCache.load(itemDefs.itemsList)

// Load NPC definitions and shop inventories
val npcDefs = gameDataStub.getAllNpcDefinitions(Empty.getDefaultInstance())
val npcShopItems = gameDataStub.getAllNpcShopItems(Empty.getDefaultInstance())
val npcShopRegistry = koin.get<NpcShopRegistry>()
npcShopRegistry.load(npcDefs.npcsList, npcShopItems.itemsList)
```

- [ ] **Step 7: Build the full project**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Update WorldServiceModuleTest**

Add new handler types to the extraTypes list in the Koin module verification test.

- [ ] **Step 9: Run all tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew test`
Expected: All tests pass

- [ ] **Step 10: Commit**

```bash
git add server/world-service/
git commit -m "feat: wire inventory handlers into PacketRouter, Koin DI, and startup"
```

---

### Task 13: DeathHandler — Deliver Loot to Inventory

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/combat/DeathHandler.kt`

- [ ] **Step 1: Add InventoryDataService dependency to DeathHandler**

Update constructor to accept `inventoryStub`:

```kotlin
class DeathHandler(
    private val xpSystem: XpSystem,
    private val lootSystem: LootSystem,
    private val broadcastService: BroadcastService,
    private val entityManager: EntityManager,
    private val inventoryStub: InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub,
    private val itemCache: ItemDefinitionCache,
    private val asyncScope: CoroutineScope
)
```

- [ ] **Step 2: Deliver loot items to player inventory**

Replace the "Roll loot" section (lines 77-82) with:

```kotlin
// 6. Roll loot and deliver to inventory
val lootDrops = lootSystem.rollLoot(monster.definitionId)
if (lootDrops.isNotEmpty()) {
    asyncScope.launch {
        for (drop in lootDrops) {
            try {
                val itemDef = itemCache.get(drop.itemId)
                val maxStack = if (itemDef?.stackable == true) itemDef.maxStack else 1
                inventoryStub.addItem(
                    AddItemRequest.newBuilder()
                        .setCharacterId(killer.characterId)
                        .setItemId(drop.itemId)
                        .setAmount(drop.amount)
                        .build()
                )
                logger.debug("Delivered loot {} x{} to player {}", drop.itemId, drop.amount, killer.name)
            } catch (e: Exception) {
                logger.warn("Failed to deliver loot {} to player {}: {}", drop.itemId, killer.name, e.message)
            }
        }
        // Send inventory update after all loot delivered
        try {
            val inv = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(killer.characterId).build()
            )
            val equip = inventoryStub.getEquipment(
                GetEquipmentRequest.newBuilder().setCharacterId(killer.characterId).build()
            )
            broadcastService.sendInventoryUpdate(killer, inv.slotsList, equip.slotsList)
        } catch (e: Exception) {
            logger.warn("Failed to send inventory update after loot: {}", e.message)
        }
    }
}
```

- [ ] **Step 3: Update DeathHandler in Koin module**

Update the `DeathHandler` singleton in `WorldServiceModule.kt` to include the new dependencies.

- [ ] **Step 4: Update DeathHandlerTest**

Update the test to mock the inventory stub and verify loot delivery.

- [ ] **Step 5: Run tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew :world-service:test`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add server/world-service/
git commit -m "feat: deliver monster loot directly to player inventory via gRPC"
```

---

### Task 14: Full Build and Integration Verification

- [ ] **Step 1: Full project build**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `cd /Users/puthoff/Development/vibe-coding/FlyAgain/server && ./gradlew test`
Expected: All tests pass

- [ ] **Step 3: Update IMPLEMENTATION_PHASES.md**

Mark Phase 1.6 server items as complete:
- [x] InventoryManager (MoveItem handler)
- [x] EquipmentManager (EquipItem/UnequipItem handlers with stat recalculation)
- [x] NpcShopHandler (NpcBuy/NpcSell with proximity check)
- [x] NPC definitions (DB migration V12 with Aerheim merchants)
- [x] Loot delivery to inventory (DeathHandler integration)

- [ ] **Step 4: Commit**

```bash
git add docs/IMPLEMENTATION_PHASES.md
git commit -m "docs: mark Phase 1.6 server implementation as complete"
```

---

### Task 15: Performance Review

- [ ] **Step 1: Review all new handlers for performance bottlenecks**

Specifically check:
1. **gRPC calls per inventory operation** — each handler should minimize round-trips
2. **Inventory snapshot frequency** — full snapshot after every change could be heavy with 100 slots
3. **NPC proximity calculation** — `sqrt()` per NPC interaction (acceptable for infrequent operations)
4. **DeathHandler loot delivery** — async but sequential per item; could batch
5. **DB connection pool pressure** — multiple concurrent gRPC calls from many players
6. **No game-loop blocking** — all inventory ops are on coroutine scope, not game thread

- [ ] **Step 2: Document findings and apply fixes**

Focus on:
- Reducing gRPC round-trips where possible (e.g., batch inventory reads)
- Ensuring inventory snapshot doesn't flood the network (consider delta updates for future)
- Verifying no synchronous DB calls block the game loop

- [ ] **Step 3: Final commit with any performance fixes**
