package com.flyagain.world.ai

import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.zone.ZoneChannel
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Processes AI behavior for all monsters in a zone channel.
 * Implements a simple state machine per monster:
 *
 * IDLE   -> AGGRO   : Player enters aggro range
 * AGGRO  -> ATTACK  : Monster reaches attack range
 * AGGRO  -> RETURN  : Target lost or leash exceeded
 * ATTACK -> AGGRO   : Target moves out of attack range
 * ATTACK -> RETURN  : Target dead or leash exceeded
 * RETURN -> IDLE    : Monster reaches spawn point
 * DEAD   -> IDLE    : Respawn timer expired (reset HP, return to spawn)
 */
class MonsterAI(
    private val entityManager: EntityManager,
    private val combatEngine: CombatEngine
) {

    private val logger = LoggerFactory.getLogger(MonsterAI::class.java)

    companion object {
        const val RETURN_SPEED_MULTIPLIER = 2.0f
        const val SPAWN_REACH_THRESHOLD = 2.0f
    }

    /**
     * Collected events from a single AI tick, to be broadcast by the game loop.
     */
    data class AITickResult(
        val damageEvents: MutableList<CombatEngine.DamageResult> = mutableListOf(),
        val respawnedMonsters: MutableList<MonsterEntity> = mutableListOf()
    )

    /**
     * Update AI for all monsters in a channel. Called once per game tick.
     */
    fun updateChannel(channel: ZoneChannel, tickDeltaMs: Long): AITickResult {
        val result = AITickResult()
        val currentTime = System.currentTimeMillis()

        for (monster in channel.getAllMonsters()) {
            when (monster.aiState) {
                AIState.IDLE -> updateIdle(monster, channel)
                AIState.AGGRO -> updateAggro(monster, channel, tickDeltaMs)
                AIState.ATTACK -> updateAttack(monster, channel, currentTime, result)
                AIState.RETURN -> updateReturn(monster, channel, tickDeltaMs)
                AIState.DEAD -> updateDead(monster, channel, currentTime, result)
            }
        }

        return result
    }

    /**
     * IDLE state: scan for nearby players within aggro range.
     */
    private fun updateIdle(monster: MonsterEntity, channel: ZoneChannel) {
        val nearbyEntityIds = channel.getNearbyEntities(monster.x, monster.z)

        for (entityId in nearbyEntityIds) {
            val player = entityManager.getPlayer(entityId) ?: continue
            if (player.hp <= 0) continue

            val distance = monster.distanceTo(player.x, player.y, player.z)
            if (distance <= monster.aggroRange) {
                monster.aiState = AIState.AGGRO
                monster.targetEntityId = player.entityId
                logger.trace("Monster {} aggroed on player {} (distance={})",
                    monster.name, player.name, distance)
                return
            }
        }
    }

    /**
     * AGGRO state: pursue the target. Transition to ATTACK if in range, RETURN if leashed.
     */
    private fun updateAggro(monster: MonsterEntity, channel: ZoneChannel, tickDeltaMs: Long) {
        val targetId = monster.targetEntityId
        val target = if (targetId != null) entityManager.getPlayer(targetId) else null

        // Target lost or dead
        if (target == null || target.hp <= 0) {
            monster.aiState = AIState.RETURN
            monster.targetEntityId = null
            return
        }

        // Leash check
        if (monster.distanceToSpawn() > monster.leashDistance) {
            monster.aiState = AIState.RETURN
            monster.targetEntityId = null
            return
        }

        val distance = monster.distanceTo(target.x, target.y, target.z)

        // Close enough to attack
        if (distance <= monster.attackRange) {
            monster.aiState = AIState.ATTACK
            return
        }

        // Move toward target
        moveToward(monster, target.x, target.y, target.z, monster.moveSpeed, tickDeltaMs)
        channel.updateMonsterPosition(monster.entityId, monster.x, monster.z)
    }

    /**
     * ATTACK state: deal damage on cooldown. Chase if target moves out of range.
     */
    private fun updateAttack(
        monster: MonsterEntity,
        channel: ZoneChannel,
        currentTime: Long,
        result: AITickResult
    ) {
        val targetId = monster.targetEntityId
        val target = if (targetId != null) entityManager.getPlayer(targetId) else null

        // Target lost or dead
        if (target == null || target.hp <= 0) {
            monster.aiState = AIState.RETURN
            monster.targetEntityId = null
            return
        }

        // Leash check
        if (monster.distanceToSpawn() > monster.leashDistance) {
            monster.aiState = AIState.RETURN
            monster.targetEntityId = null
            return
        }

        val distance = monster.distanceTo(target.x, target.y, target.z)

        // Target moved out of attack range, chase
        if (distance > monster.attackRange * 1.2f) {
            monster.aiState = AIState.AGGRO
            return
        }

        // Attack on cooldown
        if (currentTime - monster.lastAttackTime >= monster.attackSpeedMs) {
            monster.lastAttackTime = currentTime
            val damageResult = combatEngine.calculateMonsterVsPlayer(monster, target)
            result.damageEvents.add(damageResult)
        }
    }

    /**
     * RETURN state: move back toward spawn point. Full heal on arrival.
     */
    private fun updateReturn(monster: MonsterEntity, channel: ZoneChannel, tickDeltaMs: Long) {
        val distToSpawn = monster.distanceToSpawn()

        if (distToSpawn <= SPAWN_REACH_THRESHOLD) {
            // Arrived at spawn
            monster.x = monster.spawnX
            monster.y = monster.spawnY
            monster.z = monster.spawnZ
            monster.hp = monster.maxHp
            monster.aiState = AIState.IDLE
            monster.targetEntityId = null
            channel.updateMonsterPosition(monster.entityId, monster.x, monster.z)
            return
        }

        // Move toward spawn at increased speed
        moveToward(
            monster, monster.spawnX, monster.spawnY, monster.spawnZ,
            monster.moveSpeed * RETURN_SPEED_MULTIPLIER, tickDeltaMs
        )
        channel.updateMonsterPosition(monster.entityId, monster.x, monster.z)
    }

    /**
     * DEAD state: check if respawn timer has elapsed.
     */
    private fun updateDead(
        monster: MonsterEntity,
        channel: ZoneChannel,
        currentTime: Long,
        result: AITickResult
    ) {
        if (monster.canRespawn(currentTime)) {
            // Respawn at spawn point with full HP
            monster.x = monster.spawnX
            monster.y = monster.spawnY
            monster.z = monster.spawnZ
            monster.hp = monster.maxHp
            monster.aiState = AIState.IDLE
            monster.targetEntityId = null
            monster.lastAttackTime = 0L
            channel.updateMonsterPosition(monster.entityId, monster.x, monster.z)
            result.respawnedMonsters.add(monster)

            logger.trace("Monster {} respawned at ({}, {}, {})",
                monster.name, monster.spawnX, monster.spawnY, monster.spawnZ)
        }
    }

    /**
     * Move a monster toward a target point at a given speed.
     */
    private fun moveToward(
        monster: MonsterEntity,
        targetX: Float,
        targetY: Float,
        targetZ: Float,
        speed: Float,
        tickDeltaMs: Long
    ) {
        val dx = targetX - monster.x
        val dy = targetY - monster.y
        val dz = targetZ - monster.z
        val dist = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()

        if (dist < 0.01f) return

        val moveDistance = speed * (tickDeltaMs / 1000f)
        val ratio = (moveDistance / dist).coerceAtMost(1.0f)

        monster.x += dx * ratio
        monster.y += dy * ratio
        monster.z += dz * ratio
    }
}
