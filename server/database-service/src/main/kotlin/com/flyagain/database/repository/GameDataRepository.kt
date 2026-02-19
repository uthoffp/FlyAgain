package com.flyagain.database.repository

import com.flyagain.common.grpc.*

/**
 * Repository interface for read-only game definition data.
 *
 * Provides access to static game data tables (items, monsters, spawns, skills,
 * loot tables) that are loaded at server startup and cached by consumers.
 * These records are defined by game designers and do not change at runtime.
 *
 * @see GameDataRepositoryImpl for the PostgreSQL-backed implementation
 */
interface GameDataRepository {

    /**
     * Retrieves all item definitions, ordered by ID.
     *
     * Item definitions describe every item in the game: weapons, armor,
     * consumables, quest items, etc.
     *
     * @return complete list of [ItemDefinitionRecord]s
     */
    suspend fun getAllItemDefinitions(): List<ItemDefinitionRecord>

    /**
     * Retrieves all monster definitions, ordered by ID.
     *
     * Monster definitions describe each monster type's stats, aggro range,
     * attack parameters, and movement speed.
     *
     * @return complete list of [MonsterDefinitionRecord]s
     */
    suspend fun getAllMonsterDefinitions(): List<MonsterDefinitionRecord>

    /**
     * Retrieves all monster spawn points, ordered by ID.
     *
     * Spawn records define where monsters appear in the world, including
     * map placement, spawn radius, count, and respawn timing.
     *
     * @return complete list of [MonsterSpawnRecord]s
     */
    suspend fun getAllMonsterSpawns(): List<MonsterSpawnRecord>

    /**
     * Retrieves all skill definitions, ordered by ID.
     *
     * Skill definitions describe each ability's class requirement, level
     * requirement, damage scaling, mana cost, and cooldown.
     *
     * @return complete list of [SkillDefinitionRecord]s
     */
    suspend fun getAllSkillDefinitions(): List<SkillDefinitionRecord>

    /**
     * Retrieves the complete loot table, ordered by ID.
     *
     * Loot table entries map monsters to the items they can drop, including
     * drop chance and amount range.
     *
     * @return complete list of [LootTableRecord]s
     */
    suspend fun getAllLootTables(): List<LootTableRecord>

    /**
     * Retrieves all learned skills for a specific character.
     *
     * @param characterId the character whose skills to load
     * @return list of [CharacterSkillRecord]s with skill ID and current level
     */
    suspend fun getCharacterSkills(characterId: Long): List<CharacterSkillRecord>
}
