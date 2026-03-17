package com.flyagain.world.handler

import com.flyagain.common.grpc.GetInventoryRequest
import com.flyagain.common.grpc.InventoryDataServiceGrpcKt
import com.flyagain.common.grpc.MoveItemRequest
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ClientMoveItemRequest
import com.flyagain.common.proto.ClientMoveItemResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles inventory item move/swap requests from clients.
 *
 * Validates slot ranges server-side, delegates the actual move to
 * the database service via gRPC, and sends an inventory snapshot
 * back to the client on success.
 */
class MoveItemHandler(
    private val inventoryStub: InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub,
    private val broadcastService: BroadcastService
) {

    private val logger = LoggerFactory.getLogger(MoveItemHandler::class.java)

    companion object {
        private const val MIN_SLOT = 0
        private const val MAX_SLOT = 99
    }

    suspend fun handle(ctx: ChannelHandlerContext, player: PlayerEntity, request: ClientMoveItemRequest) {
        val fromSlot = request.fromSlot
        val toSlot = request.toSlot

        // Validate slot range
        if (fromSlot < MIN_SLOT || fromSlot > MAX_SLOT || toSlot < MIN_SLOT || toSlot > MAX_SLOT) {
            sendError(ctx, "Invalid slot range")
            return
        }

        // Validate slots are different
        if (fromSlot == toSlot) {
            sendError(ctx, "Source and destination slots are the same")
            return
        }

        try {
            // Call gRPC to move item
            val grpcRequest = MoveItemRequest.newBuilder()
                .setCharacterId(player.characterId)
                .setFromSlot(fromSlot)
                .setToSlot(toSlot)
                .build()

            val grpcResponse = inventoryStub.moveItem(grpcRequest)

            if (!grpcResponse.success) {
                sendError(ctx, grpcResponse.errorMessage.ifEmpty { "Failed to move item" })
                return
            }

            // Fetch updated inventory only — equipment never changes on a move
            val inventory = inventoryStub.getInventory(
                GetInventoryRequest.newBuilder().setCharacterId(player.characterId).build()
            )

            // Send success response
            val response = ClientMoveItemResponse.newBuilder()
                .setSuccess(true)
                .build()
            ctx.writeAndFlush(Packet(Opcode.MOVE_ITEM_VALUE, response.toByteArray()))

            // Send inventory update (empty equipment list — client merges, not replaces)
            broadcastService.sendInventoryUpdate(player, inventory.slotsList, emptyList())

            logger.debug("Player {} moved item from slot {} to slot {}", player.name, fromSlot, toSlot)
        } catch (e: Exception) {
            logger.error("Error moving item for player {}: {}", player.name, e.message, e)
            sendError(ctx, "Internal error")
        }
    }

    private fun sendError(ctx: ChannelHandlerContext, reason: String) {
        val response = ClientMoveItemResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(reason)
            .build()
        ctx.writeAndFlush(Packet(Opcode.MOVE_ITEM_VALUE, response.toByteArray()))
    }
}
