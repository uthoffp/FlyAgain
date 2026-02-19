package com.flyagain.world

import com.flyagain.world.entity.PlayerEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerEntityTest {

    private fun makePlayer(
        str: Int = 10,
        sta: Int = 10,
        dex: Int = 10,
        level: Int = 1
    ): PlayerEntity {
        return PlayerEntity(
            entityId = 1L,
            characterId = 100L,
            accountId = 200L,
            name = "TestPlayer",
            characterClass = 1,
            x = 0f, y = 0f, z = 0f,
            str = str, sta = sta, dex = dex, level = level
        )
    }

    @Test
    fun `getAttackPower is str times 2 plus level`() {
        val player = makePlayer(str = 15, level = 5)
        // atk = str * 2 + level = 15 * 2 + 5 = 35
        assertEquals(35, player.getAttackPower())
    }

    @Test
    fun `getDefense is sta plus level`() {
        val player = makePlayer(sta = 20, level = 3)
        // def = sta + level = 20 + 3 = 23
        assertEquals(23, player.getDefense())
    }

    @Test
    fun `getMoveSpeed is base 5 plus dex scaling`() {
        val player = makePlayer(dex = 20)
        // speed = 5.0 + 20 * 0.05 = 5.0 + 1.0 = 6.0
        assertEquals(6.0f, player.getMoveSpeed())
    }

    @Test
    fun `markDirty sets dirty flag`() {
        val player = makePlayer()
        assertFalse(player.dirty)
        player.markDirty()
        assertTrue(player.dirty)
    }

    @Test
    fun `default values are correct`() {
        val player = makePlayer()
        assertEquals(1, player.level)
        assertEquals(100, player.hp)
        assertEquals(100, player.maxHp)
        assertEquals(50, player.mp)
        assertEquals(50, player.maxMp)
        assertEquals(0L, player.xp)
        assertEquals(0L, player.gold)
        assertFalse(player.autoAttacking)
        assertFalse(player.isMoving)
        assertFalse(player.dirty)
    }
}
