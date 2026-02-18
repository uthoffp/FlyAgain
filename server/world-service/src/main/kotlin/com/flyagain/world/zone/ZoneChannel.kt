package com.flyagain.world.zone

import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a single channel within a zone.
 * Each channel has its own set of players, monsters, and a spatial grid
 * for interest management. Max players per channel is configurable
 * (default 1000 as per architecture spec).
 */
class ZoneChannel(
    val zoneId: Int,
    val channelId: Int,
    val maxPlayers: Int = MAX_PLAYERS_PER_CHANNEL
) {

    companion object {
        const val MAX_PLAYERS_PER_CHANNEL = 1000
    }

    private val logger = LoggerFactory.getLogger(ZoneChannel::class.java)

    val spatialGrid = SpatialGrid()

    // Players in this channel, keyed by entity ID
    private val players = ConcurrentHashMap<Long, PlayerEntity>()

    // Monsters in this channel, keyed by entity ID
    private val monsters = ConcurrentHashMap<Long, MonsterEntity>()

    /**
     * Add a player to this channel.
     * Returns false if the channel is full.
     */
    fun addPlayer(player: PlayerEntity): Boolean {
        if (players.size >= maxPlayers) {
            logger.warn("Channel {}-{} is full ({} players), rejecting player {}",
                zoneId, channelId, players.size, player.name)
            return false
        }

        players[player.entityId] = player
        spatialGrid.addEntity(player.entityId, player.x, player.z)
        player.zoneId = zoneId
        player.channelId = channelId

        logger.debug("Player {} joined channel {}-{} (total: {})",
            player.name, zoneId, channelId, players.size)
        return true
    }

    /**
     * Remove a player from this channel.
     */
    fun removePlayer(entityId: Long): PlayerEntity? {
        val player = players.remove(entityId) ?: return null
        spatialGrid.removeEntity(entityId)
        logger.debug("Player {} left channel {}-{} (remaining: {})",
            player.name, zoneId, channelId, players.size)
        return player
    }

    /**
     * Add a monster to this channel.
     */
    fun addMonster(monster: MonsterEntity) {
        monsters[monster.entityId] = monster
        spatialGrid.addEntity(monster.entityId, monster.x, monster.z)
        monster.zoneId = zoneId
        monster.channelId = channelId
    }

    /**
     * Remove a monster from this channel.
     */
    fun removeMonster(entityId: Long): MonsterEntity? {
        val monster = monsters.remove(entityId) ?: return null
        spatialGrid.removeEntity(entityId)
        return monster
    }

    /**
     * Get a player by entity ID.
     */
    fun getPlayer(entityId: Long): PlayerEntity? = players[entityId]

    /**
     * Get a monster by entity ID.
     */
    fun getMonster(entityId: Long): MonsterEntity? = monsters[entityId]

    /**
     * Get all players in this channel.
     */
    fun getAllPlayers(): Collection<PlayerEntity> = players.values

    /**
     * Get all monsters in this channel.
     */
    fun getAllMonsters(): Collection<MonsterEntity> = monsters.values

    /**
     * Get the current player count.
     */
    fun getPlayerCount(): Int = players.size

    /**
     * Check if this channel has room for more players.
     */
    fun hasCapacity(): Boolean = players.size < maxPlayers

    /**
     * Get nearby entity IDs for a given position using the spatial grid.
     */
    fun getNearbyEntities(x: Float, z: Float): Set<Long> {
        return spatialGrid.getNearbyEntities(x, z)
    }

    /**
     * Update a player's position in the spatial grid.
     */
    fun updatePlayerPosition(entityId: Long, x: Float, z: Float) {
        spatialGrid.updateEntity(entityId, x, z)
    }

    /**
     * Update a monster's position in the spatial grid.
     */
    fun updateMonsterPosition(entityId: Long, x: Float, z: Float) {
        spatialGrid.updateEntity(entityId, x, z)
    }
}
