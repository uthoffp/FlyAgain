package com.flyagain.world.inventory

import com.flyagain.common.grpc.ItemDefinitionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ItemDefinitionCacheTest {

    private val cache = ItemDefinitionCache()

    private fun weapon(id: Int, name: String = "Sword"): ItemDefinitionRecord =
        ItemDefinitionRecord.newBuilder()
            .setId(id)
            .setName(name)
            .setType(ItemDefinitionCache.TYPE_WEAPON)
            .setBaseAttack(50)
            .build()

    private fun armor(id: Int, name: String = "Plate"): ItemDefinitionRecord =
        ItemDefinitionRecord.newBuilder()
            .setId(id)
            .setName(name)
            .setType(ItemDefinitionCache.TYPE_ARMOR)
            .setBaseDefense(30)
            .build()

    private fun consumable(id: Int, name: String = "Potion"): ItemDefinitionRecord =
        ItemDefinitionRecord.newBuilder()
            .setId(id)
            .setName(name)
            .setType(ItemDefinitionCache.TYPE_CONSUMABLE)
            .build()

    @Test
    fun `get returns loaded item by ID`() {
        val sword = weapon(1, "Iron Sword")
        cache.load(listOf(sword))
        val result = cache.get(1)
        assertEquals("Iron Sword", result?.name)
        assertEquals(1, result?.id)
    }

    @Test
    fun `get returns null for unknown item ID`() {
        cache.load(listOf(weapon(1)))
        assertNull(cache.get(999))
    }

    @Test
    fun `getAll returns all loaded items`() {
        cache.load(listOf(weapon(1), armor(2), consumable(3)))
        assertEquals(3, cache.getAll().size)
    }

    @Test
    fun `isWeapon returns true for weapon type`() {
        cache.load(listOf(weapon(1)))
        assertTrue(cache.isWeapon(1))
    }

    @Test
    fun `isWeapon returns false for non-weapon`() {
        cache.load(listOf(armor(1)))
        assertFalse(cache.isWeapon(1))
    }

    @Test
    fun `isArmor returns true for armor type`() {
        cache.load(listOf(armor(2)))
        assertTrue(cache.isArmor(2))
    }

    @Test
    fun `isArmor returns false for non-armor`() {
        cache.load(listOf(weapon(1)))
        assertFalse(cache.isArmor(1))
    }

    @Test
    fun `isConsumable returns true for consumable type`() {
        cache.load(listOf(consumable(3)))
        assertTrue(cache.isConsumable(3))
    }

    @Test
    fun `isConsumable returns false for non-consumable`() {
        cache.load(listOf(weapon(1)))
        assertFalse(cache.isConsumable(1))
    }

    @Test
    fun `type checks return false for unknown item ID`() {
        cache.load(emptyList())
        assertFalse(cache.isWeapon(999))
        assertFalse(cache.isArmor(999))
        assertFalse(cache.isConsumable(999))
    }

    @Test
    fun `load replaces previous definitions`() {
        cache.load(listOf(weapon(1, "Old Sword")))
        assertEquals("Old Sword", cache.get(1)?.name)

        cache.load(listOf(weapon(1, "New Sword")))
        assertEquals("New Sword", cache.get(1)?.name)
    }
}
