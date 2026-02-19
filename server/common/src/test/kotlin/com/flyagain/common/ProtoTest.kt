package com.flyagain.common

import com.flyagain.common.proto.LoginRequest
import com.flyagain.common.proto.RegisterRequest
import com.flyagain.common.proto.Heartbeat
import com.flyagain.common.proto.Opcode
import com.flyagain.common.proto.Position
import com.flyagain.common.proto.CharacterStats
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtoTest {

    @Test
    fun `LoginRequest serializes and deserializes correctly`() {
        val request = LoginRequest.newBuilder()
            .setUsername("testuser")
            .setPassword("testpass")
            .build()

        val bytes = request.toByteArray()
        val deserialized = LoginRequest.parseFrom(bytes)

        assertEquals("testuser", deserialized.username)
        assertEquals("testpass", deserialized.password)
    }

    @Test
    fun `RegisterRequest serializes and deserializes correctly`() {
        val request = RegisterRequest.newBuilder()
            .setUsername("newuser")
            .setEmail("test@example.com")
            .setPassword("securepass123")
            .build()

        val bytes = request.toByteArray()
        val deserialized = RegisterRequest.parseFrom(bytes)

        assertEquals("newuser", deserialized.username)
        assertEquals("test@example.com", deserialized.email)
        assertEquals("securepass123", deserialized.password)
    }

    @Test
    fun `Heartbeat serializes and deserializes correctly`() {
        val heartbeat = Heartbeat.newBuilder()
            .setClientTime(1000L)
            .setServerTime(1001L)
            .build()

        val bytes = heartbeat.toByteArray()
        val deserialized = Heartbeat.parseFrom(bytes)

        assertEquals(1000L, deserialized.clientTime)
        assertEquals(1001L, deserialized.serverTime)
    }

    @Test
    fun `Position serializes and deserializes correctly`() {
        val position = Position.newBuilder()
            .setX(100.5f)
            .setY(50.0f)
            .setZ(200.75f)
            .build()

        val bytes = position.toByteArray()
        val deserialized = Position.parseFrom(bytes)

        assertEquals(100.5f, deserialized.x)
        assertEquals(50.0f, deserialized.y)
        assertEquals(200.75f, deserialized.z)
    }

    @Test
    fun `CharacterStats serializes and deserializes correctly`() {
        val stats = CharacterStats.newBuilder()
            .setLevel(15)
            .setHp(500)
            .setMaxHp(500)
            .setMp(200)
            .setMaxMp(200)
            .setStr(30)
            .setSta(25)
            .setDex(20)
            .setInt(15)
            .setXp(50000L)
            .build()

        val bytes = stats.toByteArray()
        val deserialized = CharacterStats.parseFrom(bytes)

        assertEquals(15, deserialized.level)
        assertEquals(500, deserialized.hp)
        assertEquals(500, deserialized.maxHp)
        assertEquals(200, deserialized.mp)
        assertEquals(200, deserialized.maxMp)
        assertEquals(30, deserialized.str)
        assertEquals(25, deserialized.sta)
        assertEquals(20, deserialized.dex)
        assertEquals(15, deserialized.int)
        assertEquals(50000L, deserialized.xp)
    }

    @Test
    fun `Opcode enum values match expected hex values`() {
        // Auth opcodes
        assertEquals(0x0001, Opcode.LOGIN_REQUEST.number)
        assertEquals(0x0002, Opcode.LOGIN_RESPONSE.number)
        assertEquals(0x0003, Opcode.CHARACTER_SELECT.number)
        assertEquals(0x0004, Opcode.ENTER_WORLD.number)
        assertEquals(0x0005, Opcode.CHARACTER_CREATE.number)
        assertEquals(0x0006, Opcode.REGISTER_REQUEST.number)
        assertEquals(0x0007, Opcode.REGISTER_RESPONSE.number)

        // Movement opcodes
        assertEquals(0x0101, Opcode.MOVEMENT_INPUT.number)
        assertEquals(0x0102, Opcode.ENTITY_POSITION.number)
        assertEquals(0x0103, Opcode.POSITION_CORRECTION.number)

        // Combat opcodes
        assertEquals(0x0201, Opcode.SELECT_TARGET.number)
        assertEquals(0x0202, Opcode.USE_SKILL.number)
        assertEquals(0x0203, Opcode.DAMAGE_EVENT.number)
        assertEquals(0x0204, Opcode.ENTITY_DEATH.number)
        assertEquals(0x0205, Opcode.XP_GAIN.number)
        assertEquals(0x0206, Opcode.TOGGLE_AUTO_ATTACK.number)

        // Entity opcodes
        assertEquals(0x0301, Opcode.ENTITY_SPAWN.number)
        assertEquals(0x0302, Opcode.ENTITY_DESPAWN.number)
        assertEquals(0x0303, Opcode.ENTITY_STATS_UPDATE.number)

        // Inventory opcodes
        assertEquals(0x0401, Opcode.MOVE_ITEM.number)
        assertEquals(0x0402, Opcode.INVENTORY_UPDATE.number)
        assertEquals(0x0403, Opcode.EQUIP_ITEM.number)
        assertEquals(0x0404, Opcode.UNEQUIP_ITEM.number)
        assertEquals(0x0405, Opcode.NPC_BUY.number)
        assertEquals(0x0406, Opcode.NPC_SELL.number)
        assertEquals(0x0407, Opcode.GOLD_UPDATE.number)

        // Chat opcodes
        assertEquals(0x0501, Opcode.CHAT_MESSAGE.number)
        assertEquals(0x0502, Opcode.CHAT_BROADCAST.number)

        // System opcodes
        assertEquals(0x0601, Opcode.HEARTBEAT.number)
        assertEquals(0x0602, Opcode.SERVER_MESSAGE.number)
        assertEquals(0x0603, Opcode.ERROR_RESPONSE.number)

        // Zone opcodes
        assertEquals(0x0701, Opcode.ZONE_DATA.number)
        assertEquals(0x0702, Opcode.CHANNEL_SWITCH.number)
        assertEquals(0x0703, Opcode.CHANNEL_LIST.number)
    }
}
