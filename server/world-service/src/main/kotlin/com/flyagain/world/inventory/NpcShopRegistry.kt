package com.flyagain.world.inventory

import com.flyagain.common.grpc.NpcDefinitionRecord
import com.flyagain.common.grpc.NpcShopItemRecord
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Registry of NPC definitions and their shop inventories.
 * Loaded at startup from the database service and used for shop interaction
 * validation including proximity checks.
 */
class NpcShopRegistry {
    private val logger = LoggerFactory.getLogger(NpcShopRegistry::class.java)

    companion object {
        const val NPC_INTERACTION_RANGE = 10.0f
    }

    private var npcDefs: Map<Int, NpcDefinitionRecord> = emptyMap()
    private var shopItems: Map<Int, List<Int>> = emptyMap()

    fun load(npcs: List<NpcDefinitionRecord>, items: List<NpcShopItemRecord>) {
        npcDefs = npcs.associateBy { it.id }
        shopItems = items.groupBy({ it.npcId }, { it.itemDefId })
        logger.info("Loaded {} NPC definitions and {} shop item entries", npcDefs.size, items.size)
    }

    fun getNpcDefinition(npcId: Int): NpcDefinitionRecord? = npcDefs[npcId]
    fun getNpcItems(npcId: Int): List<Int> = shopItems[npcId] ?: emptyList()
    fun npcSellsItem(npcId: Int, itemDefId: Int): Boolean = shopItems[npcId]?.contains(itemDefId) ?: false
    fun getAllNpcs(): Collection<NpcDefinitionRecord> = npcDefs.values

    fun isInRange(npcId: Int, playerX: Float, playerY: Float, playerZ: Float): Boolean {
        val npc = npcDefs[npcId] ?: return false
        val dx = npc.posX - playerX
        val dy = npc.posY - playerY
        val dz = npc.posZ - playerZ
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat() <= NPC_INTERACTION_RANGE
    }
}
