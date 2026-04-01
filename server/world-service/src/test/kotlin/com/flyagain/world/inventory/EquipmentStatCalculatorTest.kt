package com.flyagain.world.inventory

import com.flyagain.common.grpc.EquipmentSlot
import com.flyagain.common.grpc.ItemDefinitionRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class EquipmentStatCalculatorTest {

    private val cache = ItemDefinitionCache()
    private val calculator = EquipmentStatCalculator(cache)

    private fun loadItem(
        id: Int,
        type: Int = ItemDefinitionCache.TYPE_WEAPON,
        attack: Int = 0,
        defense: Int = 0,
        hp: Int = 0,
        mp: Int = 0
    ) {
        val existing = cache.getAll().toList()
        val newItem = ItemDefinitionRecord.newBuilder()
            .setId(id)
            .setName("Item$id")
            .setType(type)
            .setBaseAttack(attack)
            .setBaseDefense(defense)
            .setBaseHp(hp)
            .setBaseMp(mp)
            .build()
        cache.load(existing + newItem)
    }

    private fun slot(itemId: Int, enhancement: Int = 0, slotType: Int = 0): EquipmentSlot =
        EquipmentSlot.newBuilder()
            .setSlotType(slotType)
            .setItemId(itemId)
            .setEnhancement(enhancement)
            .build()

    @Test
    fun `empty equipment returns zero bonuses`() {
        val bonuses = calculator.calculateBonuses(emptyList())
        assertEquals(0, bonuses.attack)
        assertEquals(0, bonuses.defense)
        assertEquals(0, bonuses.hp)
        assertEquals(0, bonuses.mp)
    }

    @Test
    fun `weapon adds attack bonus`() {
        loadItem(id = 1, type = ItemDefinitionCache.TYPE_WEAPON, attack = 50)
        val bonuses = calculator.calculateBonuses(listOf(slot(1)))
        assertEquals(50, bonuses.attack)
        assertEquals(0, bonuses.defense)
    }

    @Test
    fun `armor adds defense bonus`() {
        loadItem(id = 2, type = ItemDefinitionCache.TYPE_ARMOR, defense = 30)
        val bonuses = calculator.calculateBonuses(listOf(slot(2)))
        assertEquals(0, bonuses.attack)
        assertEquals(30, bonuses.defense)
    }

    @Test
    fun `enhancement multiplies stats by 10 percent per level`() {
        loadItem(id = 1, attack = 100, defense = 50, hp = 200, mp = 100)
        // enhancement=3 -> multiplier=1.3
        val bonuses = calculator.calculateBonuses(listOf(slot(1, enhancement = 3)))
        assertEquals(130, bonuses.attack)   // 100 * 1.3
        assertEquals(65, bonuses.defense)   // 50 * 1.3
        assertEquals(260, bonuses.hp)       // 200 * 1.3
        assertEquals(130, bonuses.mp)       // 100 * 1.3
    }

    @Test
    fun `multiple pieces stack`() {
        loadItem(id = 1, attack = 40, defense = 0)
        loadItem(id = 2, attack = 0, defense = 20)
        loadItem(id = 3, attack = 10, defense = 10, hp = 50, mp = 25)
        val bonuses = calculator.calculateBonuses(listOf(
            slot(1, slotType = ItemDefinitionCache.EQUIP_SLOT_WEAPON),
            slot(2, slotType = ItemDefinitionCache.EQUIP_SLOT_CHEST),
            slot(3, slotType = ItemDefinitionCache.EQUIP_SLOT_LEGS)
        ))
        assertEquals(50, bonuses.attack)    // 40 + 0 + 10
        assertEquals(30, bonuses.defense)   // 0 + 20 + 10
        assertEquals(50, bonuses.hp)
        assertEquals(25, bonuses.mp)
    }

    @Test
    fun `unknown item ID is skipped`() {
        // Don't load item 999
        val bonuses = calculator.calculateBonuses(listOf(slot(999)))
        assertEquals(0, bonuses.attack)
        assertEquals(0, bonuses.defense)
        assertEquals(0, bonuses.hp)
        assertEquals(0, bonuses.mp)
    }

    @Test
    fun `zero enhancement uses base stats`() {
        loadItem(id = 5, attack = 100)
        val bonuses = calculator.calculateBonuses(listOf(slot(5, enhancement = 0)))
        assertEquals(100, bonuses.attack)
    }
}
