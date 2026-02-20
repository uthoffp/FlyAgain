package com.flyagain.world.entity

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages entity ID assignment and global entity tracking.
 * Provides thread-safe ID generation and lookup for all entity types
 * (players and monsters).
 */
class EntityManager {

    private val logger = LoggerFactory.getLogger(EntityManager::class.java)

    // Separate ID counters for players and monsters to avoid collisions
    // Players: IDs starting at 1
    // Monsters: IDs starting at 1,000,000
    private val playerIdCounter = AtomicLong(1)
    private val monsterIdCounter = AtomicLong(1_000_000)

    // Global entity maps
    private val players = ConcurrentHashMap<Long, PlayerEntity>()
    private val monsters = ConcurrentHashMap<Long, MonsterEntity>()

    // Lookup by account ID for quick session-based access
    private val playersByAccount = ConcurrentHashMap<Long, PlayerEntity>()

    // Lookup by character ID
    private val playersByCharacter = ConcurrentHashMap<Long, PlayerEntity>()

    // Lookup by session token (Long) for fast UDP packet routing
    private val playersBySessionToken = ConcurrentHashMap<Long, PlayerEntity>()

    /**
     * Generate a new unique entity ID for a player.
     */
    fun nextPlayerId(): Long = playerIdCounter.getAndIncrement()

    /**
     * Generate a new unique entity ID for a monster.
     */
    fun nextMonsterId(): Long = monsterIdCounter.getAndIncrement()

    /**
     * Atomically try to register a player entity. Returns false if the account
     * already has a player in the world (prevents duplicate login race condition).
     */
    fun tryAddPlayer(player: PlayerEntity): Boolean {
        val existing = playersByAccount.putIfAbsent(player.accountId, player)
        if (existing != null) {
            return false
        }
        players[player.entityId] = player
        playersByCharacter[player.characterId] = player
        if (player.sessionTokenLong != 0L) {
            playersBySessionToken[player.sessionTokenLong] = player
        }
        logger.debug("Player entity added: {} (entityId={}, accountId={})",
            player.name, player.entityId, player.accountId)
        return true
    }

    /**
     * Remove a player entity from the world.
     */
    fun removePlayer(entityId: Long): PlayerEntity? {
        val player = players.remove(entityId) ?: return null
        playersByAccount.remove(player.accountId)
        playersByCharacter.remove(player.characterId)
        if (player.sessionTokenLong != 0L) {
            playersBySessionToken.remove(player.sessionTokenLong)
        }
        logger.debug("Player entity removed: {} (entityId={})", player.name, entityId)
        return player
    }

    /**
     * Register a monster entity in the world.
     */
    fun addMonster(monster: MonsterEntity) {
        monsters[monster.entityId] = monster
        logger.trace("Monster entity added: {} (entityId={})", monster.name, monster.entityId)
    }

    /**
     * Remove a monster entity from the world.
     */
    fun removeMonster(entityId: Long): MonsterEntity? {
        return monsters.remove(entityId)
    }

    /**
     * Get a player by entity ID.
     */
    fun getPlayer(entityId: Long): PlayerEntity? = players[entityId]

    /**
     * Get a player by account ID.
     */
    fun getPlayerByAccount(accountId: Long): PlayerEntity? = playersByAccount[accountId]

    /**
     * Get a player by character ID.
     */
    fun getPlayerByCharacter(characterId: Long): PlayerEntity? = playersByCharacter[characterId]

    /**
     * Get a player by UDP session token.
     */
    fun getPlayerBySessionToken(sessionToken: Long): PlayerEntity? = playersBySessionToken[sessionToken]

    /**
     * Get a monster by entity ID.
     */
    fun getMonster(entityId: Long): MonsterEntity? = monsters[entityId]

    /**
     * Get all currently registered players.
     */
    fun getAllPlayers(): Collection<PlayerEntity> = players.values

    /**
     * Get all currently registered monsters.
     */
    fun getAllMonsters(): Collection<MonsterEntity> = monsters.values

    /**
     * Get total player count.
     */
    fun getPlayerCount(): Int = players.size

    /**
     * Get total monster count.
     */
    fun getMonsterCount(): Int = monsters.size

    /**
     * Check if an entity ID belongs to a player.
     */
    fun isPlayer(entityId: Long): Boolean = players.containsKey(entityId)

    /**
     * Check if an entity ID belongs to a monster.
     */
    fun isMonster(entityId: Long): Boolean = monsters.containsKey(entityId)
}
