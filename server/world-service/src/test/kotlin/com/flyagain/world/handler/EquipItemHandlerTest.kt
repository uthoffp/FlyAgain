package com.flyagain.world.handler

import com.flyagain.common.grpc.EquipItemResponse
import com.flyagain.common.grpc.EquipmentContents
import com.flyagain.common.grpc.EquipmentSlot
import com.flyagain.common.grpc.InventoryContents
import com.flyagain.common.grpc.InventoryDataServiceGrpcKt
import com.flyagain.common.grpc.InventorySlot
import com.flyagain.common.grpc.ItemDefinitionRecord
import com.flyagain.common.grpc.UnequipItemResponse
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
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
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

class EquipItemHandlerTest {

    private val inventoryStub = mockk<InventoryDataServiceGrpcKt.InventoryDataServiceCoroutineStub>()
    private val itemCache = mockk<ItemDefinitionCache>()
    private val statCalculator = mockk<EquipmentStatCalculator>()
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val zoneManager = mockk<ZoneManager>(relaxed = true)
    private val handler = EquipItemHandler(inventoryStub, itemCache, statCalculator, broadcastService, zoneManager)

    private fun makePlayer(
        entityId: Long = 1L,
        characterId: String = "char-101",
        name: String = "TestPlayer",
        level: Int = 10,
        characterClass: Int = 1
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = characterId,
            accountId = "acc-201",
            name = name,
            characterClass = characterClass,
            x = 500f, y = 0f, z = 500f,
            hp = 500, maxHp = 500, level = level,
            zoneId = 1, channelId = 0
        )
    }

    private fun makeWeaponDef(id: Int = 1001, levelReq: Int = 1, classReq: Int = 0): ItemDefinitionRecord {
        return ItemDefinitionRecord.newBuilder()
            .setId(id).setName("Test Sword").setType(ItemDefinitionCache.TYPE_WEAPON)
            .setLevelReq(levelReq).setClassReq(classReq).setBaseAttack(50)
            .setBuyPrice(100).setSellPrice(50).build()
    }

    private fun makeArmorDef(id: Int = 2001, levelReq: Int = 1, classReq: Int = 0): ItemDefinitionRecord {
        return ItemDefinitionRecord.newBuilder()
            .setId(id).setName("Test Helm").setType(ItemDefinitionCache.TYPE_ARMOR)
            .setLevelReq(levelReq).setClassReq(classReq).setBaseDefense(30)
            .setBuyPrice(80).setSellPrice(40).build()
    }

    private fun makeConsumableDef(id: Int = 3001): ItemDefinitionRecord {
        return ItemDefinitionRecord.newBuilder()
            .setId(id).setName("Health Potion").setType(ItemDefinitionCache.TYPE_CONSUMABLE)
            .setBuyPrice(10).setSellPrice(5).build()
    }

    private fun stubInventoryWithItem(itemId: Int, slot: Int = 0) {
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(slot).setItemId(itemId).setAmount(1).build())
                .build()
    }

    /**
     * Stubs the calls that happen after a successful equip/unequip:
     * getEquipment, stat calculation, zone channel lookup.
     * NOTE: Does NOT stub getInventory because success equip tests need
     * the first getInventory call to return the item, then the second to return empty.
     */
    private fun stubPostEquipDefaults() {
        coEvery { inventoryStub.getEquipment(any(), any()) } returns
            EquipmentContents.getDefaultInstance()
        every { statCalculator.calculateBonuses(any()) } returns
            EquipmentStatCalculator.EquipmentBonuses()
        every { zoneManager.getChannel(any(), any()) } returns mockk<ZoneChannel>()
    }

    private fun captureEquipResponse(ctx: ChannelHandlerContext): ClientEquipItemResponse {
        val packetSlot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(packetSlot)) }
        val packet = packetSlot.captured
        assertEquals(Opcode.EQUIP_ITEM_VALUE, packet.opcode)
        return ClientEquipItemResponse.parseFrom(packet.payload)
    }

    private fun captureUnequipResponse(ctx: ChannelHandlerContext): ClientUnequipItemResponse {
        val packetSlot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(packetSlot)) }
        val packet = packetSlot.captured
        assertEquals(Opcode.UNEQUIP_ITEM_VALUE, packet.opcode)
        return ClientUnequipItemResponse.parseFrom(packet.payload)
    }

    // --- Equip Tests ---

    @Test
    fun `successfully equips weapon to weapon slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        // First getInventory returns item, second returns empty (post-equip refresh)
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(0).setItemId(1001).setAmount(1).build())
                .build() andThen InventoryContents.getDefaultInstance()
        every { itemCache.get(1001) } returns makeWeaponDef()
        coEvery { inventoryStub.equipItem(any(), any()) } returns
            EquipItemResponse.newBuilder().setSuccess(true).build()
        stubPostEquipDefaults()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON)
            .build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertTrue(response.success)
        verify { broadcastService.sendInventoryUpdate(player, any(), any()) }
    }

    @Test
    fun `successfully equips armor to head slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(3).setItemId(2001).setAmount(1).build())
                .build() andThen InventoryContents.getDefaultInstance()
        every { itemCache.get(2001) } returns makeArmorDef()
        coEvery { inventoryStub.equipItem(any(), any()) } returns
            EquipItemResponse.newBuilder().setSuccess(true).build()
        stubPostEquipDefaults()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(3)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_HEAD)
            .build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertTrue(response.success)
    }

    @Test
    fun `rejects equip with invalid inventory slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(-1).setEquipSlotType(0).build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid inventory slot", response.errorMessage)
    }

    @Test
    fun `rejects equip with invalid equip slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0).setEquipSlotType(7).build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid equipment slot", response.errorMessage)
    }

    @Test
    fun `rejects equip when no item in slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.getDefaultInstance()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0).setEquipSlotType(0).build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("No item in that slot", response.errorMessage)
    }

    @Test
    fun `rejects equip when level requirement not met`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(level = 5)

        stubInventoryWithItem(1001, slot = 0)
        every { itemCache.get(1001) } returns makeWeaponDef(levelReq = 20)

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON)
            .build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Level requirement not met", response.errorMessage)
    }

    @Test
    fun `rejects equip when class requirement not met`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(characterClass = 1)

        stubInventoryWithItem(1001, slot = 0)
        every { itemCache.get(1001) } returns makeWeaponDef(classReq = 2)

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON)
            .build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Class requirement not met", response.errorMessage)
    }

    @Test
    fun `allows equip when class requirement is 0 (any class)`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer(characterClass = 3)

        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(0).setItemId(1001).setAmount(1).build())
                .build() andThen InventoryContents.getDefaultInstance()
        every { itemCache.get(1001) } returns makeWeaponDef(classReq = 0)
        coEvery { inventoryStub.equipItem(any(), any()) } returns
            EquipItemResponse.newBuilder().setSuccess(true).build()
        stubPostEquipDefaults()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON)
            .build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertTrue(response.success)
    }

    @Test
    fun `rejects weapon in armor slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        stubInventoryWithItem(1001, slot = 0)
        every { itemCache.get(1001) } returns makeWeaponDef()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_HEAD)
            .build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Weapons can only be equipped in the weapon slot", response.errorMessage)
    }

    @Test
    fun `rejects armor in weapon slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        stubInventoryWithItem(2001, slot = 0)
        every { itemCache.get(2001) } returns makeArmorDef()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON)
            .build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Armor cannot be equipped in the weapon slot", response.errorMessage)
    }

    @Test
    fun `rejects equipping consumable items`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        stubInventoryWithItem(3001, slot = 0)
        every { itemCache.get(3001) } returns makeConsumableDef()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0).setEquipSlotType(0).build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("This item cannot be equipped", response.errorMessage)
    }

    @Test
    fun `recalculates stats after equip`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        stubInventoryWithItem(1001, slot = 0)
        every { itemCache.get(1001) } returns makeWeaponDef()
        coEvery { inventoryStub.equipItem(any(), any()) } returns
            EquipItemResponse.newBuilder().setSuccess(true).build()

        val weaponSlot = EquipmentSlot.newBuilder()
            .setSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON).setItemId(1001).build()
        // After equip, getEquipment returns the equipped weapon; getInventory is called twice
        // (once before equip to validate, once after to send update)
        coEvery { inventoryStub.getEquipment(any(), any()) } returns
            EquipmentContents.newBuilder().addSlots(weaponSlot).build()
        // First call returns item in slot, second call returns empty (item moved to equipment)
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.newBuilder()
                .addSlots(InventorySlot.newBuilder().setSlot(0).setItemId(1001).setAmount(1).build())
                .build() andThen InventoryContents.getDefaultInstance()
        every { statCalculator.calculateBonuses(any()) } returns
            EquipmentStatCalculator.EquipmentBonuses(attack = 50, defense = 10, hp = 20, mp = 5)
        every { zoneManager.getChannel(any(), any()) } returns mockk<ZoneChannel>()

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON)
            .build()

        handler.handleEquip(ctx, player, request)

        assertEquals(50, player.bonusAttack)
        assertEquals(10, player.bonusDefense)
        assertEquals(20, player.bonusHp)
        assertEquals(5, player.bonusMp)
        assertTrue(player.dirty)
    }

    @Test
    fun `handles gRPC equip exception gracefully`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        stubInventoryWithItem(1001, slot = 0)
        every { itemCache.get(1001) } returns makeWeaponDef()
        coEvery { inventoryStub.equipItem(any(), any()) } throws RuntimeException("Connection lost")

        val request = ClientEquipItemRequest.newBuilder()
            .setInventorySlot(0)
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON)
            .build()

        handler.handleEquip(ctx, player, request)

        val response = captureEquipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Internal error", response.errorMessage)
    }

    // --- Unequip Tests ---

    @Test
    fun `successfully unequips item`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        coEvery { inventoryStub.unequipItem(any(), any()) } returns
            UnequipItemResponse.newBuilder().setSuccess(true).build()
        stubPostEquipDefaults()
        coEvery { inventoryStub.getInventory(any(), any()) } returns
            InventoryContents.getDefaultInstance()

        val request = ClientUnequipItemRequest.newBuilder()
            .setEquipSlotType(ItemDefinitionCache.EQUIP_SLOT_WEAPON)
            .build()

        handler.handleUnequip(ctx, player, request)

        val response = captureUnequipResponse(ctx)
        assertTrue(response.success)
        verify { broadcastService.sendInventoryUpdate(player, any(), any()) }
    }

    @Test
    fun `rejects unequip with invalid slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientUnequipItemRequest.newBuilder().setEquipSlotType(7).build()

        handler.handleUnequip(ctx, player, request)

        val response = captureUnequipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid equipment slot", response.errorMessage)
    }

    @Test
    fun `rejects unequip with negative slot`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        val request = ClientUnequipItemRequest.newBuilder().setEquipSlotType(-1).build()

        handler.handleUnequip(ctx, player, request)

        val response = captureUnequipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Invalid equipment slot", response.errorMessage)
    }

    @Test
    fun `handles gRPC unequip failure`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        coEvery { inventoryStub.unequipItem(any(), any()) } returns
            UnequipItemResponse.newBuilder().setSuccess(false).setErrorMessage("No item equipped").build()

        val request = ClientUnequipItemRequest.newBuilder().setEquipSlotType(0).build()

        handler.handleUnequip(ctx, player, request)

        val response = captureUnequipResponse(ctx)
        assertFalse(response.success)
        assertEquals("No item equipped", response.errorMessage)
    }

    @Test
    fun `handles gRPC unequip exception gracefully`() = runTest {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val player = makePlayer()

        coEvery { inventoryStub.unequipItem(any(), any()) } throws RuntimeException("Timeout")

        val request = ClientUnequipItemRequest.newBuilder().setEquipSlotType(0).build()

        handler.handleUnequip(ctx, player, request)

        val response = captureUnequipResponse(ctx)
        assertFalse(response.success)
        assertEquals("Internal error", response.errorMessage)
    }
}
