package com.flyagain.world.inventory

import com.flyagain.common.grpc.EquipmentSlot

/**
 * Computes aggregate stat bonuses from a character's equipped items.
 * Enhancement levels increase stats by 10% per level (multiplicative on base).
 */
class EquipmentStatCalculator(private val itemCache: ItemDefinitionCache) {

    data class EquipmentBonuses(
        val attack: Int = 0,
        val defense: Int = 0,
        val hp: Int = 0,
        val mp: Int = 0
    )

    fun calculateBonuses(equipment: List<EquipmentSlot>): EquipmentBonuses {
        var totalAttack = 0
        var totalDefense = 0
        var totalHp = 0
        var totalMp = 0

        for (slot in equipment) {
            val itemDef = itemCache.get(slot.itemId) ?: continue
            val multiplier = 1.0 + slot.enhancement * 0.1
            totalAttack += (itemDef.baseAttack * multiplier).toInt()
            totalDefense += (itemDef.baseDefense * multiplier).toInt()
            totalHp += (itemDef.baseHp * multiplier).toInt()
            totalMp += (itemDef.baseMp * multiplier).toInt()
        }

        return EquipmentBonuses(totalAttack, totalDefense, totalHp, totalMp)
    }
}
