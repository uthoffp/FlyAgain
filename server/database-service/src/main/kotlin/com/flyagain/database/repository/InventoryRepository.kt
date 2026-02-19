package com.flyagain.database.repository

import com.flyagain.common.grpc.EquipmentSlot
import com.flyagain.common.grpc.InventorySlot

/**
 * Repository interface for inventory and equipment persistence.
 *
 * Handles bag inventory (up to 100 slots), equipment slots, item movement,
 * and gold management. All mutating operations run inside transactions to
 * maintain inventory consistency.
 *
 * @see InventoryRepositoryImpl for the PostgreSQL-backed implementation
 */
interface InventoryRepository {

    /**
     * Retrieves all inventory items for a character, ordered by slot index.
     *
     * @param characterId the character whose inventory to load
     * @return list of occupied [InventorySlot]s (empty slots are not stored)
     */
    suspend fun getInventory(characterId: Long): List<InventorySlot>

    /**
     * Retrieves all currently equipped items for a character.
     *
     * Joins the `equipment` table with `inventory` to include item details.
     *
     * @param characterId the character whose equipment to load
     * @return list of [EquipmentSlot]s (one per equipped slot type)
     */
    suspend fun getEquipment(characterId: Long): List<EquipmentSlot>

    /**
     * Moves an item from one inventory slot to another.
     *
     * If the destination slot is occupied, the two items are swapped atomically
     * using a temporary slot (-1) to avoid unique constraint violations.
     *
     * @param characterId the character performing the move
     * @param fromSlot source slot index
     * @param toSlot destination slot index
     * @return `true` if the move succeeded, `false` if no item exists at [fromSlot]
     */
    suspend fun moveItem(characterId: Long, fromSlot: Int, toSlot: Int): Boolean

    /**
     * Adds an item to the character's inventory in the first available slot.
     *
     * Scans slots 0-99 for the first unoccupied index.
     *
     * @param characterId the character receiving the item
     * @param itemId the item definition ID
     * @param amount stack count
     * @return the slot index where the item was placed
     * @throws NoSuchElementException if the inventory is full (all 100 slots occupied)
     */
    suspend fun addItem(characterId: Long, itemId: Int, amount: Int): Int

    /**
     * Removes an item from the specified inventory slot.
     *
     * Deletes the entire row regardless of [amount] — partial removal is not
     * currently supported.
     *
     * @param characterId the character whose item to remove
     * @param slot the inventory slot to clear
     * @param amount currently unused (full removal only)
     */
    suspend fun removeItem(characterId: Long, slot: Int, amount: Int)

    /**
     * Equips an item from the character's inventory into an equipment slot.
     *
     * If an item is already equipped in the target slot, it is unequipped first
     * (the row is deleted from `equipment`). The inventory item remains in its
     * bag slot while also being referenced by the equipment table.
     *
     * @param characterId the character equipping the item
     * @param inventorySlot the bag slot containing the item to equip
     * @param equipSlotType the target equipment slot type (e.g. head, weapon, chest)
     * @return `true` if successful, `false` if no item exists at [inventorySlot]
     */
    suspend fun equipItem(characterId: Long, inventorySlot: Int, equipSlotType: Int): Boolean

    /**
     * Unequips an item from the specified equipment slot.
     *
     * Removes the `equipment` row; the item remains in the character's bag inventory.
     *
     * @param characterId the character unequipping the item
     * @param equipSlotType the equipment slot to clear
     * @return `true` if an item was unequipped, `false` if the slot was already empty
     */
    suspend fun unequipItem(characterId: Long, equipSlotType: Int): Boolean

    /**
     * Directly sets a character's gold amount.
     *
     * Used by trade, NPC shop, and loot operations.
     *
     * @param characterId the character whose gold to update
     * @param newGold the new gold total (must be >= 0)
     */
    suspend fun updateGold(characterId: Long, newGold: Long)

    /**
     * Reads a character's current gold balance.
     *
     * @param characterId the character to query
     * @return the gold amount, or 0 if the character doesn't exist
     */
    suspend fun getGold(characterId: Long): Long
}
