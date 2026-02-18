package com.flyagain.world.entity

import io.netty.channel.Channel

/**
 * Represents a player entity in the game world.
 * Holds all runtime state for a connected player character.
 */
data class PlayerEntity(
    val entityId: Long,
    val characterId: Long,
    val accountId: Long,
    val name: String,
    val characterClass: Int,

    // Position
    var x: Float,
    var y: Float,
    var z: Float,
    var rotation: Float = 0f,

    // Stats
    var level: Int = 1,
    var hp: Int = 100,
    var maxHp: Int = 100,
    var mp: Int = 50,
    var maxMp: Int = 50,
    var str: Int = 10,
    var sta: Int = 10,
    var dex: Int = 10,
    var int: Int = 10,
    var statPoints: Int = 0,
    var xp: Long = 0L,
    var xpToNextLevel: Long = 100L,
    var gold: Long = 0L,

    // Combat
    var targetEntityId: Long? = null,
    var autoAttacking: Boolean = false,
    var lastAttackTime: Long = 0L,

    // Cooldowns: skillId -> timestamp when cooldown expires
    val skillCooldowns: MutableMap<Int, Long> = mutableMapOf(),

    // Zone/Channel info
    var zoneId: Int = 0,
    var channelId: Int = 0,

    // Network
    var tcpChannel: Channel? = null,
    var sessionId: String = "",
    var hmacSecret: String = "",

    // Movement input (from latest client packet)
    var inputDx: Float = 0f,
    var inputDy: Float = 0f,
    var inputDz: Float = 0f,
    var isMoving: Boolean = false,

    // Tracking
    var lastHeartbeat: Long = System.currentTimeMillis(),
    var lastSaveTime: Long = System.currentTimeMillis(),
    var dirty: Boolean = false
) {
    /**
     * Computed attack power based on class and stats.
     * TODO: Refine per-class formulas in later phases.
     */
    fun getAttackPower(): Int {
        return str * 2 + level
    }

    /**
     * Computed defense based on stats and equipment.
     * TODO: Factor in equipment bonuses.
     */
    fun getDefense(): Int {
        return sta + level
    }

    /**
     * Move speed in units per tick.
     * TODO: Factor in buffs, equipment, mount status.
     */
    fun getMoveSpeed(): Float {
        return 5.0f + (dex * 0.05f)
    }

    /**
     * Mark this entity as having changed state that needs saving.
     */
    fun markDirty() {
        dirty = true
    }
}
