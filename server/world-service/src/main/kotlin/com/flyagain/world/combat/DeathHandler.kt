package com.flyagain.world.combat

import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import org.slf4j.LoggerFactory

/**
 * Resolves monster and player deaths.
 *
 * When a monster dies:
 * 1. Transition AI to DEAD, record death time, clear combat state.
 * 2. Award XP and gold to the killer.
 * 3. Roll loot (logged only; inventory delivery deferred to Phase 1.6).
 * 4. Broadcast XP gain and, on level-up, stats update.
 *
 * When a player dies:
 * 1. Full heal (HP = maxHp, MP = maxMp).
 * 2. Stop combat (auto-attack off, target cleared).
 * 3. Mark dirty for persistence.
 */
class DeathHandler(
    private val xpSystem: XpSystem,
    private val lootSystem: LootSystem,
    private val broadcastService: BroadcastService,
    private val entityManager: EntityManager
) {

    private val logger = LoggerFactory.getLogger(DeathHandler::class.java)

    /**
     * Resolve a monster death after the killing blow.
     *
     * @param monster  The monster that was killed.
     * @param killer   The player who dealt the killing blow.
     * @param channel  The zone channel the combat occurred in.
     */
    fun handleMonsterDeath(monster: MonsterEntity, killer: PlayerEntity, channel: ZoneChannel) {
        // 1. Transition monster to DEAD state
        monster.aiState = AIState.DEAD
        monster.deathTime = System.currentTimeMillis()
        monster.targetEntityId = null
        monster.hp = 0

        // 2. Stop killer's auto-attack if targeting the dead monster
        if (killer.targetEntityId == monster.entityId) {
            killer.autoAttacking = false
        }

        // 3. Award XP
        val xpResult = xpSystem.awardXp(killer, monster.xpReward)
        broadcastService.sendXpGain(killer, xpResult)

        logger.debug("Player {} gained {} XP from killing {} (level {})",
            killer.name, xpResult.xpGained, monster.name, killer.level)

        // 4. If the player leveled up, broadcast updated stats to nearby players
        if (xpResult.leveledUp) {
            broadcastService.broadcastEntityStatsUpdate(channel, killer)
            logger.info("Player {} leveled up to {} after killing {}",
                killer.name, killer.level, monster.name)
        }

        // 5. Award gold
        val goldDrop = lootSystem.calculateGoldDrop(monster.level)
        killer.gold += goldDrop
        killer.markDirty()

        // Note: GoldUpdate proto message does not exist yet; gold is persisted
        // via the normal write-back pipeline and will be sent on next full sync.

        logger.debug("Player {} received {} gold from killing {} (total: {})",
            killer.name, goldDrop, monster.name, killer.gold)

        // 6. Roll loot (Phase 1.6 will deliver items to inventory)
        val lootDrops = lootSystem.rollLoot(monster.definitionId)
        if (lootDrops.isNotEmpty()) {
            logger.debug("Loot rolled for monster {} (defId={}): {}",
                monster.name, monster.definitionId, lootDrops)
        }
    }

    /**
     * Resolve a player death. The player is immediately healed to full and
     * taken out of combat. Future phases may add respawn location logic,
     * XP penalties, or death animations.
     *
     * @param player  The player who died.
     * @param channel The zone channel the player is in.
     */
    fun handlePlayerDeath(player: PlayerEntity, channel: ZoneChannel) {
        // 1. Full heal
        player.hp = player.maxHp
        player.mp = player.maxMp

        // 2. Stop combat
        player.autoAttacking = false
        player.targetEntityId = null

        // 3. Mark dirty for persistence
        player.markDirty()

        logger.info("Player {} died in zone {}-{} and was fully healed",
            player.name, channel.zoneId, channel.channelId)
    }
}
