package com.flyagain.database.repository

import com.flyagain.common.grpc.CharacterRecord
import com.flyagain.common.grpc.SaveCharacterRequest

/**
 * Repository interface for character persistence operations.
 *
 * Manages the full lifecycle of player characters including creation with
 * class-specific base stats, retrieval, periodic save (write-back), and
 * soft deletion. Characters are never hard-deleted to allow potential recovery.
 *
 * @see CharacterRepositoryImpl for the PostgreSQL-backed implementation
 */
interface CharacterRepository {

    /**
     * Retrieves all non-deleted characters belonging to an account,
     * ordered by creation date (oldest first).
     *
     * @param accountId the owning account's ID
     * @return list of [CharacterRecord] for the account (may be empty)
     */
    suspend fun getByAccount(accountId: Long): List<CharacterRecord>

    /**
     * Retrieves a single character by ID, scoped to the owning account.
     *
     * The account check prevents unauthorized access to another player's character.
     *
     * @param characterId the character's database ID
     * @param accountId the expected owning account ID (authorization check)
     * @return the [CharacterRecord] if found and owned by the account, or `null`
     */
    suspend fun getById(characterId: Long, accountId: Long): CharacterRecord?

    /**
     * Creates a new character with class-specific base stats.
     *
     * Enforces a maximum of 3 characters per account. Initial stats vary
     * by class:
     * - **0 (Krieger):** High HP/STR/STA, low INT
     * - **1 (Magier):** High MP/INT, low HP/STR
     * - **2 (Assassine):** High DEX, balanced HP/MP
     * - **3 (Kleriker):** Balanced stats, moderate INT/STA
     *
     * The character starts at level 1, map 1, position (0,0,0) with 0 gold.
     *
     * @param accountId the owning account's ID
     * @param name the character name (must be unique — enforced by DB constraint)
     * @param characterClass class index (0-3)
     * @return the auto-generated character ID
     * @throws IllegalStateException if the account already has 3 characters
     * @throws IllegalArgumentException if [characterClass] is not in 0-3
     */
    suspend fun create(accountId: Long, name: String, characterClass: Int): Long

    /**
     * Persists a character's mutable state (HP, MP, XP, position, stats, etc.).
     *
     * Called by the [WriteBackScheduler][com.flyagain.database.writeback.WriteBackScheduler]
     * to flush dirty character data from Redis to PostgreSQL, and also on
     * logout / zone transitions for immediate persistence.
     *
     * @param request protobuf message containing the character ID and all fields to save
     */
    suspend fun save(request: SaveCharacterRequest)

    /**
     * Soft-deletes a character by setting `is_deleted = TRUE`.
     *
     * The character row remains in the database but is excluded from all
     * normal queries. Only the owning account can delete its own characters.
     *
     * @param characterId the character to mark as deleted
     * @param accountId the owning account ID (authorization check)
     */
    suspend fun softDelete(characterId: Long, accountId: Long)
}
