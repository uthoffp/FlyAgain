package com.flyagain.world.inventory

import com.flyagain.common.grpc.NpcDefinitionRecord
import com.flyagain.common.grpc.NpcShopItemRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NpcShopRegistryTest {

    private val registry = NpcShopRegistry()

    private fun npc(id: Int, name: String = "Merchant", x: Float = 0f, y: Float = 0f, z: Float = 0f): NpcDefinitionRecord =
        NpcDefinitionRecord.newBuilder()
            .setId(id)
            .setName(name)
            .setZoneId(1)
            .setNpcType(1)
            .setPosX(x)
            .setPosY(y)
            .setPosZ(z)
            .build()

    private fun shopItem(npcId: Int, itemDefId: Int): NpcShopItemRecord =
        NpcShopItemRecord.newBuilder()
            .setNpcId(npcId)
            .setItemDefId(itemDefId)
            .build()

    @Test
    fun `getNpcItems returns items for loaded NPC`() {
        registry.load(
            listOf(npc(1)),
            listOf(shopItem(1, 10), shopItem(1, 20), shopItem(1, 30))
        )
        val items = registry.getNpcItems(1)
        assertEquals(3, items.size)
        assertTrue(items.contains(10))
        assertTrue(items.contains(20))
        assertTrue(items.contains(30))
    }

    @Test
    fun `getNpcItems returns empty list for unknown NPC`() {
        registry.load(emptyList(), emptyList())
        assertEquals(emptyList(), registry.getNpcItems(999))
    }

    @Test
    fun `npcSellsItem returns true when NPC sells the item`() {
        registry.load(listOf(npc(1)), listOf(shopItem(1, 10)))
        assertTrue(registry.npcSellsItem(1, 10))
    }

    @Test
    fun `npcSellsItem returns false when NPC does not sell the item`() {
        registry.load(listOf(npc(1)), listOf(shopItem(1, 10)))
        assertFalse(registry.npcSellsItem(1, 99))
    }

    @Test
    fun `npcSellsItem returns false for unknown NPC`() {
        registry.load(emptyList(), emptyList())
        assertFalse(registry.npcSellsItem(999, 10))
    }

    @Test
    fun `getNpcDefinition returns definition for known NPC`() {
        registry.load(listOf(npc(1, "Blacksmith")), emptyList())
        val def = registry.getNpcDefinition(1)
        assertNotNull(def)
        assertEquals("Blacksmith", def.name)
    }

    @Test
    fun `getNpcDefinition returns null for unknown NPC`() {
        registry.load(emptyList(), emptyList())
        assertNull(registry.getNpcDefinition(999))
    }

    @Test
    fun `getAllNpcs returns all loaded NPCs`() {
        registry.load(listOf(npc(1), npc(2), npc(3)), emptyList())
        assertEquals(3, registry.getAllNpcs().size)
    }

    @Test
    fun `isInRange returns true when player is close`() {
        registry.load(listOf(npc(1, x = 100f, y = 0f, z = 100f)), emptyList())
        // Player at same position
        assertTrue(registry.isInRange(1, 100f, 0f, 100f))
    }

    @Test
    fun `isInRange returns true at edge of range`() {
        registry.load(listOf(npc(1, x = 0f, y = 0f, z = 0f)), emptyList())
        // Distance = 10.0 which is exactly the interaction range
        assertTrue(registry.isInRange(1, 10f, 0f, 0f))
    }

    @Test
    fun `isInRange returns false when player is far`() {
        registry.load(listOf(npc(1, x = 0f, y = 0f, z = 0f)), emptyList())
        // Distance = 50 units, well outside range
        assertFalse(registry.isInRange(1, 50f, 0f, 0f))
    }

    @Test
    fun `isInRange returns false for unknown NPC`() {
        registry.load(emptyList(), emptyList())
        assertFalse(registry.isInRange(999, 0f, 0f, 0f))
    }

    @Test
    fun `isInRange checks 3D distance`() {
        registry.load(listOf(npc(1, x = 0f, y = 0f, z = 0f)), emptyList())
        // sqrt(6^2 + 6^2 + 6^2) = sqrt(108) ≈ 10.39 > 10.0
        assertFalse(registry.isInRange(1, 6f, 6f, 6f))
        // sqrt(5^2 + 5^2 + 5^2) = sqrt(75) ≈ 8.66 < 10.0
        assertTrue(registry.isInRange(1, 5f, 5f, 5f))
    }
}
