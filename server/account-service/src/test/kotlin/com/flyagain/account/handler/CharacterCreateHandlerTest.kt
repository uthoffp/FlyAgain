package com.flyagain.account.handler

import com.flyagain.common.CharacterClassMapping
import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.CreateCharacterResponse
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.CharacterCreateRequest
import com.flyagain.common.proto.EnterWorldResponse
import com.flyagain.common.proto.Opcode
import io.mockk.*
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CharacterCreateHandlerTest {

    private val characterStub = mockk<CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub>()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private val handler = CharacterCreateHandler(characterStub)

    init {
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
    }

    private fun makePacket(name: String, characterClass: String): Packet {
        val request = CharacterCreateRequest.newBuilder()
            .setName(name)
            .setCharacterClass(characterClass)
            .build()
        return Packet(Opcode.CHARACTER_CREATE_VALUE, request.toByteArray())
    }

    private fun captureResponse(): EnterWorldResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return EnterWorldResponse.parseFrom(slot.captured.payload)
    }

    private fun captureGrpcClassId(): Int {
        val slot = slot<com.flyagain.common.grpc.CreateCharacterRequest>()
        coVerify { characterStub.createCharacter(capture(slot), any()) }
        return slot.captured.characterClass
    }

    // ---- Class string-to-ID conversion ----

    @Test
    fun `Warrior maps to class ID 0`() = runTest {
        coEvery { characterStub.createCharacter(any(), any()) } returns
            CreateCharacterResponse.newBuilder().setSuccess(true).setCharacterId("c-1").build()

        handler.handle(ctx, makePacket("TestWarrior", "Warrior"), "acc-1")

        assertEquals(0, captureGrpcClassId())
    }

    @Test
    fun `Mage maps to class ID 1`() = runTest {
        coEvery { characterStub.createCharacter(any(), any()) } returns
            CreateCharacterResponse.newBuilder().setSuccess(true).setCharacterId("c-2").build()

        handler.handle(ctx, makePacket("TestMage", "Mage"), "acc-1")

        assertEquals(1, captureGrpcClassId())
    }

    @Test
    fun `Assassin maps to class ID 2`() = runTest {
        coEvery { characterStub.createCharacter(any(), any()) } returns
            CreateCharacterResponse.newBuilder().setSuccess(true).setCharacterId("c-3").build()

        handler.handle(ctx, makePacket("TestAssassin", "Assassin"), "acc-1")

        assertEquals(2, captureGrpcClassId())
    }

    @Test
    fun `Cleric maps to class ID 3`() = runTest {
        coEvery { characterStub.createCharacter(any(), any()) } returns
            CreateCharacterResponse.newBuilder().setSuccess(true).setCharacterId("c-4").build()

        handler.handle(ctx, makePacket("TestCleric", "Cleric"), "acc-1")

        assertEquals(3, captureGrpcClassId())
    }

    @Test
    fun `class name is case-insensitive`() = runTest {
        coEvery { characterStub.createCharacter(any(), any()) } returns
            CreateCharacterResponse.newBuilder().setSuccess(true).setCharacterId("c-5").build()

        handler.handle(ctx, makePacket("TestHero", "WARRIOR"), "acc-1")

        assertEquals(0, captureGrpcClassId())
    }

    // ---- Roundtrip: create → list should be consistent ----

    @Test
    fun `all class names roundtrip through create and list consistently`() {
        for ((name, expectedId) in CharacterClassMapping.NAME_TO_ID) {
            val resolvedId = CharacterClassMapping.idForName(name)
            val resolvedName = CharacterClassMapping.nameForId(resolvedId)
            assertEquals(expectedId, resolvedId, "Create: '$name' should map to $expectedId")
            assertEquals(name, resolvedName.lowercase(),
                "List: $resolvedId should map back to '$name'")
        }
    }

    // ---- Validation ----

    @Test
    fun `invalid class name is rejected`() = runTest {
        handler.handle(ctx, makePacket("TestHero", "knight"), "acc-1")

        val response = captureResponse()
        assertFalse(response.success)
        coVerify(exactly = 0) { characterStub.createCharacter(any(), any()) }
    }

    @Test
    fun `name too short is rejected`() = runTest {
        handler.handle(ctx, makePacket("X", "Warrior"), "acc-1")

        val response = captureResponse()
        assertFalse(response.success)
        coVerify(exactly = 0) { characterStub.createCharacter(any(), any()) }
    }

    @Test
    fun `name too long is rejected`() = runTest {
        handler.handle(ctx, makePacket("A".repeat(17), "Warrior"), "acc-1")

        val response = captureResponse()
        assertFalse(response.success)
        coVerify(exactly = 0) { characterStub.createCharacter(any(), any()) }
    }

    @Test
    fun `name with invalid characters is rejected`() = runTest {
        handler.handle(ctx, makePacket("Test Hero!", "Warrior"), "acc-1")

        val response = captureResponse()
        assertFalse(response.success)
        coVerify(exactly = 0) { characterStub.createCharacter(any(), any()) }
    }
}
