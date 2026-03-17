package com.flyagain.account.handler

import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.CharacterRecord
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.CharacterSelectRequest
import com.flyagain.common.proto.EnterWorldResponse
import com.flyagain.common.proto.Opcode
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.*
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterSelectHandlerTest {

    private val characterStub = mockk<CharacterDataServiceGrpcKt.CharacterDataServiceCoroutineStub>()
    private val redisSync = mockk<RedisCommands<String, String>>(relaxed = true)
    private val redisConnection = mockk<StatefulRedisConnection<String, String>> {
        every { sync() } returns redisSync
    }
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private val handler = CharacterSelectHandler(
        characterDataStub = characterStub,
        redisConnection = redisConnection,
        worldServiceHost = "127.0.0.1",
        worldServiceTcpPort = 7780,
        worldServiceUdpPort = 7781
    )

    init {
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
    }

    private val accountId = "acc-123"
    private val characterId = "char-456"

    private fun makePacket(characterId: String): Packet {
        val request = CharacterSelectRequest.newBuilder()
            .setCharacterId(characterId)
            .build()
        return Packet(Opcode.CHARACTER_SELECT_VALUE, request.toByteArray())
    }

    private fun captureResponse(): EnterWorldResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return EnterWorldResponse.parseFrom(slot.captured.payload)
    }

    private fun buildCharacterRecord(
        id: String = characterId,
        acctId: String = accountId,
        found: Boolean = true,
        name: String = "TestHero",
        characterClass: Int = 0,
        level: Int = 10,
        xp: Long = 5000,
        hp: Int = 500,
        mp: Int = 200,
        maxHp: Int = 500,
        maxMp: Int = 200,
        str: Int = 30,
        sta: Int = 25,
        dex: Int = 20,
        intStat: Int = 15,
        statPoints: Int = 5,
        mapId: Int = 1,
        posX: Float = 100.0f,
        posY: Float = 50.0f,
        posZ: Float = 200.0f,
        rotation: Float = 1.5f,
        gold: Long = 10000
    ): CharacterRecord {
        return CharacterRecord.newBuilder()
            .setId(id)
            .setAccountId(acctId)
            .setFound(found)
            .setName(name)
            .setCharacterClass(characterClass)
            .setLevel(level)
            .setXp(xp)
            .setHp(hp)
            .setMp(mp)
            .setMaxHp(maxHp)
            .setMaxMp(maxMp)
            .setStr(str)
            .setSta(sta)
            .setDex(dex)
            .setIntStat(intStat)
            .setStatPoints(statPoints)
            .setMapId(mapId)
            .setPosX(posX)
            .setPosY(posY)
            .setPosZ(posZ)
            .setRotation(rotation)
            .setGold(gold)
            .build()
    }

    // ---- Validation tests ----

    @Test
    fun `invalid payload returns error`() = runTest {
        val badPacket = Packet(Opcode.CHARACTER_SELECT_VALUE, byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))

        handler.handle(ctx, badPacket, accountId)

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals("Invalid request payload", response.errorMessage)
    }

    @Test
    fun `blank character ID returns error`() = runTest {
        handler.handle(ctx, makePacket(""), accountId)

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals("Invalid character ID", response.errorMessage)
    }

    // ---- gRPC failure ----

    @Test
    fun `gRPC exception returns internal server error`() = runTest {
        coEvery { characterStub.getCharacter(any(), any()) } throws RuntimeException("gRPC unavailable")

        handler.handle(ctx, makePacket(characterId), accountId)

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals("Internal server error", response.errorMessage)
    }

    // ---- Character not found ----

    @Test
    fun `character not found returns error`() = runTest {
        coEvery { characterStub.getCharacter(any(), any()) } returns buildCharacterRecord(found = false)

        handler.handle(ctx, makePacket(characterId), accountId)

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals("Character not found", response.errorMessage)
    }

    // ---- Ownership validation ----

    @Test
    fun `character belonging to different account returns not found`() = runTest {
        coEvery { characterStub.getCharacter(any(), any()) } returns buildCharacterRecord(acctId = "other-account")

        handler.handle(ctx, makePacket(characterId), accountId)

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals("Character not found", response.errorMessage)
    }

    // ---- Redis failure ----

    @Test
    fun `Redis failure returns internal server error`() = runTest {
        coEvery { characterStub.getCharacter(any(), any()) } returns buildCharacterRecord()
        every { redisSync.hset(any<String>(), any<Map<String, String>>()) } throws RuntimeException("Redis down")

        handler.handle(ctx, makePacket(characterId), accountId)

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals("Internal server error", response.errorMessage)
    }

    // ---- Success path ----

    @Test
    fun `successful select caches character in Redis`() = runTest {
        val record = buildCharacterRecord()
        coEvery { characterStub.getCharacter(any(), any()) } returns record

        handler.handle(ctx, makePacket(characterId), accountId)

        val mapSlot = slot<Map<String, String>>()
        verify { redisSync.hset("character:$characterId", capture(mapSlot)) }
        val cached = mapSlot.captured
        assertEquals(accountId, cached["account_id"])
        assertEquals("TestHero", cached["name"])
        assertEquals("0", cached["class"])
        assertEquals("10", cached["level"])
        assertEquals("5000", cached["xp"])
        assertEquals("500", cached["hp"])
        assertEquals("200", cached["mp"])
        assertEquals("500", cached["max_hp"])
        assertEquals("200", cached["max_mp"])
        assertEquals("30", cached["str"])
        assertEquals("25", cached["sta"])
        assertEquals("20", cached["dex"])
        assertEquals("15", cached["int"])
        assertEquals("5", cached["stat_points"])
        assertEquals("1", cached["map_id"])
        assertEquals("100.0", cached["pos_x"])
        assertEquals("50.0", cached["pos_y"])
        assertEquals("200.0", cached["pos_z"])
        assertEquals("1.5", cached["rotation"])
        assertEquals("10000", cached["gold"])
    }

    @Test
    fun `successful select sets Redis TTL`() = runTest {
        coEvery { characterStub.getCharacter(any(), any()) } returns buildCharacterRecord()

        handler.handle(ctx, makePacket(characterId), accountId)

        verify { redisSync.expire("character:$characterId", 300L) }
    }

    @Test
    fun `successful select returns EnterWorldResponse with correct data`() = runTest {
        val record = buildCharacterRecord(
            posX = 100.0f, posY = 50.0f, posZ = 200.0f,
            level = 10, hp = 500, maxHp = 500, mp = 200, maxMp = 200,
            str = 30, sta = 25, dex = 20, intStat = 15, xp = 5000
        )
        coEvery { characterStub.getCharacter(any(), any()) } returns record

        handler.handle(ctx, makePacket(characterId), accountId)

        val response = captureResponse()
        assertTrue(response.success)

        // Position
        assertEquals(100.0f, response.position.x)
        assertEquals(50.0f, response.position.y)
        assertEquals(200.0f, response.position.z)

        // Stats
        assertEquals(10, response.stats.level)
        assertEquals(500, response.stats.hp)
        assertEquals(500, response.stats.maxHp)
        assertEquals(200, response.stats.mp)
        assertEquals(200, response.stats.maxMp)
        assertEquals(30, response.stats.str)
        assertEquals(25, response.stats.sta)
        assertEquals(20, response.stats.dex)
        assertEquals(15, response.stats.`int`)
        assertEquals(5000, response.stats.xp)

        // World service endpoint
        assertEquals("127.0.0.1", response.worldServiceHost)
        assertEquals(7780, response.worldServiceTcpPort)
        assertEquals(7781, response.worldServiceUdpPort)
    }

    @Test
    fun `successful select sends packet with ENTER_WORLD opcode`() = runTest {
        coEvery { characterStub.getCharacter(any(), any()) } returns buildCharacterRecord()

        handler.handle(ctx, makePacket(characterId), accountId)

        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        assertEquals(Opcode.ENTER_WORLD_VALUE, slot.captured.opcode)
    }
}
