package com.flyagain.account.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.GetCharacterRequest
import com.flyagain.common.proto.CharacterSelectRequest
import com.flyagain.common.proto.CharacterStats
import com.flyagain.common.proto.EnterWorldResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.Position
import io.lettuce.core.api.StatefulRedisConnection
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory

/**
 * Handles CHARACTER_SELECT (0x0003) requests.
 *
 * Loads the selected character from the database-service via gRPC, caches the
 * character data in Redis for the world-service to pick up, and responds with
 * the world-service endpoint so the client can connect there next.
 */
class CharacterSelectHandler(
    private val characterDataStub: CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub,
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val worldServiceHost: String,
    private val worldServiceTcpPort: Int,
    private val worldServiceUdpPort: Int
) {

    private val logger = LoggerFactory.getLogger(CharacterSelectHandler::class.java)

    companion object {
        private const val CHARACTER_CACHE_TTL_SECONDS = 300L // 5 minutes
    }

    suspend fun handle(ctx: ChannelHandlerContext, packet: Packet, accountId: Long) {
        val request = try {
            CharacterSelectRequest.parseFrom(packet.payload)
        } catch (e: Exception) {
            logger.warn("Failed to parse CharacterSelectRequest from account {}: {}", accountId, e.message)
            sendError(ctx, "Invalid request payload")
            return
        }

        val characterId = request.characterId
        if (characterId <= 0) {
            sendError(ctx, "Invalid character ID")
            return
        }

        // Load character from database-service via gRPC
        val grpcRequest = GetCharacterRequest.newBuilder()
            .setCharacterId(characterId)
            .setAccountId(accountId)
            .build()

        val character = try {
            characterDataStub.getCharacter(grpcRequest)
        } catch (e: Exception) {
            logger.error("gRPC error loading character {} for account {}: {}", characterId, accountId, e.message)
            sendError(ctx, "Internal server error")
            return
        }

        if (!character.found) {
            logger.warn("Character {} not found for account {}", characterId, accountId)
            sendError(ctx, "Character not found")
            return
        }

        if (character.accountId != accountId) {
            logger.warn("Character {} does not belong to account {} (belongs to {})", characterId, accountId, character.accountId)
            sendError(ctx, "Character not found")
            return
        }

        // Cache character data in Redis for world-service
        try {
            val redis = redisConnection.sync()
            val key = "char:$characterId"
            redis.hset(key, mapOf(
                "account_id" to accountId.toString(),
                "name" to character.name,
                "class" to character.characterClass.toString(),
                "level" to character.level.toString(),
                "xp" to character.xp.toString(),
                "hp" to character.hp.toString(),
                "mp" to character.mp.toString(),
                "max_hp" to character.maxHp.toString(),
                "max_mp" to character.maxMp.toString(),
                "str" to character.str.toString(),
                "sta" to character.sta.toString(),
                "dex" to character.dex.toString(),
                "int" to character.intStat.toString(),
                "stat_points" to character.statPoints.toString(),
                "map_id" to character.mapId.toString(),
                "pos_x" to character.posX.toString(),
                "pos_y" to character.posY.toString(),
                "pos_z" to character.posZ.toString(),
                "gold" to character.gold.toString()
            ))
            redis.expire(key, CHARACTER_CACHE_TTL_SECONDS)
        } catch (e: Exception) {
            logger.error("Failed to cache character {} in Redis: {}", characterId, e.message)
            sendError(ctx, "Internal server error")
            return
        }

        logger.info("Character {} selected by account {}, directing to world-service at {}:{}",
            characterId, accountId, worldServiceHost, worldServiceTcpPort)

        val response = EnterWorldResponse.newBuilder()
            .setSuccess(true)
            .setPosition(Position.newBuilder()
                .setX(character.posX)
                .setY(character.posY)
                .setZ(character.posZ)
                .build())
            .setStats(CharacterStats.newBuilder()
                .setLevel(character.level)
                .setHp(character.hp)
                .setMaxHp(character.maxHp)
                .setMp(character.mp)
                .setMaxMp(character.maxMp)
                .setStr(character.str)
                .setSta(character.sta)
                .setDex(character.dex)
                .setInt(character.intStat)
                .setXp(character.xp)
                .build())
            .setWorldServiceHost(worldServiceHost)
            .setWorldServiceTcpPort(worldServiceTcpPort)
            .setWorldServiceUdpPort(worldServiceUdpPort)
            .build()

        ctx.writeAndFlush(Packet(Opcode.ENTER_WORLD_VALUE, response.toByteArray()))
    }

    private fun sendError(ctx: ChannelHandlerContext, message: String) {
        val response = EnterWorldResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(message)
            .build()
        ctx.writeAndFlush(Packet(Opcode.ENTER_WORLD_VALUE, response.toByteArray()))
    }
}
