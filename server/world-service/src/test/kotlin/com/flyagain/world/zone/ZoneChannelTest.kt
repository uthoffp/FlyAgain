package com.flyagain.world.zone

import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZoneChannelTest {

    private fun makePlayer(entityId: Long = 1L, accountId: Long = 100L): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = entityId + 100,
            accountId = accountId,
            name = "Player$entityId",
            characterClass = 1,
            x = 50f, y = 0f, z = 50f
        )
    }

    private fun makeMonster(entityId: Long = 1_000_000L): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = "Monster$entityId",
            x = 100f, y = 0f, z = 100f,
            spawnX = 100f, spawnY = 0f, spawnZ = 100f,
            hp = 100, maxHp = 100,
            attack = 10, defense = 5, level = 1,
            xpReward = 50, aggroRange = 15f, attackRange = 2f,
            attackSpeedMs = 1500, moveSpeed = 3f
        )
    }

    @Test
    fun `addPlayer succeeds and sets zone and channel id`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer()
        assertTrue(channel.addPlayer(player))
        assertEquals(1, player.zoneId)
        assertEquals(0, player.channelId)
        assertEquals(1, channel.getPlayerCount())
    }

    @Test
    fun `addPlayer rejects when channel is full`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0, maxPlayers = 2)
        assertTrue(channel.addPlayer(makePlayer(entityId = 1L, accountId = 1L)))
        assertTrue(channel.addPlayer(makePlayer(entityId = 2L, accountId = 2L)))
        assertFalse(channel.addPlayer(makePlayer(entityId = 3L, accountId = 3L)))
        assertEquals(2, channel.getPlayerCount())
    }

    @Test
    fun `removePlayer returns player and decrements count`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer()
        channel.addPlayer(player)
        val removed = channel.removePlayer(player.entityId)
        assertNotNull(removed)
        assertEquals(player.entityId, removed.entityId)
        assertEquals(0, channel.getPlayerCount())
    }

    @Test
    fun `removePlayer returns null for non-existent entity`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        assertNull(channel.removePlayer(999L))
    }

    @Test
    fun `addMonster and getMonster`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val monster = makeMonster()
        channel.addMonster(monster)
        val retrieved = channel.getMonster(monster.entityId)
        assertNotNull(retrieved)
        assertEquals(monster.entityId, retrieved.entityId)
        assertEquals(1, monster.zoneId)
        assertEquals(0, monster.channelId)
    }

    @Test
    fun `removeMonster returns monster and removes from channel`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val monster = makeMonster()
        channel.addMonster(monster)
        val removed = channel.removeMonster(monster.entityId)
        assertNotNull(removed)
        assertNull(channel.getMonster(monster.entityId))
    }

    @Test
    fun `removeMonster returns null for non-existent entity`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        assertNull(channel.removeMonster(999L))
    }

    @Test
    fun `getPlayer returns correct player`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer(entityId = 42L)
        channel.addPlayer(player)
        val retrieved = channel.getPlayer(42L)
        assertNotNull(retrieved)
        assertEquals("Player42", retrieved.name)
    }

    @Test
    fun `getPlayer returns null for non-existent entity`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        assertNull(channel.getPlayer(999L))
    }

    @Test
    fun `getAllPlayers returns all added players`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        channel.addPlayer(makePlayer(entityId = 1L, accountId = 1L))
        channel.addPlayer(makePlayer(entityId = 2L, accountId = 2L))
        assertEquals(2, channel.getAllPlayers().size)
    }

    @Test
    fun `getAllMonsters returns all added monsters`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        channel.addMonster(makeMonster(entityId = 1_000_000L))
        channel.addMonster(makeMonster(entityId = 1_000_001L))
        assertEquals(2, channel.getAllMonsters().size)
    }

    @Test
    fun `hasCapacity returns true when room available`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0, maxPlayers = 10)
        assertTrue(channel.hasCapacity())
    }

    @Test
    fun `hasCapacity returns false when full`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0, maxPlayers = 1)
        channel.addPlayer(makePlayer())
        assertFalse(channel.hasCapacity())
    }

    @Test
    fun `getNearbyEntities uses spatial grid`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer(entityId = 1L)
        channel.addPlayer(player)
        // Player is at (50, 0, 50) — should be found near that position
        val nearby = channel.getNearbyEntities(50f, 50f)
        assertTrue(nearby.contains(1L))
    }

    @Test
    fun `updatePlayerPosition updates spatial grid`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer(entityId = 1L)
        channel.addPlayer(player)

        // Move player far away
        channel.updatePlayerPosition(1L, 5000f, 5000f)

        // Should not be found near original position
        val nearbyOld = channel.getNearbyEntities(50f, 50f)
        assertFalse(nearbyOld.contains(1L))

        // Should be found near new position
        val nearbyNew = channel.getNearbyEntities(5000f, 5000f)
        assertTrue(nearbyNew.contains(1L))
    }

    @Test
    fun `updateMonsterPosition updates spatial grid`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val monster = makeMonster(entityId = 1_000_000L)
        channel.addMonster(monster)

        channel.updateMonsterPosition(1_000_000L, 5000f, 5000f)

        val nearbyOld = channel.getNearbyEntities(100f, 100f)
        assertFalse(nearbyOld.contains(1_000_000L))

        val nearbyNew = channel.getNearbyEntities(5000f, 5000f)
        assertTrue(nearbyNew.contains(1_000_000L))
    }

    @Test
    fun `player removal also cleans up spatial grid`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val player = makePlayer(entityId = 1L)
        channel.addPlayer(player)
        channel.removePlayer(1L)

        val nearby = channel.getNearbyEntities(50f, 50f)
        assertFalse(nearby.contains(1L))
    }

    @Test
    fun `monster removal also cleans up spatial grid`() {
        val channel = ZoneChannel(zoneId = 1, channelId = 0)
        val monster = makeMonster(entityId = 1_000_000L)
        channel.addMonster(monster)
        channel.removeMonster(1_000_000L)

        val nearby = channel.getNearbyEntities(100f, 100f)
        assertFalse(nearby.contains(1_000_000L))
    }
}
