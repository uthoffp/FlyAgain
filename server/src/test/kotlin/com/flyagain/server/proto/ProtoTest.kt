package com.flyagain.server.proto

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
    fun `Opcode enum values match expected hex values`() {
        assertEquals(0x0001, Opcode.LOGIN_REQUEST.number)
        assertEquals(0x0002, Opcode.LOGIN_RESPONSE.number)
        assertEquals(0x0006, Opcode.REGISTER_REQUEST.number)
        assertEquals(0x0101, Opcode.MOVEMENT_INPUT.number)
        assertEquals(0x0201, Opcode.SELECT_TARGET.number)
        assertEquals(0x0301, Opcode.ENTITY_SPAWN.number)
        assertEquals(0x0401, Opcode.MOVE_ITEM.number)
        assertEquals(0x0501, Opcode.CHAT_MESSAGE.number)
        assertEquals(0x0601, Opcode.HEARTBEAT.number)
        assertEquals(0x0701, Opcode.ZONE_DATA.number)
    }
}
