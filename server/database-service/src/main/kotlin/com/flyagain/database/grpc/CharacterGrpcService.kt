package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.CharacterRepository
import com.flyagain.database.repository.GameDataRepository
import com.google.protobuf.Empty
import org.slf4j.LoggerFactory

class CharacterGrpcService(
    private val characterRepo: CharacterRepository,
    private val gameDataRepo: GameDataRepository
) : CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(CharacterGrpcService::class.java)

    override suspend fun getCharactersByAccount(request: GetCharactersByAccountRequest): CharacterList {
        logger.debug("getCharactersByAccount: accountId={}", request.accountId)
        val characters = characterRepo.getByAccount(request.accountId)
        return CharacterList.newBuilder().addAllCharacters(characters).build()
    }

    override suspend fun getCharacter(request: GetCharacterRequest): CharacterRecord {
        logger.debug("getCharacter: charId={}, accountId={}", request.characterId, request.accountId)
        return characterRepo.getById(request.characterId, request.accountId)
            ?: CharacterRecord.newBuilder().setFound(false).build()
    }

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

    override suspend fun saveCharacter(request: SaveCharacterRequest): Empty {
        logger.debug("saveCharacter: charId={}", request.characterId)
        characterRepo.save(request)
        return Empty.getDefaultInstance()
    }

    override suspend fun deleteCharacter(request: DeleteCharacterRequest): Empty {
        logger.info("deleteCharacter: charId={}, accountId={}", request.characterId, request.accountId)
        characterRepo.softDelete(request.characterId, request.accountId)
        return Empty.getDefaultInstance()
    }

    override suspend fun getCharacterSkills(request: GetCharacterSkillsRequest): CharacterSkillList {
        logger.debug("getCharacterSkills: charId={}", request.characterId)
        val skills = gameDataRepo.getCharacterSkills(request.characterId)
        return CharacterSkillList.newBuilder().addAllSkills(skills).build()
    }
}
