package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.MovementInput
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.Position
import com.flyagain.common.proto.PositionCorrection
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.gameloop.QueuedPacket
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Validates and applies player movement.
 *
 * Movement is received via UDP (enqueued to InputQueue) and processed
 * in the game loop tick. The handler:
 * 1. Validates the movement against speed limits
 * 2. Applies the movement to the player entity
 * 3. Updates the spatial grid
 * 4. Queues position broadcasts for nearby players
 *
 * If validation fails, a PositionCorrection is sent to the client.
 */
class MovementHandler(
    private val entityManager: EntityManager,
    private val zoneManager: ZoneManager,
    private val broadcastService: BroadcastService
) {

    private val logger = LoggerFactory.getLogger(MovementHandler::class.java)

    companion object {
        const val GROUND_MOVE_SPEED = 5.0f      // base units/sec on ground
        const val FLY_MOVE_SPEED = 8.0f          // base units/sec while flying
        const val SPEED_TOLERANCE = 1.5f         // multiplier tolerance for lag
        const val MAX_Y_POSITION = 500f          // max flight height
        const val MIN_Y_POSITION = -10f          // below terrain threshold
        const val WORLD_BOUNDARY_MIN = -100f
        const val WORLD_BOUNDARY_MAX = 10100f
    }

    /**
     * Process a movement input packet from the InputQueue.
     * Called within the game loop thread.
     */
    fun handleMovementInput(packet: QueuedPacket) {
        val player = entityManager.getPlayerByAccount(packet.accountId) ?: return

        val input = try {
            MovementInput.parseFrom(packet.payload)
        } catch (e: Exception) {
            logger.debug("Failed to parse MovementInput from account {}", packet.accountId)
            return
        }

        // Reject NaN/Infinity in input
        if (!input.dx.isFinite() || !input.dy.isFinite() || !input.dz.isFinite() || !input.rotation.isFinite()) {
            logger.warn("NaN/Infinity in movement input from account {}", packet.accountId)
            return
        }

        // Normalize direction vector to prevent speed amplification
        var dx = input.dx
        var dy = input.dy
        var dz = input.dz
        val length = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        if (length > 1.0001f) {
            dx /= length
            dy /= length
            dz /= length
        }

        // Update player movement state
        player.inputDx = dx
        player.inputDy = dy
        player.inputDz = dz
        player.isMoving = input.isMoving
        player.rotation = input.rotation

        // Handle flight toggle
        if (input.isFlying != player.isFlying) {
            player.isFlying = input.isFlying
            player.markDirty()
        }
    }

    /**
     * Apply movement to a player entity for this tick.
     * Called from the game loop for each moving player.
     *
     * @return true if position changed and needs broadcasting
     */
    fun applyMovement(player: PlayerEntity, deltaMs: Long, channel: ZoneChannel): Boolean {
        if (!player.isMoving) return false

        val deltaSeconds = deltaMs / 1000f
        val baseSpeed = if (player.isFlying) FLY_MOVE_SPEED else GROUND_MOVE_SPEED
        val speed = baseSpeed + (player.dex * 0.05f) // dex bonus

        // Calculate new position
        val dx = player.inputDx * speed * deltaSeconds
        val dy = if (player.isFlying) player.inputDy * speed * deltaSeconds else 0f
        val dz = player.inputDz * speed * deltaSeconds

        val newX = player.x + dx
        val newY = player.y + dy
        val newZ = player.z + dz

        // Validate movement
        val validationResult = validatePosition(player, newX, newY, newZ, deltaSeconds, speed)
        if (!validationResult.valid) {
            sendPositionCorrection(player, validationResult.reason)
            return false
        }

        // Apply movement
        val oldX = player.x
        val oldZ = player.z
        player.x = newX
        player.y = newY
        player.z = newZ
        player.markDirty()

        // Update spatial grid
        channel.updatePlayerPosition(player.entityId, newX, newZ)

        // Queue broadcast to nearby players
        broadcastService.queuePositionUpdate(player, channel)

        return true
    }

    private data class ValidationResult(val valid: Boolean, val reason: String = "")

    private fun validatePosition(
        player: PlayerEntity,
        newX: Float,
        newY: Float,
        newZ: Float,
        deltaSeconds: Float,
        maxSpeed: Float
    ): ValidationResult {

        // Reject NaN/Infinity positions
        if (!newX.isFinite() || !newY.isFinite() || !newZ.isFinite()) {
            return ValidationResult(false, "Invalid position values")
        }

        // World boundary check
        if (newX < WORLD_BOUNDARY_MIN || newX > WORLD_BOUNDARY_MAX ||
            newZ < WORLD_BOUNDARY_MIN || newZ > WORLD_BOUNDARY_MAX) {
            return ValidationResult(false, "Out of world bounds")
        }

        // Height check
        if (newY < MIN_Y_POSITION || newY > MAX_Y_POSITION) {
            return ValidationResult(false, "Invalid height")
        }

        // Non-flying players must stay at ground level (Y=0 for flat terrain)
        if (!player.isFlying && newY > 1.0f) {
            return ValidationResult(false, "Cannot fly without flight mode")
        }

        // Speed check: distance traveled should not exceed max speed * time * tolerance
        val dx = newX - player.x
        val dy = newY - player.y
        val dz = newZ - player.z
        val distance = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        val maxDistance = maxSpeed * deltaSeconds * SPEED_TOLERANCE

        if (distance > maxDistance && distance > 0.1f) {
            logger.warn("Speed hack detected for player {} (distance={}, maxAllowed={}, delta={}s)",
                player.name, distance, maxDistance, deltaSeconds)
            return ValidationResult(false, "Speed violation")
        }

        return ValidationResult(true)
    }

    private fun sendPositionCorrection(player: PlayerEntity, reason: String) {
        val correction = PositionCorrection.newBuilder()
            .setPosition(Position.newBuilder()
                .setX(player.x)
                .setY(player.y)
                .setZ(player.z)
                .build())
            .setRotation(player.rotation)
            .setReason(reason)
            .build()

        player.tcpChannel?.writeAndFlush(
            Packet(Opcode.POSITION_CORRECTION_VALUE, correction.toByteArray())
        )

        logger.debug("Sent position correction to player {} (reason: {})", player.name, reason)
    }
}
