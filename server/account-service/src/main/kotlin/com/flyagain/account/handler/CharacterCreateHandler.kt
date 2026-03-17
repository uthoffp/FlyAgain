package com.flyagain.account.handler

import com.flyagain.common.CharacterClassMapping
import com.flyagain.common.network.Packet
import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.CreateCharacterRequest
import com.flyagain.common.proto.CharacterCreateRequest
import com.flyagain.common.proto.EnterWorldResponse
import com.flyagain.common.proto.Opcode
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles CHARACTER_CREATE (0x0005) requests.
 *
 * Validates the character name and class, then delegates creation to the
 * database-service via gRPC. On success, responds with a confirmation so
 * the client can proceed to character selection.
 */
class CharacterCreateHandler(
    private val characterDataStub: CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub
) {

    private val logger = LoggerFactory.getLogger(CharacterCreateHandler::class.java)

    companion object {
        private const val MIN_NAME_LENGTH = 3
        private const val MAX_NAME_LENGTH = 16
        private val NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9]{2,15}$")
        private val VALID_CLASSES = CharacterClassMapping.VALID_NAMES
        private val NAME_BLACKLIST = setOf(
            "admin", "administrator", "moderator", "gamemaster", "support",
            "system", "server", "flyagain", "gm", "mod", "dev", "developer",
            "npc", "monster", "boss", "null", "undefined", "test",
        )
    }

    suspend fun handle(ctx: ChannelHandlerContext, packet: Packet, accountId: String) {
        val request = try {
            CharacterCreateRequest.parseFrom(packet.payload)
        } catch (e: Exception) {
            logger.warn("Failed to parse CharacterCreateRequest from account {}: {}", accountId, e.message)
            sendError(ctx, false, "Invalid request payload")
            return
        }

        val name = request.name.trim()
        val characterClass = request.characterClass.lowercase()

        // Validate name
        if (name.length < MIN_NAME_LENGTH || name.length > MAX_NAME_LENGTH) {
            sendError(ctx, false, "Character name must be between $MIN_NAME_LENGTH and $MAX_NAME_LENGTH characters")
            return
        }
        if (!NAME_REGEX.matches(name)) {
            sendError(ctx, false, "Character name contains invalid characters")
            return
        }
        if (name.lowercase() in NAME_BLACKLIST) {
            sendError(ctx, false, "This character name is not allowed")
            return
        }

        // Validate class
        if (characterClass !in VALID_CLASSES) {
            sendError(ctx, false, "Invalid character class: ${request.characterClass}")
            return
        }

        val classId = CharacterClassMapping.idForName(characterClass)

        // Create character via gRPC
        val grpcRequest = CreateCharacterRequest.newBuilder()
            .setAccountId(accountId)
            .setName(name)
            .setCharacterClass(classId)
            .build()

        val grpcResponse = try {
            characterDataStub.createCharacter(grpcRequest)
        } catch (e: Exception) {
            logger.error("gRPC error creating character for account {}: {}", accountId, e.message)
            sendError(ctx, false, "Internal server error")
            return
        }

        if (!grpcResponse.success) {
            logger.info("Character creation failed for account {}: {}", accountId, grpcResponse.errorMessage)
            sendError(ctx, false, grpcResponse.errorMessage)
            return
        }

        logger.info("Character '{}' (id={}) created for account {}", name, grpcResponse.characterId, accountId)

        val response = EnterWorldResponse.newBuilder()
            .setSuccess(true)
            .build()

        ctx.writeAndFlush(Packet(Opcode.ENTER_WORLD_VALUE, response.toByteArray()))
    }

    private fun sendError(ctx: ChannelHandlerContext, success: Boolean, message: String) {
        val response = EnterWorldResponse.newBuilder()
            .setSuccess(success)
            .setErrorMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ENTER_WORLD_VALUE, response.toByteArray()))
    }
}
