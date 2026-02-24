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
            "SELECT id, slot, item_id, amount, enhancement FROM inventory WHERE character_id = ? ORDER BY slot"
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

    override suspend fun removeItem(characterId: String, slot: Int, amount: Int): Unit = withTransaction { conn ->
        conn.prepareStatement(
            "DELETE FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(characterId))
            stmt.setInt(2, slot)
            stmt.executeUpdate()
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

        // Remove existing equipment in that slot
        conn.prepareStatement(
            "DELETE FROM equipment WHERE character_id = ? AND slot_type = ?"
        ).use { stmt ->
            stmt.setObject(1, charUuid)
            stmt.setInt(2, equipSlotType)
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
        val deleted = conn.prepareStatement(
            "DELETE FROM equipment WHERE character_id = ? AND slot_type = ?"
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(characterId))
            stmt.setInt(2, equipSlotType)
            stmt.executeUpdate()
        }
        deleted > 0
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
