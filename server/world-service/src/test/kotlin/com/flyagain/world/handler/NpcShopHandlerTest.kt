package com.flyagain.world.handler

import com.flyagain.common.grpc.EquipmentContents
import com.flyagain.common.grpc.InventoryContents
import com.flyagain.common.grpc.InventoryDataServiceGrpcKt
import com.flyagain.common.grpc.InventorySlot
import com.flyagain.common.grpc.ItemDefinitionRecord
import com.flyagain.common.grpc.NpcBuyRequest
import com.flyagain.common.grpc.NpcBuyResponse
import com.flyagain.common.grpc.NpcSellResponse
import com.flyagain.common.grpc.UpdateGoldResponse
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NpcShopHandlerTest {

    private val inventoryStub = mockk<InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub>()
    private val itemCache = mockk<ItemDefinitionCache>()
    private val npcShopRegistry = mockk<NpcShopRegistry>()
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val handler = NpcShopHandler(inventoryStub, itemCache, npcShopRegistry, broadcastService)

    private fun makePlayer(
        entityId: Long = 1L,
        characterId: String = "char-101",
        name: String = "TestPlayer",
        level: Int = 10,
        gold: Long = 10000L
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = characterId,
            accountId = "acc-201",
            name = name,
            characterClass = 1,
            x = 500f, y = 0f, z = 500f,
            hp = 500, maxHp = 500, level = level,
            gold = gold,
            zoneId = 1, channelId = 0
        )
    }

    private fun makeItemDef(
        id: Int = 1001,
        type: Int = ItemDefinitionCache.TYPE_CONSUMABLE,
        levelReq: Int = 1,
        buyPrice: Int = 100,
        sellPrice: Int = 50
    ): ItemDefinitionRecord {
        return ItemDefinitionRecord.newBuilder()
            .setId(id).setName("Test Item").setType(type)
            .setLevelReq(levelReq).setBuyPrice(buyPrice).setSellPrice(sellPrice)
            .build()
    }

    private fun stubGetInventoryAndEquipment() {
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.getDefaultInstance()
        coEvery { inventoryStub.getEquipment(any(), any()) } returns
            EquipmentContents.getDefaultInstance()
    }

    private fun captureBuyResponse(ctx: ChannelHandlerContext): ClientNpcBuyResponse {
        val packetSlot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(packetSlot)) }
        val packet = packetSlot.captured
        assertEquals(Opcode.NPC_BUY_VALUE, packet.opcode)
        return ClientNpcBuyResponse.parseFrom(packet.payload)
    }

    private fun captureSellResponse(ctx: ChannelHandlerContext): ClientNpcSellResponse {
        val packetSlot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(packetSlot)) }
        val packet = packetSlot.captured
        assertEquals(Opcode.NPC_SELL_VALUE, packet.opcode)
        return ClientNpcSellResponse.parseFrom(packet.payload)
    }

    // --- Buy Tests ---

    @Test
    fun `successfully buys item from NPC`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(gold = 10000L)

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        every { npcShopRegistry.npcSellsItem(1, 1001) } returns true
        every { itemCache.get(1001) } returns makeItemDef(buyPrice = 100)
        coEvery { inventoryStub.npcBuy(any(), any()) } returns
            NpcBuyResponse.newBuilder().setSuccess(true).setNewGold(9900L).setAssignedSlot(0).build()
        stubGetInventoryAndEquipment()

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(1001).setAmount(1).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertTrue(response.success)
        assertEquals(9900L, response.newGold)
        assertEquals(0, response.assignedSlot)
        assertEquals(9900L, player.gold)
        assertTrue(player.dirty)
    }

    @Test
    fun `rejects buy with invalid amount zero`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(1001).setAmount(0).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid amount", response.errorMessage)
    }

    @Test
    fun `rejects buy with amount over 99`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(1001).setAmount(100).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid amount", response.errorMessage)
    }

    @Test
    fun `rejects buy when NPC is out of range`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns false

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(1001).setAmount(1).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertFalse(response.success)
        assertEquals("NPC is not in range", response.errorMessage)
    }

    @Test
    fun `rejects buy when NPC does not sell item`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        every { npcShopRegistry.npcSellsItem(1, 9999) } returns false

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(9999).setAmount(1).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertFalse(response.success)
        assertEquals("NPC does not sell this item", response.errorMessage)
    }

    @Test
    fun `rejects buy when level requirement not met`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(level = 5)

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        every { npcShopRegistry.npcSellsItem(1, 1001) } returns true
        every { itemCache.get(1001) } returns makeItemDef(levelReq = 20)

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(1001).setAmount(1).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertFalse(response.success)
        assertEquals("Level requirement not met", response.errorMessage)
    }

    @Test
    fun `rejects buy when not enough gold`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(gold = 50L)

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        every { npcShopRegistry.npcSellsItem(1, 1001) } returns true
        every { itemCache.get(1001) } returns makeItemDef(buyPrice = 100)

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(1001).setAmount(1).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertFalse(response.success)
        assertEquals("Not enough gold", response.errorMessage)
    }

    @Test
    fun `rejects buy when gold insufficient for multiple items`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(gold = 250L)

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        every { npcShopRegistry.npcSellsItem(1, 1001) } returns true
        every { itemCache.get(1001) } returns makeItemDef(buyPrice = 100)

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(1001).setAmount(3).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertFalse(response.success)
        assertEquals("Not enough gold", response.errorMessage)
    }

    @Test
    fun `handles gRPC buy exception gracefully`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(gold = 10000L)

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        every { npcShopRegistry.npcSellsItem(1, 1001) } returns true
        every { itemCache.get(1001) } returns makeItemDef(buyPrice = 100)
        coEvery { inventoryStub.npcBuy(any(), any()) } throws RuntimeException("DB error")

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(1L).setItemDefId(1001).setAmount(1).build()

        handler.handleBuy(ctx, player, request)

        val response = captureBuyResponse(ctx)
        assertFalse(response.success)
        assertEquals("Internal error", response.errorMessage)
    }

    // --- Sell Tests ---

    @Test
    fun `successfully sells item to NPC`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(gold = 1000L)

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(0).setItemId(1001).setAmount(5).build())
                .build() andThen InventoryContents.getDefaultInstance()
        every { itemCache.get(1001) } returns makeItemDef(sellPrice = 50)
        coEvery { inventoryStub.npcSell(any(), any()) } returns
            NpcSellResponse.newBuilder().setSuccess(true).setNewGold(1050L).build()
        coEvery { inventoryStub.updateGold(any(), any()) } returns
            UpdateGoldResponse.newBuilder().setSuccess(true).build()
        coEvery { inventoryStub.getEquipment(any(), any()) } returns
            EquipmentContents.getDefaultInstance()

        val request = ClientNpcSellRequest.newBuilder()
            .setNpcEntityId(1L).setInventorySlot(0).setAmount(1).build()

        handler.handleSell(ctx, player, request)

        val response = captureSellResponse(ctx)
        assertTrue(response.success)
        assertEquals(1050L, response.newGold)
        assertEquals(1050L, player.gold)
        assertTrue(player.dirty)
    }

    @Test
    fun `rejects sell with invalid amount`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientNpcSellRequest.newBuilder()
            .setNpcEntityId(1L).setInventorySlot(0).setAmount(0).build()

        handler.handleSell(ctx, player, request)

        val response = captureSellResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid amount", response.errorMessage)
    }

    @Test
    fun `rejects sell when NPC is out of range`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns false

        val request = ClientNpcSellRequest.newBuilder()
            .setNpcEntityId(1L).setInventorySlot(0).setAmount(1).build()

        handler.handleSell(ctx, player, request)

        val response = captureSellResponse(ctx)
        assertFalse(response.success)
        assertEquals("NPC is not in range", response.errorMessage)
    }

    @Test
    fun `rejects sell when no item in slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.getDefaultInstance()

        val request = ClientNpcSellRequest.newBuilder()
            .setNpcEntityId(1L).setInventorySlot(5).setAmount(1).build()

        handler.handleSell(ctx, player, request)

        val response = captureSellResponse(ctx)
        assertFalse(response.success)
        assertEquals("No item in that slot", response.errorMessage)
    }

    @Test
    fun `rejects selling quest items`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(0).setItemId(5001).setAmount(1).build())
                .build()
        every { itemCache.get(5001) } returns makeItemDef(id = 5001, type = ItemDefinitionCache.TYPE_QUEST_ITEM)

        val request = ClientNpcSellRequest.newBuilder()
            .setNpcEntityId(1L).setInventorySlot(0).setAmount(1).build()

        handler.handleSell(ctx, player, request)

        val response = captureSellResponse(ctx)
        assertFalse(response.success)
        assertEquals("Quest items cannot be sold", response.errorMessage)
    }

    @Test
    fun `rejects sell when amount exceeds inventory`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(0).setItemId(1001).setAmount(3).build())
                .build()
        every { itemCache.get(1001) } returns makeItemDef()

        val request = ClientNpcSellRequest.newBuilder()
            .setNpcEntityId(1L).setInventorySlot(0).setAmount(5).build()

        handler.handleSell(ctx, player, request)

        val response = captureSellResponse(ctx)
        assertFalse(response.success)
        assertEquals("Not enough items to sell", response.errorMessage)
    }

    @Test
    fun `handles gRPC sell exception gracefully`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        every { npcShopRegistry.isInRange(1, player.x, player.y, player.z) } returns true
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(0).setItemId(1001).setAmount(5).build())
                .build()
        every { itemCache.get(1001) } returns makeItemDef()
        coEvery { inventoryStub.npcSell(any(), any()) } throws RuntimeException("Connection refused")

        val request = ClientNpcSellRequest.newBuilder()
            .setNpcEntityId(1L).setInventorySlot(0).setAmount(1).build()

        handler.handleSell(ctx, player, request)

        val response = captureSellResponse(ctx)
        assertFalse(response.success)
        assertEquals("Internal error", response.errorMessage)
    }

    @Test
    fun `passes correct gRPC buy request parameters`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(characterId = "char-999", gold = 10000L)

        every { npcShopRegistry.isInRange(5, player.x, player.y, player.z) } returns true
        every { npcShopRegistry.npcSellsItem(5, 2002) } returns true
        every { itemCache.get(2002) } returns makeItemDef(id = 2002, buyPrice = 200)

        val grpcSlot = slot<NpcBuyRequest>()
        coEvery { inventoryStub.npcBuy(capture(grpcSlot), any()) } returns
            NpcBuyResponse.newBuilder().setSuccess(true).setNewGold(9400L).setAssignedSlot(2).build()
        stubGetInventoryAndEquipment()

        val request = ClientNpcBuyRequest.newBuilder()
            .setNpcEntityId(5L).setItemDefId(2002).setAmount(3).build()

        handler.handleBuy(ctx, player, request)

        assertEquals("char-999", grpcSlot.captured.characterId)
        assertEquals(2002, grpcSlot.captured.itemDefId)
        assertEquals(3, grpcSlot.captured.amount)
        assertEquals(9400L, grpcSlot.captured.currentGold) // 10000 - (200 * 3) = 9400
    }
}
