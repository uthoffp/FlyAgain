package com.flyagain.database.repository

import com.flyagain.common.grpc.*
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [InventoryRepository].
 *
 * Manages bag inventory (100 slots max), equipment, item movement, and gold.
 * All mutating operations use transactions to ensure inventory consistency —
 * particularly important for slot swaps which use a temporary slot (-1).
 *
 * @param dataSource the HikariCP connection pool
 */
class InventoryRepositoryImpl(dataSource: DataSource) : BaseRepository(dataSource), InventoryRepository {

    override suspend fun getInventory(characterId: String): List<InventorySlot> = withConnection { conn ->
        conn.prepareStatement(
            "SELECT id, slot, item_id, amount, enhancement FROM inventory WHERE character_id = ? AND slot >= 0 ORDER BY slot"
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(characterId))
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<InventorySlot>()
                while (rs.next()) {
                    results.add(
                        InventorySlot.newBuilder()
                            .setId(rs.getString("id"))
                            .setSlot(rs.getInt("slot"))
                            .setItemId(rs.getInt("item_id"))
                            .setAmount(rs.getInt("amount"))
                            .setEnhancement(rs.getInt("enhancement"))
                            .build()
                    )
                }
                results
            }
        }
    }

    override suspend fun getEquipment(characterId: String): List<EquipmentSlot> = withConnection { conn ->
        conn.prepareStatement(
            """SELECT e.slot_type, e.inventory_id, i.item_id, i.enhancement
               FROM equipment e JOIN inventory i ON e.inventory_id = i.id
               WHERE e.character_id = ?"""
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(characterId))
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<EquipmentSlot>()
                while (rs.next()) {
                    results.add(
                        EquipmentSlot.newBuilder()
                            .setSlotType(rs.getInt("slot_type"))
                            .setInventoryId(rs.getString("inventory_id"))
                            .setItemId(rs.getInt("item_id"))
                            .setEnhancement(rs.getInt("enhancement"))
                            .build()
                    )
                }
                results
            }
        }
    }

    override suspend fun moveItem(characterId: String, fromSlot: Int, toSlot: Int): Boolean = withTransaction { conn ->
        val charUuid = UUID.fromString(characterId)

        // Get item at fromSlot
        val fromItem = conn.prepareStatement(
            "SELECT id FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, fromSlot)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("id") else null }
        } ?: return@withTransaction false

        // Check if toSlot is occupied
        val toItem = conn.prepareStatement(
            "SELECT id FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, toSlot)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("id") else null }
        }

        if (toItem != null) {
            // Swap: move toItem to a temp slot, then swap
            conn.prepareStatement("UPDATE inventory SET slot = -1 WHERE id = ?").use { it.setObject(1, UUID.fromString(toItem)); it.executeUpdate() }
            conn.prepareStatement("UPDATE inventory SET slot = ? WHERE id = ?").use { it.setInt(1, toSlot); it.setObject(2, UUID.fromString(fromItem)); it.executeUpdate() }
            conn.prepareStatement("UPDATE inventory SET slot = ? WHERE id = ?").use { it.setInt(1, fromSlot); it.setObject(2, UUID.fromString(toItem)); it.executeUpdate() }
        } else {
            conn.prepareStatement("UPDATE inventory SET slot = ? WHERE id = ?").use {
                it.setInt(1, toSlot)
                it.setObject(2, UUID.fromString(fromItem))
                it.executeUpdate()
            }
        }
        true
    }

    override suspend fun addItem(characterId: String, itemId: Int, amount: Int): Int = withTransaction { conn ->
        val charUuid = UUID.fromString(characterId)

        // Look up stackability from item_definitions so loot drops (and all
        // other addItem callers) automatically merge into existing stacks.
        val maxStack = conn.prepareStatement(
            "SELECT stackable, max_stack FROM item_definitions WHERE id = ?"
        ).use { stmt ->
            stmt.setInt(1, itemId)
            stmt.executeQuery().use { rs ->
                if (rs.next() && rs.getBoolean("stackable")) rs.getInt("max_stack") else 1
            }
        }

        // If item is stackable, try to find an existing stack with room
        if (maxStack > 1) {
            val existingSlot = conn.prepareStatement(
                "SELECT slot, amount FROM inventory WHERE character_id = ? AND item_id = ? AND amount < ? ORDER BY slot LIMIT 1"
            ).use { stmt ->
                stmt.setObject(1, charUuid)
                stmt.setInt(2, itemId)
                stmt.setInt(3, maxStack)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) Pair(rs.getInt("slot"), rs.getInt("amount")) else null
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

        // Find first free slot (0-99)
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

    override suspend fun addItemStackable(characterId: String, itemId: Int, amount: Int, maxStack: Int): Int = withTransaction { conn ->
        val charUuid = UUID.fromString(characterId)

        // If stackable, try to find an existing stack with room
        if (maxStack > 1) {
            val existingSlot = conn.prepareStatement(
                "SELECT slot, amount FROM inventory WHERE character_id = ? AND item_id = ? AND amount < ? ORDER BY slot LIMIT 1"
            ).use { stmt ->
                stmt.setObject(1, charUuid)
                stmt.setInt(2, itemId)
                stmt.setInt(3, maxStack)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) Pair(rs.getInt("slot"), rs.getInt("amount")) else null
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

        // Fall back to finding first free slot (0-99)
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

    override suspend fun removeItem(characterId: String, slot: Int, amount: Int): Unit = withTransaction { conn ->
        val charUuid = UUID.fromString(characterId)

        // Get current amount in slot
        val currentAmount = conn.prepareStatement(
            "SELECT amount FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, slot)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt("amount") else 0 }
        }

        if (currentAmount <= 0) return@withTransaction

        if (amount >= currentAmount) {
            // Remove entire stack
            conn.prepareStatement(
                "DELETE FROM inventory WHERE character_id = ? AND slot = ?"
            ).use { stmt ->
                stmt.setObject(1, charUuid)
                stmt.setInt(2, slot)
                stmt.executeUpdate()
            }
        } else {
            // Reduce stack
            conn.prepareStatement(
                "UPDATE inventory SET amount = amount - ? WHERE character_id = ? AND slot = ?"
            ).use { stmt ->
                stmt.setInt(1, amount)
                stmt.setObject(2, charUuid)
                stmt.setInt(3, slot)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun equipItem(characterId: String, inventorySlot: Int, equipSlotType: Int): Boolean = withTransaction { conn ->
        val charUuid = UUID.fromString(characterId)

        val inventoryId = conn.prepareStatement(
            "SELECT id FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, inventorySlot)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("id") else null }
        } ?: return@withTransaction false

        // If there's already an item equipped in that slot, move it back to the freed bag slot
        val oldEquippedInvId = conn.prepareStatement(
            "SELECT inventory_id FROM equipment WHERE character_id = ? AND slot_type = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, equipSlotType)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("inventory_id") else null }
        }

        // Remove existing equipment in that slot
        conn.prepareStatement(
            "DELETE FROM equipment WHERE character_id = ? AND slot_type = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, equipSlotType)
            stmt.executeUpdate()
        }

        // Move previously equipped item back to the now-freed inventory slot
        if (oldEquippedInvId != null) {
            conn.prepareStatement(
                "UPDATE inventory SET slot = ? WHERE id = ?"
            ).use { stmt ->
                stmt.setInt(1, inventorySlot)
                stmt.setObject(2, UUID.fromString(oldEquippedInvId))
                stmt.executeUpdate()
            }
        }

        // Move new item out of the bag (slot = -1 means "equipped, not in bag")
        conn.prepareStatement(
            "UPDATE inventory SET slot = -1 WHERE id = ?"
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(inventoryId))
            stmt.executeUpdate()
        }

        // Equip new item
        conn.prepareStatement(
            "INSERT INTO equipment (character_id, slot_type, inventory_id) VALUES (?, ?, ?)"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, equipSlotType)
            stmt.setObject(3, UUID.fromString(inventoryId))
            stmt.executeUpdate()
        }
        true
    }

    override suspend fun unequipItem(characterId: String, equipSlotType: Int): Boolean = withTransaction { conn ->
        val charUuid = UUID.fromString(characterId)

        // Find the inventory item referenced by this equipment slot
        val inventoryId = conn.prepareStatement(
            "SELECT inventory_id FROM equipment WHERE character_id = ? AND slot_type = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, equipSlotType)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("inventory_id") else null }
        } ?: return@withTransaction false

        // Remove equipment entry
        conn.prepareStatement(
            "DELETE FROM equipment WHERE character_id = ? AND slot_type = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, equipSlotType)
            stmt.executeUpdate()
        }

        // Find first free bag slot (0-99)
        val usedSlots = conn.prepareStatement(
            "SELECT slot FROM inventory WHERE character_id = ? AND slot >= 0 AND slot < 100 ORDER BY slot"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.executeQuery().use { rs ->
                val slots = mutableSetOf<Int>()
                while (rs.next()) slots.add(rs.getInt("slot"))
                slots
            }
        }

        val freeSlot = (0 until 100).firstOrNull { it !in usedSlots }
            ?: return@withTransaction false  // Inventory full — cannot unequip

        // Move item back to bag
        conn.prepareStatement(
            "UPDATE inventory SET slot = ? WHERE id = ?"
        ).use { stmt ->
            stmt.setInt(1, freeSlot)
            stmt.setObject(2, UUID.fromString(inventoryId))
            stmt.executeUpdate()
        }

        true
    }

    override suspend fun atomicBuyItem(characterId: String, itemId: Int, amount: Int, newGold: Long): Int = withTransaction { conn ->
        val charUuid = UUID.fromString(characterId)

        // Deduct gold
        conn.prepareStatement("UPDATE characters SET gold = ? WHERE id = ?").use { stmt ->
            stmt.setLong(1, newGold)
            stmt.setObject(2, charUuid)
            stmt.executeUpdate()
        }

        // Look up stackability from item_definitions so purchased stackable
        // items merge into existing stacks instead of consuming a new slot.
        val maxStack = conn.prepareStatement(
            "SELECT stackable, max_stack FROM item_definitions WHERE id = ?"
        ).use { stmt ->
            stmt.setInt(1, itemId)
            stmt.executeQuery().use { rs ->
                if (rs.next() && rs.getBoolean("stackable")) rs.getInt("max_stack") else 1
            }
        }

        // If item is stackable, try to find an existing stack with room
        if (maxStack > 1) {
            val existingSlot = conn.prepareStatement(
                "SELECT slot, amount FROM inventory WHERE character_id = ? AND item_id = ? AND amount < ? ORDER BY slot LIMIT 1"
            ).use { stmt ->
                stmt.setObject(1, charUuid)
                stmt.setInt(2, itemId)
                stmt.setInt(3, maxStack)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) Pair(rs.getInt("slot"), rs.getInt("amount")) else null
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

        // Find first free slot
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

        val freeSlot = (0 until 100).firstOrNull { it !in usedSlots }
            ?: throw NoSuchElementException("Inventory full")

        // Add item
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

    override suspend fun atomicSellItem(characterId: String, slot: Int, amount: Int, newGold: Long): Unit = withTransaction { conn ->
        val charUuid = UUID.fromString(characterId)

        // Get current amount in slot
        val currentAmount = conn.prepareStatement(
            "SELECT amount FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, slot)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt("amount") else 0 }
        }

        if (currentAmount <= 0) throw NoSuchElementException("No item in slot $slot")

        if (amount >= currentAmount) {
            conn.prepareStatement("DELETE FROM inventory WHERE character_id = ? AND slot = ?").use { stmt ->
                stmt.setObject(1, charUuid)
                stmt.setInt(2, slot)
                stmt.executeUpdate()
            }
        } else {
            conn.prepareStatement("UPDATE inventory SET amount = amount - ? WHERE character_id = ? AND slot = ?").use { stmt ->
                stmt.setInt(1, amount)
                stmt.setObject(2, charUuid)
                stmt.setInt(3, slot)
                stmt.executeUpdate()
            }
        }

        // Update gold
        conn.prepareStatement("UPDATE characters SET gold = ? WHERE id = ?").use { stmt ->
            stmt.setLong(1, newGold)
            stmt.setObject(2, charUuid)
            stmt.executeUpdate()
        }
    }

    override suspend fun updateGold(characterId: String, newGold: Long): Unit = withTransaction { conn ->
        conn.prepareStatement(
            "UPDATE characters SET gold = ? WHERE id = ?"
        ).use { stmt ->
            stmt.setLong(1, newGold)
            stmt.setObject(2, UUID.fromString(characterId))
            stmt.executeUpdate()
        }
    }

    override suspend fun getGold(characterId: String): Long = withConnection { conn ->
        conn.prepareStatement("SELECT gold FROM characters WHERE id = ?").use { stmt ->
            stmt.setObject(1, UUID.fromString(characterId))
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getLong("gold") else 0L
            }
        }
    }
}
