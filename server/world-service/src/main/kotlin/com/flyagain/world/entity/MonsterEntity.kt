package com.flyagain.world.entity

import com.flyagain.world.ai.AIState

/**
 * Represents a monster entity in the game world.
 * Each monster instance has runtime combat/AI state and a reference
 * back to its static definition ID.
 */
data class MonsterEntity(
    val entityId: Long,
    val definitionId: Int,
    val name: String,

    // Position
    var x: Float,
    var y: Float,
    var z: Float,

    // Spawn origin (for RETURN state)
    val spawnX: Float,
    val spawnY: Float,
    val spawnZ: Float,
    val spawnRadius: Float = 10f,

    // Stats (from definition)
    var hp: Int,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val level: Int,
    val xpReward: Int,
    val aggroRange: Float,
    val attackRange: Float,
    val attackSpeedMs: Int,
    val moveSpeed: Float,

    // Zone info
    var zoneId: Int = 0,
    var channelId: Int = 0,

    // AI state
    var aiState: AIState = AIState.IDLE,
    var targetEntityId: Long? = null,
    var lastAttackTime: Long = 0L,

    // Respawn
    val respawnMs: Int = 30000,
    var deathTime: Long = 0L,

    // Leash distance - max distance from spawn before returning
    val leashDistance: Float = 50f
) {
    /**
     * Check if this monster is alive.
     */
    fun isAlive(): Boolean = hp > 0 && aiState != AIState.DEAD

    /**
     * Calculate distance to a point.
     */
    fun distanceTo(targetX: Float, targetY: Float, targetZ: Float): Float {
        val dx = x - targetX
        val dy = y - targetY
        val dz = z - targetZ
        return kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

    /**
     * Calculate distance to the spawn point.
     */
    fun distanceToSpawn(): Float = distanceTo(spawnX, spawnY, spawnZ)

    /**
     * Check if the respawn timer has elapsed since death.
     */
    fun canRespawn(currentTime: Long): Boolean {
        return aiState == AIState.DEAD && (currentTime - deathTime) >= respawnMs
    }
}
