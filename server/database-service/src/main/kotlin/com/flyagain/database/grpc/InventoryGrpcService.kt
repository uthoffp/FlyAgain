package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.InventoryRepository
import com.google.protobuf.Empty
import org.slf4j.LoggerFactory

/**
 * gRPC service implementation for inventory and equipment database operations.
 *
 * Exposes bag inventory management (add/remove/move items), equipment
 * equip/unequip, and NPC shop operations over gRPC. Used by the world-service
 * for real-time inventory changes and by the account-service for character
 * equipment display.
 *
 * @param inventoryRepo the inventory repository (interface — testable with mocks)
 */
class InventoryGrpcService(
    private val inventoryRepo: InventoryRepository
) : InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(InventoryGrpcService::class.java)

    /** Returns all occupied inventory slots for a character. */
    override suspend fun getInventory(request: GetInventoryRequest): InventoryContents {
        val slots = inventoryRepo.getInventory(request.characterId)
        return InventoryContents.newBuilder().addAllSlots(slots).build()
    }

    /** Returns all equipped items for a character. */
    override suspend fun getEquipment(request: GetEquipmentRequest): EquipmentContents {
        val slots = inventoryRepo.getEquipment(request.characterId)
        return EquipmentContents.newBuilder().addAllSlots(slots).build()
    }

    /** Moves (or swaps) an item between two inventory slots. */
    override suspend fun moveItem(request: MoveItemRequest): MoveItemResponse {
        val success = inventoryRepo.moveItem(request.characterId, request.fromSlot, request.toSlot)
        return MoveItemResponse.newBuilder()
            .setSuccess(success)
            .setErrorMessage(if (!success) "Item not found" else "")
            .build()
    }

    /** Adds an item to the first free inventory slot. Fails if inventory is full. */
    override suspend fun addItem(request: AddItemRequest): AddItemResponse {
        return try {
            val slot = inventoryRepo.addItem(request.characterId, request.itemId, request.amount)
            AddItemResponse.newBuilder().setSuccess(true).setAssignedSlot(slot).build()
        } catch (e: Exception) {
            logger.warn("addItem failed: {}", e.message)
            AddItemResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.message ?: "Unknown error")
                .build()
        }
    }

    /** Removes an item from the specified inventory slot. */
    override suspend fun removeItem(request: RemoveItemRequest): Empty {
        inventoryRepo.removeItem(request.characterId, request.slot, request.amount)
        return Empty.getDefaultInstance()
    }

    /** Equips an item from inventory into an equipment slot. Replaces any existing equipment. */
    override suspend fun equipItem(request: EquipItemRequest): EquipItemResponse {
        val success = inventoryRepo.equipItem(request.characterId, request.inventorySlot, request.equipSlotType)
        return EquipItemResponse.newBuilder()
            .setSuccess(success)
            .setErrorMessage(if (!success) "Item not found" else "")
            .build()
    }

    /** Unequips an item from an equipment slot back to inventory only. */
    override suspend fun unequipItem(request: UnequipItemRequest): UnequipItemResponse {
        val success = inventoryRepo.unequipItem(request.characterId, request.equipSlotType)
        return UnequipItemResponse.newBuilder()
            .setSuccess(success)
            .setErrorMessage(if (!success) "No item equipped in that slot" else "")
            .build()
    }

    /** Buys an item from an NPC vendor. Not yet implemented. */
    override suspend fun npcBuy(request: NpcBuyRequest): NpcBuyResponse {
        // TODO: Validate item price, deduct gold, add item
        return NpcBuyResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Not implemented yet")
            .build()
    }

    /** Sells an item to an NPC vendor. Not yet implemented. */
    override suspend fun npcSell(request: NpcSellRequest): NpcSellResponse {
        // TODO: Calculate sell price, add gold, remove item
        return NpcSellResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Not implemented yet")
            .build()
    }
}
