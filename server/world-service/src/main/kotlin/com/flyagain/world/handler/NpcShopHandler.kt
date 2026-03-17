package com.flyagain.world.handler

import com.flyagain.common.grpc.GetEquipmentRequest
import com.flyagain.common.grpc.GetInventoryRequest
import com.flyagain.common.grpc.InventoryDataServiceGrpcKt
import com.flyagain.common.grpc.NpcBuyRequest
import com.flyagain.common.grpc.NpcSellRequest
import com.flyagain.common.grpc.UpdateGoldRequest
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ClientNpcBuyRequest
import com.flyagain.common.proto.ClientNpcBuyResponse
import com.flyagain.common.proto.ClientNpcSellRequest
import com.flyagain.common.proto.ClientNpcSellResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.inventory.ItemDefinitionCache
import com.flyagain.world.inventory.NpcShopRegistry
import com.flyagain.world.network.BroadcastService
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles NPC shop buy and sell requests from clients.
 *
 * Performs server-authoritative validation including NPC proximity,
 * gold balance, item availability, level requirements, and quest item
 * restrictions before delegating to the database service via gRPC.
 */
class NpcShopHandler(
    private val inventoryStub: InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub,
    private val itemCache: ItemDefinitionCache,
    private val npcShopRegistry: NpcShopRegistry,
    private val broadcastService: BroadcastService
) {

    private val logger = LoggerFactory.getLogger(NpcShopHandler::class.java)

    companion object {
        private const val MIN_AMOUNT = 1
        private const val MAX_AMOUNT = 99
    }

    suspend fun handleBuy(ctx: ChannelHandlerContext, player: PlayerEntity, request: ClientNpcBuyRequest) {
        val npcEntityId = request.npcEntityId.toInt()
        val itemDefId = request.itemDefId
        val amount = request.amount

        // Validate amount
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            sendBuyError(ctx, "Invalid amount")
            return
        }

        // NPC proximity check
        if (!npcShopRegistry.isInRange(npcEntityId, player.x, player.y, player.z)) {
            sendBuyError(ctx, "NPC is not in range")
            return
        }

        // Check NPC sells this item
        if (!npcShopRegistry.npcSellsItem(npcEntityId, itemDefId)) {
            sendBuyError(ctx, "NPC does not sell this item")
            return
        }

        // Lookup item definition
        val itemDef = itemCache.get(itemDefId)
        if (itemDef == null) {
            sendBuyError(ctx, "Unknown item")
            return
        }

        // Check level requirement
        if (itemDef.levelReq > player.level) {
            sendBuyError(ctx, "Level requirement not met")
            return
        }

        // Check player has enough gold
        val totalCost = itemDef.buyPrice.toLong() * amount
        if (player.gold < totalCost) {
            sendBuyError(ctx, "Not enough gold")
            return
        }

        try {
            val newGold = player.gold - totalCost

            // Call gRPC to buy item
            val grpcRequest = NpcBuyRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setItemDefId(itemDefId)
                .setAmount(amount)
                .setCurrentGold(newGold)
                .build()

            val grpcResponse = inventoryStub.npcBuy(grpcRequest)

            if (!grpcResponse.success) {
                sendBuyError(ctx, grpcResponse.errorMessage.ifEmpty { "Failed to buy item" })
                return
            }

            // Update player gold
            player.gold = grpcResponse.newGold
            player.markDirty()

            // Send gold update
            broadcastService.sendGoldUpdate(player, -totalCost)

            // Fetch updated inventory and equipment
            val inventory = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            val equipment = inventoryStub.getEquipment(
                GetEquipmentRequest.newBuilder().setCharacterId(player.characterId).build()
            )

            // Send success response
            val response = ClientNpcBuyResponse.newBuilder()
                .setSuccess(true)
                .setNewGold(player.gold)
                .setAssignedSlot(grpcResponse.assignedSlot)
                .build()
            ctx.writeAndFlush(Packet(Opcode.NPC_BUY_VALUE, response.toByteArray()))

            // Send inventory update
            broadcastService.sendInventoryUpdate(player, inventory.slotsList, equipment.slotsList)

            logger.debug("Player {} bought {}x item {} from NPC {}", player.name, amount, itemDefId, npcEntityId)
        } catch (e: Exception) {
            logger.error("Error buying item for player {}: {}", player.name, e.message, e)
            sendBuyError(ctx, "Internal error")
        }
    }

    suspend fun handleSell(ctx: ChannelHandlerContext, player: PlayerEntity, request: ClientNpcSellRequest) {
        val npcEntityId = request.npcEntityId.toInt()
        val inventorySlot = request.inventorySlot
        val amount = request.amount

        // Validate amount
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            sendSellError(ctx, "Invalid amount")
            return
        }

        // NPC proximity check
        if (!npcShopRegistry.isInRange(npcEntityId, player.x, player.y, player.z)) {
            sendSellError(ctx, "NPC is not in range")
            return
        }

        try {
            // Fetch current inventory to find item at slot
            val inventory = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )

            val invSlot = inventory.slotsList.find { it.slot == inventorySlot }
            if (invSlot == null || invSlot.itemId == 0) {
                sendSellError(ctx, "No item in that slot")
                return
            }

            // Lookup item definition
            val itemDef = itemCache.get(invSlot.itemId)
            if (itemDef == null) {
                sendSellError(ctx, "Unknown item")
                return
            }

            // Cannot sell quest items
            if (itemDef.type == ItemDefinitionCache.TYPE_QUEST_ITEM) {
                sendSellError(ctx, "Quest items cannot be sold")
                return
            }

            // Validate amount does not exceed what the player has
            if (amount > invSlot.amount) {
                sendSellError(ctx, "Not enough items to sell")
                return
            }

            // Calculate sell price
            val totalSellPrice = itemDef.sellPrice.toLong() * amount

            // Call gRPC to remove item
            val grpcSellRequest = NpcSellRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setInventorySlot(inventorySlot)
                .setAmount(amount)
                .build()

            val grpcSellResponse = inventoryStub.npcSell(grpcSellRequest)

            if (!grpcSellResponse.success) {
                sendSellError(ctx, grpcSellResponse.errorMessage.ifEmpty { "Failed to sell item" })
                return
            }

            // Update gold via gRPC
            val newGold = player.gold + totalSellPrice
            val goldRequest = UpdateGoldRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setNewGold(newGold)
                .build()
            inventoryStub.updateGold(goldRequest)

            // Update player gold
            player.gold = newGold
            player.markDirty()

            // Send gold update
            broadcastService.sendGoldUpdate(player, totalSellPrice)

            // Fetch updated inventory and equipment
            val updatedInventory = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )
            val equipment = inventoryStub.getEquipment(
                GetEquipmentRequest.newBuilder().setCharacterId(player.characterId).build()
            )

            // Send success response
            val response = ClientNpcSellResponse.newBuilder()
                .setSuccess(true)
                .setNewGold(newGold)
                .build()
            ctx.writeAndFlush(Packet(Opcode.NPC_SELL_VALUE, response.toByteArray()))

            // Send inventory update
            broadcastService.sendInventoryUpdate(player, updatedInventory.slotsList, equipment.slotsList)

            logger.debug("Player {} sold {}x item {} to NPC {}", player.name, amount, invSlot.itemId, npcEntityId)
        } catch (e: Exception) {
            logger.error("Error selling item for player {}: {}", player.name, e.message, e)
            sendSellError(ctx, "Internal error")
        }
    }

    private fun sendBuyError(ctx: ChannelHandlerContext, reason: String) {
        val response = ClientNpcBuyResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(reason)
            .build()
        ctx.writeAndFlush(Packet(Opcode.NPC_BUY_VALUE, response.toByteArray()))
    }

    private fun sendSellError(ctx: ChannelHandlerContext, reason: String) {
        val response = ClientNpcSellResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(reason)
            .build()
        ctx.writeAndFlush(Packet(Opcode.NPC_SELL_VALUE, response.toByteArray()))
    }
}
