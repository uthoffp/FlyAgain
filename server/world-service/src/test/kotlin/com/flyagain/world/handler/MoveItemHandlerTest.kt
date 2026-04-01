package com.flyagain.world.handler

import com.flyagain.common.grpc.EquipmentContents
import com.flyagain.common.grpc.InventoryContents
import com.flyagain.common.grpc.InventoryDataServiceGrpcKt
import com.flyagain.common.grpc.MoveItemRequest
import com.flyagain.common.grpc.MoveItemResponse
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ClientMoveItemRequest
import com.flyagain.common.proto.ClientMoveItemResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.inventory.InventoryLockManager
import com.flyagain.world.network.BroadcastService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoveItemHandlerTest {

    private val inventoryStub = mockk<InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub>()
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val inventoryLockManager = InventoryLockManager()
    private val handler = MoveItemHandler(inventoryStub, broadcastService, inventoryLockManager)

    private fun makePlayer(
        entityId: Long = 1L,
        characterId: String = "char-101",
        name: String = "TestPlayer"
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = characterId,
            accountId = "acc-201",
            name = name,
            characterClass = 1,
            x = 500f, y = 0f, z = 500f,
            hp = 500, maxHp = 500, level = 10,
            zoneId = 1, channelId = 0
        )
    }

    private fun captureResponse(ctx: ChannelHandlerContext): ClientMoveItemResponse {
        val packetSlot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(packetSlot)) }
        val packet = packetSlot.captured
        assertEquals(Opcode.MOVE_ITEM_VALUE, packet.opcode)
        return ClientMoveItemResponse.parseFrom(packet.payload)
    }

    private fun stubMoveItemSuccess() {
        coEvery { inventoryStub.moveItem(any(), any()) } returns
            MoveItemResponse.newBuilder().setSuccess(true).build()
    }

    private fun stubGetInventoryAndEquipment() {
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.getDefaultInstance()
        coEvery { inventoryStub.getEquipment(any(), any()) } returns
            EquipmentContents.getDefaultInstance()
    }

    @Test
    fun `successfully moves item between slots`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        stubMoveItemSuccess()
        stubGetInventoryAndEquipment()

        val request = ClientMoveItemRequest.newBuilder()
            .setFromSlot(0)
            .setToSlot(5)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertTrue(response.success)

        coVerify { inventoryStub.moveItem(any(), any()) }
        verify { broadcastService.sendInventoryUpdate(player, any(), any()) }
    }

    @Test
    fun `rejects negative from slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientMoveItemRequest.newBuilder()
            .setFromSlot(-1)
            .setToSlot(5)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid slot range", response.errorMessage)
    }

    @Test
    fun `rejects slot over 99`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientMoveItemRequest.newBuilder()
            .setFromSlot(0)
            .setToSlot(100)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid slot range", response.errorMessage)
    }

    @Test
    fun `rejects same from and to slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientMoveItemRequest.newBuilder()
            .setFromSlot(3)
            .setToSlot(3)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertFalse(response.success)
        assertEquals("Source and destination slots are the same", response.errorMessage)
    }

    @Test
    fun `handles gRPC move failure`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        coEvery { inventoryStub.moveItem(any(), any()) } returns
            MoveItemResponse.newBuilder().setSuccess(false).setErrorMessage("Slot is empty").build()

        val request = ClientMoveItemRequest.newBuilder()
            .setFromSlot(0)
            .setToSlot(5)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertFalse(response.success)
        assertEquals("Slot is empty", response.errorMessage)
    }

    @Test
    fun `handles gRPC exception gracefully`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        coEvery { inventoryStub.moveItem(any(), any()) } throws RuntimeException("Connection lost")

        val request = ClientMoveItemRequest.newBuilder()
            .setFromSlot(0)
            .setToSlot(5)
            .build()

        handler.handle(ctx, player, request)

        val response = captureResponse(ctx)
        assertFalse(response.success)
        assertEquals("Internal error", response.errorMessage)
    }

    @Test
    fun `passes correct character ID to gRPC`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(characterId = "char-999")

        val grpcSlot = slot<MoveItemRequest>()
        coEvery { inventoryStub.moveItem(capture(grpcSlot), any()) } returns
            MoveItemResponse.newBuilder().setSuccess(true).build()
        stubGetInventoryAndEquipment()

        val request = ClientMoveItemRequest.newBuilder()
            .setFromSlot(2)
            .setToSlot(8)
            .build()

        handler.handle(ctx, player, request)

        assertEquals("char-999", grpcSlot.captured.characterId)
        assertEquals(2, grpcSlot.captured.fromSlot)
        assertEquals(8, grpcSlot.captured.toSlot)
    }
}
