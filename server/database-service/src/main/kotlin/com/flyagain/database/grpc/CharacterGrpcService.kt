package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.CharacterRepository
import com.flyagain.database.repository.GameDataRepository
import com.google.protobuf.Empty
import org.slf4j.LoggerFactory

/**
 * gRPC service implementation for character-related database operations.
 *
 * Exposes character CRUD, save (write-back), soft-delete, and skill queries
 * over gRPC. Used by the account-service for character management and by the
 * world-service for loading/saving character state.
 *
 * @param characterRepo the character repository (interface — testable with mocks)
 * @param gameDataRepo the game data repository, used here for character skill queries
 */
class CharacterGrpcService(
    private val characterRepo: CharacterRepository,
    private val gameDataRepo: GameDataRepository
) : CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(CharacterGrpcService::class.java)

    /** Returns all non-deleted characters for an account. */
    override suspend fun getCharactersByAccount(request: GetCharactersByAccountRequest): CharacterList {
        logger.debug("getCharactersByAccount: accountId={}", request.accountId)
        val characters = characterRepo.getByAccount(request.accountId)
        return CharacterList.newBuilder().addAllCharacters(characters).build()
    }

    /** Returns a single character. Returns a record with `found=false` if missing or not owned. */
    override suspend fun getCharacter(request: GetCharacterRequest): CharacterRecord {
        logger.debug("getCharacter: charId={}, accountId={}", request.characterId, request.accountId)
        return characterRepo.getById(request.characterId, request.accountId)
            ?: CharacterRecord.newBuilder().setFound(false).build()
    }

    /** Creates a new character with class-specific base stats. Enforces 3-character limit. */
    override suspend fun createCharacter(request: CreateCharacterRequest): CreateCharacterResponse {
        logger.info("createCharacter: name={}, class={}", request.name, request.characterClass)
        return try {
            val charId = characterRepo.create(request.accountId, request.name, request.characterClass)
            CreateCharacterResponse.newBuilder()
                .setSuccess(true)
                .setCharacterId(charId)
                .build()
        } catch (e: Exception) {
            logger.warn("createCharacter failed: {}", e.message)
            CreateCharacterResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.message ?: "Unknown error")
                .build()
        }
    }

    /** Persists a character's mutable state (HP, MP, XP, position, stats) to PostgreSQL. */
    override suspend fun saveCharacter(request: SaveCharacterRequest): Empty {
        logger.debug("saveCharacter: charId={}", request.characterId)
        characterRepo.save(request)
        return Empty.getDefaultInstance()
    }

    /** Soft-deletes a character (sets `is_deleted = TRUE`). */
    override suspend fun deleteCharacter(request: DeleteCharacterRequest): Empty {
        logger.info("deleteCharacter: charId={}, accountId={}", request.characterId, request.accountId)
        characterRepo.softDelete(request.characterId, request.accountId)
        return Empty.getDefaultInstance()
    }

    /** Returns all learned skills for a character. */
    override suspend fun getCharacterSkills(request: GetCharacterSkillsRequest): CharacterSkillList {
        logger.debug("getCharacterSkills: charId={}", request.characterId)
        val skills = gameDataRepo.getCharacterSkills(request.characterId)
        return CharacterSkillList.newBuilder().addAllSkills(skills).build()
    }
}
