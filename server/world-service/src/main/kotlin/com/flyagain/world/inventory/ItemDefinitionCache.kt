package com.flyagain.world.inventory

import com.flyagain.common.grpc.ItemDefinitionRecord
import org.slf4j.LoggerFactory

/**
 * In-memory cache of item definitions loaded at startup from the database service.
 * Provides fast lookup of item metadata by ID and type-checking helpers.
 */
class ItemDefinitionCache {
    private val logger = LoggerFactory.getLogger(ItemDefinitionCache::class.java)
    private var items: Map<Int, ItemDefinitionRecord> = emptyMap()

    companion object {
        const val TYPE_WEAPON = 0
        const val TYPE_ARMOR = 1
        const val TYPE_QUEST_ITEM = 2
        const val TYPE_CONSUMABLE = 3

        const val EQUIP_SLOT_HEAD = 0
        const val EQUIP_SLOT_CHEST = 1
        const val EQUIP_SLOT_LEGS = 2
        const val EQUIP_SLOT_FEET = 3
        const val EQUIP_SLOT_HANDS = 4
        const val EQUIP_SLOT_BACK = 5
        const val EQUIP_SLOT_WEAPON = 6
    }

    fun load(definitions: List<ItemDefinitionRecord>) {
        items = definitions.associateBy { it.id }
        logger.info("Loaded {} item definitions into cache", items.size)
    }

    fun get(itemId: Int): ItemDefinitionRecord? = items[itemId]
    fun getAll(): Collection<ItemDefinitionRecord> = items.values
    fun isWeapon(itemId: Int): Boolean = items[itemId]?.type == TYPE_WEAPON
    fun isArmor(itemId: Int): Boolean = items[itemId]?.type == TYPE_ARMOR
    fun isConsumable(itemId: Int): Boolean = items[itemId]?.type == TYPE_CONSUMABLE
}
