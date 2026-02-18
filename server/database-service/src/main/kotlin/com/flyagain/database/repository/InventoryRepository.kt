package com.flyagain.database.repository

import com.flyagain.common.grpc.*
import javax.sql.DataSource

class InventoryRepository(dataSource: DataSource) : BaseRepository(dataSource) {

    suspend fun getInventory(characterId: Long): List<InventorySlot> = withConnection { conn ->
        conn.prepareStatement(
            "SELECT id, slot, item_id, amount, enhancement FROM inventory WHERE character_id = ? ORDER BY slot"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<InventorySlot>()
                while (rs.next()) {
                    results.add(
                        InventorySlot.newBuilder()
                            .setId(rs.getLong("id"))
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

    suspend fun getEquipment(characterId: Long): List<EquipmentSlot> = withConnection { conn ->
        conn.prepareStatement(
            """SELECT e.slot_type, e.inventory_id, i.item_id, i.enhancement
               FROM equipment e JOIN inventory i ON e.inventory_id = i.id
               WHERE e.character_id = ?"""
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<EquipmentSlot>()
                while (rs.next()) {
                    results.add(
                        EquipmentSlot.newBuilder()
                            .setSlotType(rs.getInt("slot_type"))
                            .setInventoryId(rs.getLong("inventory_id"))
                            .setItemId(rs.getInt("item_id"))
                            .setEnhancement(rs.getInt("enhancement"))
                            .build()
                    )
                }
                results
            }
        }
    }

    suspend fun moveItem(characterId: Long, fromSlot: Int, toSlot: Int): Boolean = withTransaction { conn ->
        // Get item at fromSlot
        val fromItem = conn.prepareStatement(
            "SELECT id FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.setInt(2, fromSlot)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong("id") else null }
        } ?: return@withTransaction false

        // Check if toSlot is occupied
        val toItem = conn.prepareStatement(
            "SELECT id FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.setInt(2, toSlot)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong("id") else null }
        }

        if (toItem != null) {
            // Swap: move toItem to a temp slot, then swap
            conn.prepareStatement("UPDATE inventory SET slot = -1 WHERE id = ?").use { it.setLong(1, toItem); it.executeUpdate() }
            conn.prepareStatement("UPDATE inventory SET slot = ? WHERE id = ?").use { it.setInt(1, toSlot); it.setLong(2, fromItem); it.executeUpdate() }
            conn.prepareStatement("UPDATE inventory SET slot = ? WHERE id = ?").use { it.setInt(1, fromSlot); it.setLong(2, toItem); it.executeUpdate() }
        } else {
            conn.prepareStatement("UPDATE inventory SET slot = ? WHERE id = ?").use {
                it.setInt(1, toSlot)
                it.setLong(2, fromItem)
                it.executeUpdate()
            }
        }
        true
    }

    suspend fun addItem(characterId: Long, itemId: Int, amount: Int): Int = withTransaction { conn ->
        // Find first free slot
        val usedSlots = conn.prepareStatement(
            "SELECT slot FROM inventory WHERE character_id = ? ORDER BY slot"
        ).use { stmt ->
            stmt.setLong(1, characterId)
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
            stmt.setLong(1, characterId)
            stmt.setInt(2, freeSlot)
            stmt.setInt(3, itemId)
            stmt.setInt(4, amount)
            stmt.executeUpdate()
        }
        freeSlot
    }

    suspend fun removeItem(characterId: Long, slot: Int, amount: Int) = withTransaction { conn ->
        conn.prepareStatement(
            "DELETE FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.setInt(2, slot)
            stmt.executeUpdate()
        }
    }

    suspend fun equipItem(characterId: Long, inventorySlot: Int, equipSlotType: Int): Boolean = withTransaction { conn ->
        val inventoryId = conn.prepareStatement(
            "SELECT id FROM inventory WHERE character_id = ? AND slot = ?"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.setInt(2, inventorySlot)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getLong("id") else null }
        } ?: return@withTransaction false

        // Remove existing equipment in that slot
        conn.prepareStatement(
            "DELETE FROM equipment WHERE character_id = ? AND slot_type = ?"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.setInt(2, equipSlotType)
            stmt.executeUpdate()
        }

        // Equip new item
        conn.prepareStatement(
            "INSERT INTO equipment (character_id, slot_type, inventory_id) VALUES (?, ?, ?)"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.setInt(2, equipSlotType)
            stmt.setLong(3, inventoryId)
            stmt.executeUpdate()
        }
        true
    }

    suspend fun unequipItem(characterId: Long, equipSlotType: Int): Boolean = withTransaction { conn ->
        val deleted = conn.prepareStatement(
            "DELETE FROM equipment WHERE character_id = ? AND slot_type = ?"
        ).use { stmt ->
            stmt.setLong(1, characterId)
            stmt.setInt(2, equipSlotType)
            stmt.executeUpdate()
        }
        deleted > 0
    }

    suspend fun updateGold(characterId: Long, newGold: Long) = withTransaction { conn ->
        conn.prepareStatement(
            "UPDATE characters SET gold = ? WHERE id = ?"
        ).use { stmt ->
            stmt.setLong(1, newGold)
            stmt.setLong(2, characterId)
            stmt.executeUpdate()
        }
    }

    suspend fun getGold(characterId: Long): Long = withConnection { conn ->
        conn.prepareStatement("SELECT gold FROM characters WHERE id = ?").use { stmt ->
            stmt.setLong(1, characterId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getLong("gold") else 0L
            }
        }
    }
}
