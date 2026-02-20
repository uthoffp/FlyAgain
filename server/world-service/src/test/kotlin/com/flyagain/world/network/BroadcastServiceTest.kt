package com.flyagain.world.network

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.Opcode
import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.zone.ZoneChannel
import io.mockk.mockk
import io.mockk.verify
import io.netty.channel.Channel
import kotlin.test.Test

class BroadcastServiceTest {

    private val entityManager = EntityManager()
    private val broadcastService = BroadcastService(entityManager)

    private fun makePlayer(
        entityId: Long,
        x: Float = 50f,
        z: Float = 50f,
        tcpChannel: Channel? = null
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = entityId + 100,
            accountId = entityId + 200,
            name = "Player$entityId",
            characterClass = 1,
            x = x, y = 0f, z = z,
            tcpChannel = tcpChannel
        )
    }

    private fun makeMonster(entityId: Long = 1_000_000L, x: Float = 50f, z: Float = 50f): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = "Monster$entityId",
            x = x, y = 0f, z = z,
            spawnX = x, spawnY = 0f, spawnZ = z,
            hp = 100, maxHp = 100,
            attack = 10, defense = 5, level = 1,
            xpReward = 50, aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 1500, moveSpeed = 3f
        )
    }

    @Test
    fun `queuePositionUpdate and flushPendingUpdates sends to nearby players`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)

        val mockTcp1 = mockk<Channel>(relaxed = true)
        val mockTcp2 = mockk<Channel>(relaxed = true)

        val player1 = makePlayer(entityId = 1L, x = 50f, z = 50f, tcpChannel = mockTcp1)
        val player2 = makePlayer(entityId = 2L, x = 55f, z = 50f, tcpChannel = mockTcp2)

        channel.addPlayer(player1)
        channel.addPlayer(player2)

        // Queue position update for player1 — should broadcast to player2 (nearby), not to self
        broadcastService.queuePositionUpdate(player1, channel)
        broadcastService.flushPendingUpdates()
        broadcastService.flushNetworkWrites()

        verify(exactly = 1) { mockTcp2.write(any<Packet>()) }
        verify(exactly = 1) { mockTcp2.flush() }
        // player1 (self) should not receive their own update
        verify(exactly = 0) { mockTcp1.write(any<Packet>()) }
    }

    @Test
    fun `broadcastEntitySpawn sends to nearby players`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val mockTcp = mockk<Channel>(relaxed = true)

        val player = makePlayer(entityId = 1L, x = 50f, z = 50f, tcpChannel = mockTcp)
        channel.addPlayer(player)

        val monster = makeMonster(entityId = 1_000_000L, x = 55f, z = 50f)
        channel.addMonster(monster)

        broadcastService.broadcastEntitySpawn(channel, monster)
        broadcastService.flushNetworkWrites()

        verify(exactly = 1) { mockTcp.write(any<Packet>()) }
        verify(exactly = 1) { mockTcp.flush() }
    }

    @Test
    fun `broadcastEntityDespawn sends to nearby players`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val mockTcp = mockk<Channel>(relaxed = true)

        val player = makePlayer(entityId = 1L, x = 50f, z = 50f, tcpChannel = mockTcp)
        channel.addPlayer(player)

        // Despawn entity 999 near player's position
        broadcastService.broadcastEntityDespawn(channel, 999L, 55f, 50f)
        broadcastService.flushNetworkWrites()

        verify(exactly = 1) { mockTcp.write(any<Packet>()) }
    }

    @Test
    fun `broadcastDamageEvent sends to nearby players when target is monster`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val mockTcp = mockk<Channel>(relaxed = true)

        val player = makePlayer(entityId = 1L, x = 50f, z = 50f, tcpChannel = mockTcp)
        channel.addPlayer(player)

        val monster = makeMonster(entityId = 1_000_000L, x = 55f, z = 50f)
        channel.addMonster(monster)
        entityManager.addMonster(monster)

        val damageResult = CombatEngine.DamageResult(
            damage = 25,
            isCritical = false,
            targetKilled = false,
            targetEntityId = monster.entityId,
            attackerEntityId = player.entityId
        )

        broadcastService.broadcastDamageEvent(channel, damageResult)
        broadcastService.flushNetworkWrites()

        // Should get damage event packet
        verify(atLeast = 1) { mockTcp.write(any<Packet>()) }
    }

    @Test
    fun `broadcastDamageEvent broadcasts death when target killed`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val mockTcp = mockk<Channel>(relaxed = true)

        val player = makePlayer(entityId = 1L, x = 50f, z = 50f, tcpChannel = mockTcp)
        channel.addPlayer(player)

        val monster = makeMonster(entityId = 1_000_000L, x = 55f, z = 50f)
        channel.addMonster(monster)
        entityManager.addMonster(monster)

        val damageResult = CombatEngine.DamageResult(
            damage = 100,
            isCritical = true,
            targetKilled = true,
            targetEntityId = monster.entityId,
            attackerEntityId = player.entityId
        )

        broadcastService.broadcastDamageEvent(channel, damageResult)
        broadcastService.flushNetworkWrites()

        // Should get damage event + death event (2 writes)
        verify(exactly = 2) { mockTcp.write(any<Packet>()) }
    }

    @Test
    fun `sendToPlayer sends directly to player tcp channel`() {
        val mockTcp = mockk<Channel>(relaxed = true)
        val player = makePlayer(entityId = 1L, tcpChannel = mockTcp)

        val packet = Packet(Opcode.ERROR_RESPONSE_VALUE, byteArrayOf())
        broadcastService.sendToPlayer(player, packet)

        verify(exactly = 1) { mockTcp.writeAndFlush(packet) }
    }

    @Test
    fun `sendToPlayer does nothing when tcp channel is null`() {
        val player = makePlayer(entityId = 1L, tcpChannel = null)
        val packet = Packet(Opcode.ERROR_RESPONSE_VALUE, byteArrayOf())
        // Should not throw
        broadcastService.sendToPlayer(player, packet)
    }

    @Test
    fun `flushNetworkWrites clears pending channels`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val mockTcp = mockk<Channel>(relaxed = true)

        val player1 = makePlayer(entityId = 1L, x = 50f, z = 50f, tcpChannel = mockTcp)
        val player2 = makePlayer(entityId = 2L, x = 55f, z = 50f)
        channel.addPlayer(player1)
        channel.addPlayer(player2)

        broadcastService.queuePositionUpdate(player2, channel)
        broadcastService.flushPendingUpdates()
        broadcastService.flushNetworkWrites()

        // Second flush should not flush again
        broadcastService.flushNetworkWrites()

        verify(exactly = 1) { mockTcp.flush() }
    }
}
