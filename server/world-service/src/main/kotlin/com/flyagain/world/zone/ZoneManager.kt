package com.flyagain.world.zone

import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.common.grpc.MonsterSpawnRecord
import com.flyagain.common.grpc.MonsterDefinitionRecord
import org.slf4j.LoggerFactory

/**
 * Manages all zones and their channels in the game world.
 * Handles zone creation, channel auto-creation, and player/monster placement.
 *
 * Zone IDs follow the architecture naming scheme:
 * - Zone 1: Aerheim (starting town)
 * - Zone 2: Gruene Ebene (green plains)
 * - Zone 3: Dunkler Wald (dark forest)
 */
class ZoneManager(
    private val entityManager: EntityManager
) {

    companion object {
        // Zone definitions
        const val ZONE_AERHEIM = 1
        const val ZONE_GRUENE_EBENE = 2
        const val ZONE_DUNKLER_WALD = 3

        val ZONE_NAMES = mapOf(
            ZONE_AERHEIM to "Aerheim",
            ZONE_GRUENE_EBENE to "Gruene Ebene",
            ZONE_DUNKLER_WALD to "Dunkler Wald"
        )

        // Default spawn position for new players (Aerheim town center)
        const val DEFAULT_SPAWN_X = 500f
        const val DEFAULT_SPAWN_Y = 0f
        const val DEFAULT_SPAWN_Z = 500f
    }

    private val logger = LoggerFactory.getLogger(ZoneManager::class.java)

    // Map of zoneId -> list of channels
    private val zones = HashMap<Int, MutableList<ZoneChannel>>()

    /**
     * Initialize all zones with at least one channel each.
     */
    fun initialize() {
        logger.info("Initializing zones...")

        for ((zoneId, zoneName) in ZONE_NAMES) {
            val channels = mutableListOf<ZoneChannel>()
            // Create initial channel (channel 0) for each zone
            channels.add(ZoneChannel(zoneId, channelId = 0))
            zones[zoneId] = channels
            logger.info("Zone '{}' (id={}) initialized with 1 channel", zoneName, zoneId)
        }

        logger.info("Zone initialization complete: {} zones", zones.size)
    }

    /**
     * Spawn monsters in the appropriate zones and channels based on spawn data.
     * Called during startup after loading game data from the database service.
     */
    fun spawnMonsters(
        spawnRecords: List<MonsterSpawnRecord>,
        monsterDefs: Map<Int, MonsterDefinitionRecord>
    ) {
        var totalSpawned = 0

        for (spawn in spawnRecords) {
            val def = monsterDefs[spawn.monsterId] ?: continue
            val channels = zones[spawn.mapId] ?: continue

            for (channel in channels) {
                for (i in 0 until spawn.spawnCount) {
                    // Scatter monsters within spawn radius
                    val offsetX = (Math.random().toFloat() - 0.5f) * 2 * spawn.spawnRadius
                    val offsetZ = (Math.random().toFloat() - 0.5f) * 2 * spawn.spawnRadius
                    val posX = spawn.posX + offsetX
                    val posZ = spawn.posZ + offsetZ

                    val monster = MonsterEntity(
                        entityId = entityManager.nextMonsterId(),
                        definitionId = def.id,
                        name = def.name,
                        x = posX,
                        y = spawn.posY,
                        z = posZ,
                        spawnX = spawn.posX,
                        spawnY = spawn.posY,
                        spawnZ = spawn.posZ,
                        spawnRadius = spawn.spawnRadius,
                        hp = def.hp,
                        maxHp = def.hp,
                        attack = def.attack,
                        defense = def.defense,
                        level = def.level,
                        xpReward = def.xpReward,
                        aggroRange = def.aggroRange,
                        attackRange = def.attackRange,
                        attackSpeedMs = def.attackSpeedMs,
                        moveSpeed = def.moveSpeed,
                        respawnMs = spawn.respawnMs
                    )

                    entityManager.addMonster(monster)
                    channel.addMonster(monster)
                    totalSpawned++
                }
            }
        }

        logger.info("Spawned {} monsters across all zones", totalSpawned)
    }

    /**
     * Get the best channel for a player to join in a given zone.
     * Returns the first channel with capacity, or creates a new one if all are full.
     */
    fun getBestChannel(zoneId: Int): ZoneChannel? {
        val channels = zones[zoneId] ?: return null

        // Find first channel with capacity
        for (channel in channels) {
            if (channel.hasCapacity()) {
                return channel
            }
        }

        // All channels full - create a new one
        val newChannelId = channels.size
        val newChannel = ZoneChannel(zoneId, newChannelId)
        channels.add(newChannel)
        logger.info("Auto-created channel {} for zone {} (all channels full)",
            newChannelId, ZONE_NAMES[zoneId] ?: zoneId)

        // TODO: Spawn monsters in the new channel (copy from spawn data)

        return newChannel
    }

    /**
     * Get a specific channel.
     */
    fun getChannel(zoneId: Int, channelId: Int): ZoneChannel? {
        return zones[zoneId]?.getOrNull(channelId)
    }

    /**
     * Get all channels for a zone.
     */
    fun getChannels(zoneId: Int): List<ZoneChannel> {
        return zones[zoneId] ?: emptyList()
    }

    /**
     * Get all zone channels across all zones (for game loop iteration).
     */
    fun getAllChannels(): List<ZoneChannel> {
        return zones.values.flatten()
    }

    /**
     * Add a player to the appropriate zone and channel.
     * Returns the channel the player was added to, or null on failure.
     */
    fun addPlayerToZone(player: PlayerEntity, zoneId: Int): ZoneChannel? {
        val channel = getBestChannel(zoneId) ?: return null

        if (!channel.addPlayer(player)) {
            logger.error("Failed to add player {} to channel {}-{}",
                player.name, zoneId, channel.channelId)
            return null
        }

        return channel
    }

    /**
     * Remove a player from their current zone/channel.
     */
    fun removePlayerFromZone(player: PlayerEntity) {
        val channel = getChannel(player.zoneId, player.channelId)
        channel?.removePlayer(player.entityId)
    }

    /**
     * Get the zone name for a zone ID.
     */
    fun getZoneName(zoneId: Int): String = ZONE_NAMES[zoneId] ?: "Unknown"

    /**
     * Check if a zone exists.
     */
    fun zoneExists(zoneId: Int): Boolean = zones.containsKey(zoneId)
}
