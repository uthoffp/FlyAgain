package com.flyagain.world.network

import com.flyagain.common.network.SessionSecretProvider
import com.flyagain.common.network.UdpPacket
import com.flyagain.common.network.UdpPacketHandler
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.gameloop.InputQueue
import com.flyagain.world.gameloop.QueuedPacket
import io.lettuce.core.api.StatefulRedisConnection
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles incoming UDP packets from the world service's UDP server.
 *
 * UDP is used for real-time game traffic (movement inputs). Validated packets
 * are enqueued into the [InputQueue] for processing in the game loop.
 *
 * Session token lookup uses EntityManager's O(1) ConcurrentHashMap
 * instead of a linear scan over all players.
 */
class WorldUdpHandler(
    private val inputQueue: InputQueue,
    private val entityManager: EntityManager
) : UdpPacketHandler {

    private val logger = LoggerFactory.getLogger(WorldUdpHandler::class.java)

    override fun handlePacket(packet: UdpPacket) {
        when (packet.opcode) {
            Opcode.MOVEMENT_INPUT_VALUE -> {
                // O(1) lookup by session token via dedicated map in EntityManager
                val player = entityManager.getPlayerBySessionToken(packet.sessionToken) ?: return

                inputQueue.enqueue(QueuedPacket(
                    accountId = player.accountId,
                    opcode = packet.opcode,
                    payload = packet.payload,
                    tcpChannel = player.tcpChannel,
                    timestamp = System.currentTimeMillis()
                ))
            }
            else -> {
                logger.debug("Unknown UDP opcode 0x{}", packet.opcode.toString(16))
            }
        }
    }
}

/**
 * Resolves session tokens to HMAC secrets using an in-memory cache
 * backed by Redis for initial population.
 *
 * The cache avoids a blocking Redis call on every UDP packet (up to 100k/sec
 * at 5,000 CCU). Secrets are registered when a player enters the world and
 * removed on disconnect. A Redis fallback handles edge cases (e.g., server
 * restart while sessions are still valid).
 */
class RedisSessionSecretProvider(
    private val redisConnection: StatefulRedisConnection<String, String>
) : SessionSecretProvider {

    private val logger = LoggerFactory.getLogger(RedisSessionSecretProvider::class.java)

    // In-memory cache: sessionToken -> HMAC secret bytes.
    // Populated via registerSecret() when a player enters the world.
    private val secretCache = ConcurrentHashMap<Long, ByteArray>()

    /**
     * Register a session secret in the local cache.
     * Called from EnterWorldHandler when a player joins.
     */
    fun registerSecret(sessionToken: Long, secret: ByteArray) {
        secretCache[sessionToken] = secret
    }

    /**
     * Remove a session secret from the local cache.
     * Called on player disconnect.
     */
    fun removeSecret(sessionToken: Long) {
        secretCache.remove(sessionToken)
    }

    override fun getSecret(sessionToken: Long): ByteArray? {
        // Fast path: in-memory lookup (no blocking, no I/O)
        secretCache[sessionToken]?.let { return it }

        // Slow path fallback: Redis lookup for sessions that existed before
        // this server started (e.g., after a restart). This is blocking but
        // only hit once per unknown session token, then cached.
        return try {
            val tokenHex = sessionToken.toString(16).padStart(16, '0')
            val sessionKey = "session:$tokenHex"
            val hmacSecret = redisConnection.sync().hget(sessionKey, "hmacSecret")
            hmacSecret?.toByteArray(Charsets.UTF_8)?.also { secretCache[sessionToken] = it }
        } catch (e: Exception) {
            logger.debug("Failed to resolve session secret for token {}: {}", sessionToken, e.message)
            null
        }
    }
}
