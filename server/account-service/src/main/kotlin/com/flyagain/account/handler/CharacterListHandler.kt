package com.flyagain.account.handler

import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.GetCharactersByAccountRequest
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.CharacterInfo
import com.flyagain.common.proto.CharacterListResponse
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles CHARACTER_LIST_REQUEST (0x0008) requests.
 *
 * Fetches the current character list for the authenticated account via gRPC
 * and responds with a CHARACTER_LIST_RESPONSE containing the character data.
 */
class CharacterListHandler(
    private val characterDataStub: CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub
) {

    private val logger = LoggerFactory.getLogger(CharacterListHandler::class.java)

    companion object {
        private val CLASS_NAMES = mapOf(
            0 to "Krieger",
            1 to "Magier",
            2 to "Assassine",
            3 to "Kleriker"
        )
    }

    suspend fun handle(ctx: ChannelHandlerContext, accountId: String) {
        val grpcRequest = GetCharactersByAccountRequest.newBuilder()
            .setAccountId(accountId)
            .build()

        val charList = try {
            characterDataStub.getCharactersByAccount(grpcRequest)
        } catch (e: Exception) {
            logger.error("gRPC error fetching characters for account {}: {}", accountId, e.message)
            sendError(ctx, "Internal server error")
            return
        }

        val characters = charList.charactersList.map { charRecord ->
            CharacterInfo.newBuilder()
                .setId(charRecord.id)
                .setName(charRecord.name)
                .setCharacterClass(CLASS_NAMES.getOrDefault(charRecord.characterClass, "Unknown"))
                .setLevel(charRecord.level)
                .build()
        }

        val response = CharacterListResponse.newBuilder()
            .addAllCharacters(characters)
            .build()

        ctx.writeAndFlush(Packet(Opcode.CHARACTER_LIST_RESPONSE_VALUE, response.toByteArray()))
        logger.debug("Sent character list ({} characters) for account {}", characters.size, accountId)
    }

    private fun sendError(ctx: ChannelHandlerContext, message: String) {
        val error = ErrorResponse.newBuilder()
            .setOriginalOpcode(Opcode.CHARACTER_LIST_REQUEST_VALUE)
            .setErrorCode(500)
            .setMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ERROR_RESPONSE_VALUE, error.toByteArray()))
    }
}
