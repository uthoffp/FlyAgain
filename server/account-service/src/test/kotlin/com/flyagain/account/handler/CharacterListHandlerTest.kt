package com.flyagain.account.handler

import com.flyagain.common.CharacterClassMapping
import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.CharacterList
import com.flyagain.common.grpc.CharacterRecord
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.CharacterListResponse
import com.flyagain.common.proto.Opcode
import io.mockk.*
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CharacterListHandlerTest {

    private val characterStub = mockk<CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub>()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private val handler = CharacterListHandler(characterStub)

    init {
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
    }

    private fun captureResponse(): CharacterListResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return CharacterListResponse.parseFrom(slot.captured.payload)
    }

    @Test
    fun `maps all character classes correctly`() = runTest {
        val charList = CharacterList.newBuilder()
        for ((id, _) in CharacterClassMapping.ID_TO_NAME) {
            charList.addCharacters(
                CharacterRecord.newBuilder()
                    .setId("char-$id")
                    .setName("Hero$id")
                    .setCharacterClass(id)
                    .setLevel(id + 1)
                    .setFound(true)
                    .build()
            )
        }
        coEvery { characterStub.getCharactersByAccount(any(), any()) } returns charList.build()

        handler.handle(ctx, "acc-123")

        val response = captureResponse()
        assertEquals(4, response.charactersList.size)

        for (charInfo in response.charactersList) {
            val classId = charInfo.id.removePrefix("char-").toInt()
            val expectedName = CharacterClassMapping.nameForId(classId)
            assertEquals(expectedName, charInfo.characterClass,
                "Class ID $classId should map to '$expectedName'")
            assertEquals(classId + 1, charInfo.level,
                "Level should be preserved for class $classId")
        }
    }

    @Test
    fun `maps Warrior (class 0) correctly`() = runTest {
        val charList = CharacterList.newBuilder()
            .addCharacters(
                CharacterRecord.newBuilder()
                    .setId("w-1")
                    .setName("Tank")
                    .setCharacterClass(0)
                    .setLevel(5)
                    .setFound(true)
                    .build()
            ).build()
        coEvery { characterStub.getCharactersByAccount(any(), any()) } returns charList

        handler.handle(ctx, "acc-456")

        val response = captureResponse()
        assertEquals(1, response.charactersList.size)
        assertEquals("Warrior", response.charactersList[0].characterClass)
        assertEquals(5, response.charactersList[0].level)
        assertEquals("Tank", response.charactersList[0].name)
    }

    @Test
    fun `preserves character ID and name`() = runTest {
        val charList = CharacterList.newBuilder()
            .addCharacters(
                CharacterRecord.newBuilder()
                    .setId("uuid-abc")
                    .setName("TestChar")
                    .setCharacterClass(2)
                    .setLevel(15)
                    .setFound(true)
                    .build()
            ).build()
        coEvery { characterStub.getCharactersByAccount(any(), any()) } returns charList

        handler.handle(ctx, "acc-789")

        val response = captureResponse()
        assertEquals("uuid-abc", response.charactersList[0].id)
        assertEquals("TestChar", response.charactersList[0].name)
        assertEquals("Assassin", response.charactersList[0].characterClass)
        assertEquals(15, response.charactersList[0].level)
    }

    @Test
    fun `empty character list returns empty response`() = runTest {
        coEvery { characterStub.getCharactersByAccount(any(), any()) } returns
            CharacterList.newBuilder().build()

        handler.handle(ctx, "acc-empty")

        val response = captureResponse()
        assertEquals(0, response.charactersList.size)
    }
}
