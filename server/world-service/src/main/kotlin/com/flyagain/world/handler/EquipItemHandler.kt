package com.flyagain.world.handler

import com.flyagain.common.grpc.EquipItemRequest
import com.flyagain.common.grpc.GetEquipmentRequest
import com.flyagain.common.grpc.GetInventoryRequest
import com.flyagain.common.grpc.InventoryDataServiceGrpcKt
import com.flyagain.common.grpc.UnequipItemRequest
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ClientEquipItemRequest
import com.flyagain.common.proto.ClientEquipItemResponse
import com.flyagain.common.proto.ClientUnequipItemRequest
import com.flyagain.common.proto.ClientUnequipItemResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.inventory.EquipmentStatCalculator
import com.flyagain.world.inventory.ItemDefinitionCache
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneManager
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles equip and unequip requests from clients.
 *
 * Performs server-authoritative validation of item type, level/class
 * requirements, and equip slot compatibility before delegating to the
 * database service. Recalculates equipment stat bonuses after changes
 * and broadcasts the updated stats to nearby players.
 */
class EquipItemHandler(
    private val inventoryStub: InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub,
    private val itemCache: ItemDefinitionCache,
    private val statCalculator: EquipmentStatCalculator,
    private val broadcastService: BroadcastService,
    private val zoneManager: ZoneManager
) {

    private val logger = LoggerFactory.getLogger(EquipItemHandler::class.java)

    companion object {
        private const val MIN_INVENTORY_SLOT = 0
        private const val MAX_INVENTORY_SLOT = 99
        private const val MIN_EQUIP_SLOT = 0
        private const val MAX_EQUIP_SLOT = 6
    }

    suspend fun handleEquip(ctx: ChannelHandlerContext, player: PlayerEntity, request: ClientEquipItemRequest) {
        val inventorySlot = request.inventorySlot
        val equipSlotType = request.equipSlotType

        // Validate inventory slot range
        if (inventorySlot < MIN_INVENTORY_SLOT || inventorySlot > MAX_INVENTORY_SLOT) {
            sendEquipError(ctx, "Invalid inventory slot")
            return
        }

        // Validate equip slot range
        if (equipSlotType < MIN_EQUIP_SLOT || equipSlotType > MAX_EQUIP_SLOT) {
            sendEquipError(ctx, "Invalid equipment slot")
            return
        }

        try {
            // Fetch current inventory to get the item at the specified slot
            val inventory = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )

            val invSlot = inventory.slotsList.find { it.slot == inventorySlot }
            if (invSlot == null || invSlot.itemId == 0) {
                sendEquipError(ctx, "No item in that slot")
                return
            }

            // Lookup item definition
            val itemDef = itemCache.get(invSlot.itemId)
            if (itemDef == null) {
                sendEquipError(ctx, "Unknown item")
                return
            }

            // Check level requirement
            if (itemDef.levelReq > player.level) {
                sendEquipError(ctx, "Level requirement not met")
                return
            }

            // Check class requirement (0 means any class)
            if (itemDef.classReq != 0 && itemDef.classReq != player.characterClass) {
                sendEquipError(ctx, "Class requirement not met")
                return
            }

            // Validate item type matches equip slot
            if (itemDef.type == ItemDefinitionCache.TYPE_WEAPON && equipSlotType != ItemDefinitionCache.EQUIP_SLOT_WEAPON) {
                sendEquipError(ctx, "Weapons can only be equipped in the weapon slot")
                return
            }
            if (itemDef.type == ItemDefinitionCache.TYPE_ARMOR && equipSlotType == ItemDefinitionCache.EQUIP_SLOT_WEAPON) {
                sendEquipError(ctx, "Armor cannot be equipped in the weapon slot")
                return
            }
            if (itemDef.type != ItemDefinitionCache.TYPE_WEAPON && itemDef.type != ItemDefinitionCache.TYPE_ARMOR) {
                sendEquipError(ctx, "This item cannot be equipped")
                return
            }

            // Call gRPC to equip item
            val grpcRequest = EquipItemRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setInventorySlot(inventorySlot)
                .setEquipSlotType(equipSlotType)
                .build()

            val grpcResponse = inventoryStub.equipItem(grpcRequest)

            if (!grpcResponse.success) {
                sendEquipError(ctx, grpcResponse.errorMessage.ifEmpty { "Failed to equip item" })
                return
            }

            // Recalculate stats from updated equipment
            val updatedEquipment = inventoryStub.getEquipment(
                GetEquipmentRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            val updatedInventory = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )

            applyEquipmentBonuses(player, updatedEquipment.slotsList)

            // Broadcast stats update to nearby players
            val channel = zoneManager.getChannel(player.zoneId, player.channelId)
            if (channel != null) {
                broadcastService.broadcastEntityStatsUpdate(channel, player)
            }

            // Send success response
            val response = ClientEquipItemResponse.newBuilder()
                .setSuccess(true)
                .build()
            ctx.writeAndFlush(Packet(Opcode.EQUIP_ITEM_VALUE, response.toByteArray()))

            // Send inventory update
            broadcastService.sendInventoryUpdate(player, updatedInventory.slotsList, updatedEquipment.slotsList)

            logger.debug("Player {} equipped item {} to slot {}", player.name, invSlot.itemId, equipSlotType)
        } catch (e: Exception) {
            logger.error("Error equipping item for player {}: {}", player.name, e.message, e)
            sendEquipError(ctx, "Internal error")
        }
    }

    suspend fun handleUnequip(ctx: ChannelHandlerContext, player: PlayerEntity, request: ClientUnequipItemRequest) {
        val equipSlotType = request.equipSlotType

        // Validate equip slot range
        if (equipSlotType < MIN_EQUIP_SLOT || equipSlotType > MAX_EQUIP_SLOT) {
            sendUnequipError(ctx, "Invalid equipment slot")
            return
        }

        try {
            // Call gRPC to unequip item
            val grpcRequest = UnequipItemRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setEquipSlotType(equipSlotType)
                .build()

            val grpcResponse = inventoryStub.unequipItem(grpcRequest)

            if (!grpcResponse.success) {
                sendUnequipError(ctx, grpcResponse.errorMessage.ifEmpty { "Failed to unequip item" })
                return
            }

            // Recalculate stats from updated equipment
            val updatedEquipment = inventoryStub.getEquipment(
                GetEquipmentRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            val updatedInventory = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )

            applyEquipmentBonuses(player, updatedEquipment.slotsList)

            // Broadcast stats update to nearby players
            val channel = zoneManager.getChannel(player.zoneId, player.channelId)
            if (channel != null) {
                broadcastService.broadcastEntityStatsUpdate(channel, player)
            }

            // Send success response
            val response = ClientUnequipItemResponse.newBuilder()
                .setSuccess(true)
                .build()
            ctx.writeAndFlush(Packet(Opcode.UNEQUIP_ITEM_VALUE, response.toByteArray()))

            // Send inventory update
            broadcastService.sendInventoryUpdate(player, updatedInventory.slotsList, updatedEquipment.slotsList)

            logger.debug("Player {} unequipped slot {}", player.name, equipSlotType)
        } catch (e: Exception) {
            logger.error("Error unequipping item for player {}: {}", player.name, e.message, e)
            sendUnequipError(ctx, "Internal error")
        }
    }

    private fun applyEquipmentBonuses(
        player: PlayerEntity,
        equipment: List<com.flyagain.common.grpc.EquipmentSlot>
    ) {
        val bonuses = statCalculator.calculateBonuses(equipment)
        player.bonusAttack = bonuses.attack
        player.bonusDefense = bonuses.defense
        player.bonusHp = bonuses.hp
        player.bonusMp = bonuses.mp
        player.markDirty()
    }

    private fun sendEquipError(ctx: ChannelHandlerContext, reason: String) {
        val response = ClientEquipItemResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(reason)
            .build()
        ctx.writeAndFlush(Packet(Opcode.EQUIP_ITEM_VALUE, response.toByteArray()))
    }

    private fun sendUnequipError(ctx: ChannelHandlerContext, reason: String) {
        val response = ClientUnequipItemResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(reason)
            .build()
        ctx.writeAndFlush(Packet(Opcode.UNEQUIP_ITEM_VALUE, response.toByteArray()))
    }
}
