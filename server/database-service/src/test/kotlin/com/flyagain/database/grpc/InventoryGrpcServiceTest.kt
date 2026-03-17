package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.InventoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryGrpcServiceTest {

    private val inventoryRepo = mockk<InventoryRepository>()
    private val service = InventoryGrpcService(inventoryRepo)

    @Test
    fun `getInventory returns slots from repo`() = runTest {
        val slots = listOf(
            InventorySlot.newBuilder().setSlot(0).setItemId(1).setAmount(5).build()
        )
        coEvery { inventoryRepo.getInventory("c-1") } returns slots

        val result = service.getInventory(
            GetInventoryRequest.newBuilder().setCharacterId("c-1").build()
        )

        assertEquals(1, result.slotsList.size)
        assertEquals(1, result.slotsList[0].itemId)
    }

    @Test
    fun `getInventory returns empty for no items`() = runTest {
        coEvery { inventoryRepo.getInventory("c-1") } returns emptyList()

        val result = service.getInventory(
            GetInventoryRequest.newBuilder().setCharacterId("c-1").build()
        )

        assertEquals(0, result.slotsList.size)
    }

    @Test
    fun `getEquipment returns equipped items`() = runTest {
        val slots = listOf(
            EquipmentSlot.newBuilder().setSlotType(1).setItemId(10).build()
        )
        coEvery { inventoryRepo.getEquipment("c-1") } returns slots

        val result = service.getEquipment(
            GetEquipmentRequest.newBuilder().setCharacterId("c-1").build()
        )

        assertEquals(1, result.slotsList.size)
    }

    @Test
    fun `moveItem returns success on valid move`() = runTest {
        coEvery { inventoryRepo.moveItem("c-1", 0, 5) } returns true

        val result = service.moveItem(
            MoveItemRequest.newBuilder().setCharacterId("c-1").setFromSlot(0).setToSlot(5).build()
        )

        assertTrue(result.success)
    }

    @Test
    fun `moveItem returns failure when item not found`() = runTest {
        coEvery { inventoryRepo.moveItem("c-1", 0, 5) } returns false

        val result = service.moveItem(
            MoveItemRequest.newBuilder().setCharacterId("c-1").setFromSlot(0).setToSlot(5).build()
        )

        assertFalse(result.success)
        assertEquals("Item not found", result.errorMessage)
    }

    @Test
    fun `addItem returns assigned slot on success`() = runTest {
        coEvery { inventoryRepo.addItem("c-1", 42, 1) } returns 7

        val result = service.addItem(
            AddItemRequest.newBuilder().setCharacterId("c-1").setItemId(42).setAmount(1).build()
        )

        assertTrue(result.success)
        assertEquals(7, result.assignedSlot)
    }

    @Test
    fun `addItem returns failure when inventory full`() = runTest {
        coEvery { inventoryRepo.addItem("c-1", 42, 1) } throws
            NoSuchElementException("No free inventory slot")

        val result = service.addItem(
            AddItemRequest.newBuilder().setCharacterId("c-1").setItemId(42).setAmount(1).build()
        )

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("free inventory slot"))
    }

    @Test
    fun `removeItem calls repository`() = runTest {
        coEvery { inventoryRepo.removeItem("c-1", 3, 1) } returns Unit

        service.removeItem(
            RemoveItemRequest.newBuilder().setCharacterId("c-1").setSlot(3).setAmount(1).build()
        )

        coVerify(exactly = 1) { inventoryRepo.removeItem("c-1", 3, 1) }
    }

    @Test
    fun `equipItem returns success when valid`() = runTest {
        coEvery { inventoryRepo.equipItem("c-1", 0, 1) } returns true

        val result = service.equipItem(
            EquipItemRequest.newBuilder().setCharacterId("c-1").setInventorySlot(0).setEquipSlotType(1).build()
        )

        assertTrue(result.success)
    }

    @Test
    fun `equipItem returns failure when item not found`() = runTest {
        coEvery { inventoryRepo.equipItem("c-1", 99, 1) } returns false

        val result = service.equipItem(
            EquipItemRequest.newBuilder().setCharacterId("c-1").setInventorySlot(99).setEquipSlotType(1).build()
        )

        assertFalse(result.success)
        assertEquals("Item not found", result.errorMessage)
    }

    @Test
    fun `unequipItem returns success when equipped`() = runTest {
        coEvery { inventoryRepo.unequipItem("c-1", 1) } returns true

        val result = service.unequipItem(
            UnequipItemRequest.newBuilder().setCharacterId("c-1").setEquipSlotType(1).build()
        )

        assertTrue(result.success)
    }

    @Test
    fun `unequipItem returns failure when slot empty`() = runTest {
        coEvery { inventoryRepo.unequipItem("c-1", 1) } returns false

        val result = service.unequipItem(
            UnequipItemRequest.newBuilder().setCharacterId("c-1").setEquipSlotType(1).build()
        )

        assertFalse(result.success)
        assertEquals("No item equipped in that slot", result.errorMessage)
    }

    @Test
    fun `npcBuy returns not implemented`() = runTest {
        val result = service.npcBuy(NpcBuyRequest.getDefaultInstance())

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("Not implemented"))
    }

    @Test
    fun `npcSell returns not implemented`() = runTest {
        val result = service.npcSell(NpcSellRequest.getDefaultInstance())

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("Not implemented"))
    }
}
