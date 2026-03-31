package com.flyagain.world.handler

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
import com.flyagain.world.inventory.InventoryLockManager
import com.flyagain.world.inventory.ItemDefinitionCache
import com.flyagain.world.inventory.NpcShopRegistry
import com.flyagain.world.network.BroadcastService
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.sync.withLock
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
    private val broadcastService: BroadcastService,
    private val inventoryLockManager: InventoryLockManager
) {

    private val logger = LoggerFactory.getLogger(NpcShopHandler::class.java)

    companion object {
        private const val MIN_AMOUNT = 1
        private const val MAX_AMOUNT = 99
    }

    suspend fun handleBuy(ctx: ChannelHandlerContext, player: PlayerEntity, request: ClientNpcBuyRequest) {
        // npcEntityId currently maps directly to the NPC definition ID.
        // When runtime NPC entities are added, this will need a lookup step
        // to resolve the entity ID to the underlying definition ID.
        val npcEntityId = request.npcEntityId.toInt()
        val itemDefId = request.itemDefId
        val amount = request.amount

        logger.info("NPC buy request from {}: npcId={}, itemDefId={}, amount={}, playerPos=({},{},{}), gold={}",
            player.name, npcEntityId, itemDefId, amount, player.x, player.y, player.z, player.gold)

        // Validate NPC exists
        if (npcShopRegistry.getNpcDefinition(npcEntityId) == null) {
            sendBuyError(ctx, "Unknown NPC")
            return
        }

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

        val lock = inventoryLockManager.getLock(player.characterId)
        lock.withLock {
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
                    return@withLock
                }

                // Update player gold
                player.gold = grpcResponse.newGold
                player.markDirty()

                // Send gold update
                broadcastService.sendGoldUpdate(player, -totalCost)

                // Fetch updated inventory and filter to only the assigned slot
                val assignedSlot = grpcResponse.assignedSlot
                val inventory = inventoryStub.getInventory(
                    GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
                )
                val changedSlots = inventory.slotsList.filter { it.slot == assignedSlot }

                // Send success response
                val response = ClientNpcBuyResponse.newBuilder()
                    .setSuccess(true)
                    .setNewGold(player.gold)
                    .setAssignedSlot(assignedSlot)
                    .build()
                ctx.writeAndFlush(Packet(Opcode.NPC_BUY_VALUE, response.toByteArray()))

                // Send delta inventory update (only the slot that received the bought item)
                broadcastService.sendInventoryUpdate(player, changedSlots, emptyList())

                logger.debug("Player {} bought {}x item {} from NPC {}", player.name, amount, itemDefId, npcEntityId)
            } catch (e: Exception) {
                logger.error("Error buying item for player {}: {}", player.name, e.message, e)
                sendBuyError(ctx, "Internal error")
            }
        }
    }

    suspend fun handleSell(ctx: ChannelHandlerContext, player: PlayerEntity, request: ClientNpcSellRequest) {
        // npcEntityId currently maps directly to the NPC definition ID.
        // When runtime NPC entities are added, this will need a lookup step
        // to resolve the entity ID to the underlying definition ID.
        val npcEntityId = request.npcEntityId.toInt()
        val inventorySlot = request.inventorySlot
        val amount = request.amount

        // Validate NPC exists
        if (npcShopRegistry.getNpcDefinition(npcEntityId) == null) {
            sendSellError(ctx, "Unknown NPC")
            return
        }

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

        val lock = inventoryLockManager.getLock(player.characterId)
        lock.withLock {
            try {
                // Fetch current inventory to find item at slot
                val inventory = inventoryStub.getInventory(
                    GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
                )

                val invSlot = inventory.slotsList.find { it.slot == inventorySlot }
                if (invSlot == null || invSlot.itemId == 0) {
                    sendSellError(ctx, "No item in that slot")
                    return@withLock
                }

                // Lookup item definition
                val itemDef = itemCache.get(invSlot.itemId)
                if (itemDef == null) {
                    sendSellError(ctx, "Unknown item")
                    return@withLock
                }

                // Cannot sell quest items
                if (itemDef.type == ItemDefinitionCache.TYPE_QUEST_ITEM) {
                    sendSellError(ctx, "Quest items cannot be sold")
                    return@withLock
                }

                // Validate amount does not exceed what the player has
                if (amount > invSlot.amount) {
                    sendSellError(ctx, "Not enough items to sell")
                    return@withLock
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
                    return@withLock
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

                // Fetch updated inventory and filter to only the sold slot
                val updatedInventory = inventoryStub.getInventory(
                    GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
                )
                val changedSlots = updatedInventory.slotsList.filter { it.slot == inventorySlot }

                // If the slot was fully emptied it won't appear — send a cleared entry
                val clearedSlots = if (changedSlots.isEmpty()) {
                    listOf(com.flyagain.common.grpc.InventorySlot.newBuilder()
                        .setSlot(inventorySlot).setItemId(0).setAmount(0).build())
                } else emptyList()

                // Send success response
                val response = ClientNpcSellResponse.newBuilder()
                    .setSuccess(true)
                    .setNewGold(newGold)
                    .build()
                ctx.writeAndFlush(Packet(Opcode.NPC_SELL_VALUE, response.toByteArray()))

                // Send delta inventory update (only the slot that was sold from)
                broadcastService.sendInventoryUpdate(player, changedSlots + clearedSlots, emptyList())

                logger.debug("Player {} sold {}x item {} to NPC {}", player.name, amount, invSlot.itemId, npcEntityId)
            } catch (e: Exception) {
                logger.error("Error selling item for player {}: {}", player.name, e.message, e)
                sendSellError(ctx, "Internal error")
            }
        }
    }

    private fun sendBuyError(ctx: ChannelHandlerContext, reason: String) {
        logger.warn("NPC buy rejected: {}", reason)
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
