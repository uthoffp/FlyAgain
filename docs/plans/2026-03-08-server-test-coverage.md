# Server Test Coverage Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add 15 new test files to close unit test coverage gaps across all 4 server modules + gRPC service tests for database-service.

**Architecture:** Each test file uses Mockk for dependency isolation, `runTest` for coroutines, backtick-named test methods, and the `slot<Packet>()` capture pattern for verifying responses. All tests are pure unit tests — no database, no Redis, no network I/O.

**Tech Stack:** JUnit 5 (kotlin.test), Mockk 1.13.16, kotlinx-coroutines-test, Netty embedded (ByteBuf for codec tests)

---

### Task 1: PacketTest (common)

**Files:**
- Create: `server/common/src/test/kotlin/com/flyagain/common/network/PacketTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.common.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse

class PacketTest {

    @Test
    fun `equality with same opcode and payload`() {
        val a = Packet(0x0001, byteArrayOf(1, 2, 3))
        val b = Packet(0x0001, byteArrayOf(1, 2, 3))
        assertEquals(a, b)
    }

    @Test
    fun `inequality with different opcode`() {
        val a = Packet(0x0001, byteArrayOf(1))
        val b = Packet(0x0002, byteArrayOf(1))
        assertNotEquals(a, b)
    }

    @Test
    fun `inequality with different payload`() {
        val a = Packet(0x0001, byteArrayOf(1))
        val b = Packet(0x0001, byteArrayOf(2))
        assertNotEquals(a, b)
    }

    @Test
    fun `equality with empty payloads`() {
        val a = Packet(0, byteArrayOf())
        val b = Packet(0, byteArrayOf())
        assertEquals(a, b)
    }

    @Test
    fun `not equal to null`() {
        val packet = Packet(1, byteArrayOf())
        assertFalse(packet.equals(null))
    }

    @Test
    fun `not equal to different type`() {
        val packet = Packet(1, byteArrayOf())
        assertFalse(packet.equals("not a packet"))
    }

    @Test
    fun `hashCode is consistent for equal packets`() {
        val a = Packet(0x00AB, byteArrayOf(10, 20))
        val b = Packet(0x00AB, byteArrayOf(10, 20))
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `hashCode differs for different opcodes`() {
        val a = Packet(0x0001, byteArrayOf(1))
        val b = Packet(0x0002, byteArrayOf(1))
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `identical packets work as HashMap keys`() {
        val map = HashMap<Packet, String>()
        map[Packet(1, byteArrayOf(5))] = "first"
        map[Packet(1, byteArrayOf(5))] = "second"
        assertEquals(1, map.size)
        assertEquals("second", map[Packet(1, byteArrayOf(5))])
    }

    @Test
    fun `toString formats opcode as 4-digit hex`() {
        val packet = Packet(0x00AB, byteArrayOf(1, 2, 3))
        assertEquals("Packet(opcode=0x00ab, payloadSize=3)", packet.toString())
    }

    @Test
    fun `toString with zero opcode and empty payload`() {
        val packet = Packet(0, byteArrayOf())
        assertEquals("Packet(opcode=0x0000, payloadSize=0)", packet.toString())
    }

    @Test
    fun `toString with max unsigned short opcode`() {
        val packet = Packet(0xFFFF, byteArrayOf(1))
        assertEquals("Packet(opcode=0xffff, payloadSize=1)", packet.toString())
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :common:test --tests "com.flyagain.common.network.PacketTest" --info`

**Step 3: Commit**

```
git add server/common/src/test/kotlin/com/flyagain/common/network/PacketTest.kt
git commit -m "test: add PacketTest for equality, hashCode, and toString"
```

---

### Task 2: PacketEncoderTest (common)

**Files:**
- Create: `server/common/src/test/kotlin/com/flyagain/common/network/PacketEncoderTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.common.network

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.Channel
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class PacketEncoderTest {

    private val encoder = PacketEncoder()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    init {
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        every { channel.remoteAddress() } returns InetSocketAddress("127.0.0.1", 1234)
    }

    @Test
    fun `encodes opcode as 2-byte big-endian unsigned short`() {
        val buf = Unpooled.buffer()
        encoder.encode(ctx, Packet(0x0102, byteArrayOf()), buf)

        assertEquals(2, buf.readableBytes())
        assertEquals(0x0102, buf.readUnsignedShort())
    }

    @Test
    fun `encodes opcode followed by payload bytes`() {
        val payload = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val buf = Unpooled.buffer()
        encoder.encode(ctx, Packet(0x0001, payload), buf)

        assertEquals(5, buf.readableBytes()) // 2 opcode + 3 payload
        assertEquals(0x0001, buf.readUnsignedShort())
        val readPayload = ByteArray(3)
        buf.readBytes(readPayload)
        assertEquals(payload.toList(), readPayload.toList())
    }

    @Test
    fun `encodes zero opcode`() {
        val buf = Unpooled.buffer()
        encoder.encode(ctx, Packet(0x0000, byteArrayOf()), buf)

        assertEquals(0x0000, buf.readUnsignedShort())
    }

    @Test
    fun `encodes max opcode 0xFFFF`() {
        val buf = Unpooled.buffer()
        encoder.encode(ctx, Packet(0xFFFF, byteArrayOf()), buf)

        assertEquals(0xFFFF, buf.readUnsignedShort())
    }

    @Test
    fun `encodes empty payload as opcode only`() {
        val buf = Unpooled.buffer()
        encoder.encode(ctx, Packet(0x0001, byteArrayOf()), buf)

        assertEquals(2, buf.readableBytes())
    }

    @Test
    fun `encodes large payload without truncation`() {
        val payload = ByteArray(10_000) { it.toByte() }
        val buf = Unpooled.buffer()
        encoder.encode(ctx, Packet(0x0001, payload), buf)

        assertEquals(10_002, buf.readableBytes()) // 2 + 10000
        buf.readUnsignedShort() // skip opcode
        val readPayload = ByteArray(10_000)
        buf.readBytes(readPayload)
        assertEquals(payload.toList(), readPayload.toList())
    }

    @Test
    fun `preserves null bytes in payload`() {
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x02)
        val buf = Unpooled.buffer()
        encoder.encode(ctx, Packet(0x0001, payload), buf)

        buf.readUnsignedShort()
        val readPayload = ByteArray(4)
        buf.readBytes(readPayload)
        assertEquals(payload.toList(), readPayload.toList())
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :common:test --tests "com.flyagain.common.network.PacketEncoderTest" --info`

**Step 3: Commit**

```
git add server/common/src/test/kotlin/com/flyagain/common/network/PacketEncoderTest.kt
git commit -m "test: add PacketEncoderTest for opcode and payload encoding"
```

---

### Task 3: PacketDecoderTest (common)

**Files:**
- Create: `server/common/src/test/kotlin/com/flyagain/common/network/PacketDecoderTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.common.network

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.Channel
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketDecoderTest {

    private val decoder = PacketDecoder()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    init {
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        every { channel.remoteAddress() } returns InetSocketAddress("127.0.0.1", 1234)
    }

    @Test
    fun `decodes opcode and payload from valid frame`() {
        val buf = Unpooled.buffer()
        buf.writeShort(0x0102)
        buf.writeBytes(byteArrayOf(0xAA.toByte(), 0xBB.toByte()))

        val out = mutableListOf<Any>()
        decoder.decode(ctx, buf, out)

        assertEquals(1, out.size)
        val packet = out[0] as Packet
        assertEquals(0x0102, packet.opcode)
        assertEquals(listOf(0xAA.toByte(), 0xBB.toByte()), packet.payload.toList())
    }

    @Test
    fun `decodes opcode-only frame as empty payload`() {
        val buf = Unpooled.buffer()
        buf.writeShort(0x0001)

        val out = mutableListOf<Any>()
        decoder.decode(ctx, buf, out)

        assertEquals(1, out.size)
        val packet = out[0] as Packet
        assertEquals(0x0001, packet.opcode)
        assertEquals(0, packet.payload.size)
    }

    @Test
    fun `emits nothing for undersized buffer with 1 byte`() {
        val buf = Unpooled.buffer()
        buf.writeByte(0xFF)

        val out = mutableListOf<Any>()
        decoder.decode(ctx, buf, out)

        assertTrue(out.isEmpty())
    }

    @Test
    fun `emits nothing for empty buffer`() {
        val buf = Unpooled.buffer()
        val out = mutableListOf<Any>()
        decoder.decode(ctx, buf, out)

        assertTrue(out.isEmpty())
    }

    @Test
    fun `decodes zero opcode`() {
        val buf = Unpooled.buffer()
        buf.writeShort(0x0000)

        val out = mutableListOf<Any>()
        decoder.decode(ctx, buf, out)

        assertEquals(0, (out[0] as Packet).opcode)
    }

    @Test
    fun `decodes max opcode 0xFFFF`() {
        val buf = Unpooled.buffer()
        buf.writeShort(0xFFFF.toInt())

        val out = mutableListOf<Any>()
        decoder.decode(ctx, buf, out)

        assertEquals(0xFFFF, (out[0] as Packet).opcode)
    }

    @Test
    fun `decodes large payload without truncation`() {
        val payload = ByteArray(5000) { it.toByte() }
        val buf = Unpooled.buffer()
        buf.writeShort(0x0001)
        buf.writeBytes(payload)

        val out = mutableListOf<Any>()
        decoder.decode(ctx, buf, out)

        val packet = out[0] as Packet
        assertEquals(5000, packet.payload.size)
        assertEquals(payload.toList(), packet.payload.toList())
    }

    @Test
    fun `consumes all readable bytes from buffer`() {
        val buf = Unpooled.buffer()
        buf.writeShort(0x0001)
        buf.writeBytes(byteArrayOf(1, 2, 3))

        val out = mutableListOf<Any>()
        decoder.decode(ctx, buf, out)

        assertEquals(0, buf.readableBytes())
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :common:test --tests "com.flyagain.common.network.PacketDecoderTest" --info`

**Step 3: Commit**

```
git add server/common/src/test/kotlin/com/flyagain/common/network/PacketDecoderTest.kt
git commit -m "test: add PacketDecoderTest for frame decoding and edge cases"
```

---

### Task 4: ConnectionLimiterTest (common)

**Files:**
- Create: `server/common/src/test/kotlin/com/flyagain/common/network/ConnectionLimiterTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.common.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionLimiterTest {

    private fun mockCtx(ip: String = "192.168.1.1", port: Int = 12345): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        every { channel.remoteAddress() } returns InetSocketAddress(ip, port)
        every { ctx.close() } returns mockk<ChannelFuture>()
        return ctx
    }

    @Test
    fun `accepts connections under total limit`() {
        val limiter = ConnectionLimiter(maxConnections = 3, maxConnectionsPerIp = 10)
        val ctx1 = mockCtx("10.0.0.1")
        val ctx2 = mockCtx("10.0.0.2")
        val ctx3 = mockCtx("10.0.0.3")

        limiter.channelActive(ctx1)
        limiter.channelActive(ctx2)
        limiter.channelActive(ctx3)

        assertEquals(3, limiter.getTotalConnections())
        verify(exactly = 0) { ctx1.close() }
        verify(exactly = 0) { ctx2.close() }
        verify(exactly = 0) { ctx3.close() }
    }

    @Test
    fun `rejects connection exceeding total limit`() {
        val limiter = ConnectionLimiter(maxConnections = 2, maxConnectionsPerIp = 10)
        limiter.channelActive(mockCtx("10.0.0.1"))
        limiter.channelActive(mockCtx("10.0.0.2"))

        val rejected = mockCtx("10.0.0.3")
        limiter.channelActive(rejected)

        verify(exactly = 1) { rejected.close() }
        assertEquals(2, limiter.getTotalConnections())
    }

    @Test
    fun `accepts connections under per-IP limit`() {
        val limiter = ConnectionLimiter(maxConnections = 100, maxConnectionsPerIp = 3)
        val ctx1 = mockCtx("10.0.0.1", 1001)
        val ctx2 = mockCtx("10.0.0.1", 1002)
        val ctx3 = mockCtx("10.0.0.1", 1003)

        limiter.channelActive(ctx1)
        limiter.channelActive(ctx2)
        limiter.channelActive(ctx3)

        assertEquals(3, limiter.getConnectionsForIp("10.0.0.1"))
    }

    @Test
    fun `rejects connection exceeding per-IP limit`() {
        val limiter = ConnectionLimiter(maxConnections = 100, maxConnectionsPerIp = 2)
        limiter.channelActive(mockCtx("10.0.0.1", 1001))
        limiter.channelActive(mockCtx("10.0.0.1", 1002))

        val rejected = mockCtx("10.0.0.1", 1003)
        limiter.channelActive(rejected)

        verify(exactly = 1) { rejected.close() }
        assertEquals(2, limiter.getConnectionsForIp("10.0.0.1"))
    }

    @Test
    fun `per-IP limits are independent between IPs`() {
        val limiter = ConnectionLimiter(maxConnections = 100, maxConnectionsPerIp = 1)
        val ctx1 = mockCtx("10.0.0.1")
        val ctx2 = mockCtx("10.0.0.2")

        limiter.channelActive(ctx1)
        limiter.channelActive(ctx2)

        assertEquals(1, limiter.getConnectionsForIp("10.0.0.1"))
        assertEquals(1, limiter.getConnectionsForIp("10.0.0.2"))
        verify(exactly = 0) { ctx1.close() }
        verify(exactly = 0) { ctx2.close() }
    }

    @Test
    fun `channelInactive decrements counters`() {
        val limiter = ConnectionLimiter(maxConnections = 10, maxConnectionsPerIp = 10)
        val ctx1 = mockCtx("10.0.0.1")

        limiter.channelActive(ctx1)
        assertEquals(1, limiter.getTotalConnections())

        limiter.channelInactive(ctx1)
        assertEquals(0, limiter.getTotalConnections())
        assertEquals(0, limiter.getConnectionsForIp("10.0.0.1"))
    }

    @Test
    fun `IP entry removed when last connection closes`() {
        val limiter = ConnectionLimiter(maxConnections = 10, maxConnectionsPerIp = 10)
        val ctx1 = mockCtx("10.0.0.1", 1001)
        val ctx2 = mockCtx("10.0.0.1", 1002)

        limiter.channelActive(ctx1)
        limiter.channelActive(ctx2)
        assertEquals(2, limiter.getConnectionsForIp("10.0.0.1"))

        limiter.channelInactive(ctx1)
        assertEquals(1, limiter.getConnectionsForIp("10.0.0.1"))

        limiter.channelInactive(ctx2)
        assertEquals(0, limiter.getConnectionsForIp("10.0.0.1"))
    }

    @Test
    fun `new connection accepted after previous one closed`() {
        val limiter = ConnectionLimiter(maxConnections = 1, maxConnectionsPerIp = 10)
        val ctx1 = mockCtx("10.0.0.1")

        limiter.channelActive(ctx1)
        limiter.channelInactive(ctx1)

        val ctx2 = mockCtx("10.0.0.2")
        limiter.channelActive(ctx2)

        assertEquals(1, limiter.getTotalConnections())
        verify(exactly = 0) { ctx2.close() }
    }

    @Test
    fun `exceptionCaught closes the channel`() {
        val limiter = ConnectionLimiter(maxConnections = 10, maxConnectionsPerIp = 10)
        val ctx = mockCtx("10.0.0.1")

        limiter.exceptionCaught(ctx, RuntimeException("test error"))

        verify(exactly = 1) { ctx.close() }
    }

    @Test
    fun `getConnectionsForIp returns 0 for unknown IP`() {
        val limiter = ConnectionLimiter(maxConnections = 10, maxConnectionsPerIp = 10)
        assertEquals(0, limiter.getConnectionsForIp("unknown-ip"))
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :common:test --tests "com.flyagain.common.network.ConnectionLimiterTest" --info`

**Step 3: Commit**

```
git add server/common/src/test/kotlin/com/flyagain/common/network/ConnectionLimiterTest.kt
git commit -m "test: add ConnectionLimiterTest for total/per-IP limits and cleanup"
```

---

### Task 5: AccountGrpcServiceTest (database-service)

**Files:**
- Create: `server/database-service/src/test/kotlin/com/flyagain/database/grpc/AccountGrpcServiceTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.AccountRepository
import io.grpc.StatusException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountGrpcServiceTest {

    private val accountRepo = mockk<AccountRepository>()
    private val service = AccountGrpcService(accountRepo)

    // --- getAccountByUsername ---

    @Test
    fun `getAccountByUsername returns account when found`() = runTest {
        val record = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-1")
            .setUsername("testuser")
            .setPasswordHash("hashed")
            .setIsBanned(false)
            .build()
        coEvery { accountRepo.getByUsername("testuser") } returns record

        val result = service.getAccountByUsername(
            GetAccountRequest.newBuilder().setUsername("testuser").build()
        )

        assertTrue(result.found)
        assertEquals("acc-1", result.id)
        assertEquals("testuser", result.username)
    }

    @Test
    fun `getAccountByUsername returns found=false when not found`() = runTest {
        coEvery { accountRepo.getByUsername("nobody") } returns null

        val result = service.getAccountByUsername(
            GetAccountRequest.newBuilder().setUsername("nobody").build()
        )

        assertFalse(result.found)
    }

    // --- getAccountById ---

    @Test
    fun `getAccountById returns account when found`() = runTest {
        val record = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-1")
            .setUsername("player1")
            .build()
        coEvery { accountRepo.getById("acc-1") } returns record

        val result = service.getAccountById(
            GetAccountByIdRequest.newBuilder().setAccountId("acc-1").build()
        )

        assertTrue(result.found)
        assertEquals("player1", result.username)
    }

    @Test
    fun `getAccountById returns found=false when not found`() = runTest {
        coEvery { accountRepo.getById("nonexistent") } returns null

        val result = service.getAccountById(
            GetAccountByIdRequest.newBuilder().setAccountId("nonexistent").build()
        )

        assertFalse(result.found)
    }

    // --- createAccount ---

    @Test
    fun `createAccount returns success with account ID`() = runTest {
        coEvery { accountRepo.create("newuser", "new@test.com", "hashed") } returns "acc-new"

        val result = service.createAccount(
            CreateAccountRequest.newBuilder()
                .setUsername("newuser")
                .setEmail("new@test.com")
                .setPasswordHash("hashed")
                .build()
        )

        assertTrue(result.success)
        assertEquals("acc-new", result.accountId)
    }

    @Test
    fun `createAccount returns failure on duplicate username`() = runTest {
        coEvery { accountRepo.create("taken", "a@b.com", "hash") } throws
            RuntimeException("duplicate key value violates unique constraint")

        val result = service.createAccount(
            CreateAccountRequest.newBuilder()
                .setUsername("taken")
                .setEmail("a@b.com")
                .setPasswordHash("hash")
                .build()
        )

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("duplicate"))
    }

    // --- updateLastLogin ---

    @Test
    fun `updateLastLogin calls repository`() = runTest {
        coEvery { accountRepo.updateLastLogin("acc-1") } returns Unit

        service.updateLastLogin(
            UpdateLastLoginRequest.newBuilder().setAccountId("acc-1").build()
        )

        coVerify(exactly = 1) { accountRepo.updateLastLogin("acc-1") }
    }

    // --- checkBan ---

    @Test
    fun `checkBan returns ban status for existing account`() = runTest {
        val record = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-1")
            .setIsBanned(true)
            .setBanReason("Cheating")
            .setBanUntil(9999999999L)
            .build()
        coEvery { accountRepo.getById("acc-1") } returns record

        val result = service.checkBan(
            CheckBanRequest.newBuilder().setAccountId("acc-1").build()
        )

        assertTrue(result.isBanned)
        assertEquals("Cheating", result.banReason)
    }

    @Test
    fun `checkBan returns not banned for clean account`() = runTest {
        val record = AccountRecord.newBuilder()
            .setFound(true)
            .setId("acc-1")
            .setIsBanned(false)
            .build()
        coEvery { accountRepo.getById("acc-1") } returns record

        val result = service.checkBan(
            CheckBanRequest.newBuilder().setAccountId("acc-1").build()
        )

        assertFalse(result.isBanned)
    }

    @Test
    fun `checkBan throws NOT_FOUND for missing account`() = runTest {
        coEvery { accountRepo.getById("ghost") } returns null

        assertFailsWith<StatusException> {
            service.checkBan(
                CheckBanRequest.newBuilder().setAccountId("ghost").build()
            )
        }
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :database-service:test --tests "com.flyagain.database.grpc.AccountGrpcServiceTest" --info`

**Step 3: Commit**

```
git add server/database-service/src/test/kotlin/com/flyagain/database/grpc/AccountGrpcServiceTest.kt
git commit -m "test: add AccountGrpcServiceTest for CRUD and ban checks"
```

---

### Task 6: CharacterGrpcServiceTest (database-service)

**Files:**
- Create: `server/database-service/src/test/kotlin/com/flyagain/database/grpc/CharacterGrpcServiceTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.CharacterRepository
import com.flyagain.database.repository.GameDataRepository
import com.google.protobuf.Empty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterGrpcServiceTest {

    private val characterRepo = mockk<CharacterRepository>()
    private val gameDataRepo = mockk<GameDataRepository>()
    private val service = CharacterGrpcService(characterRepo, gameDataRepo)

    // --- getCharactersByAccount ---

    @Test
    fun `getCharactersByAccount returns character list`() = runTest {
        val chars = listOf(
            CharacterRecord.newBuilder().setId("c-1").setName("Hero1").setFound(true).build(),
            CharacterRecord.newBuilder().setId("c-2").setName("Hero2").setFound(true).build()
        )
        coEvery { characterRepo.getByAccount("acc-1") } returns chars

        val result = service.getCharactersByAccount(
            GetCharactersByAccountRequest.newBuilder().setAccountId("acc-1").build()
        )

        assertEquals(2, result.charactersList.size)
        assertEquals("Hero1", result.charactersList[0].name)
    }

    @Test
    fun `getCharactersByAccount returns empty list for no characters`() = runTest {
        coEvery { characterRepo.getByAccount("acc-empty") } returns emptyList()

        val result = service.getCharactersByAccount(
            GetCharactersByAccountRequest.newBuilder().setAccountId("acc-empty").build()
        )

        assertEquals(0, result.charactersList.size)
    }

    // --- getCharacter ---

    @Test
    fun `getCharacter returns character when found and owned`() = runTest {
        val record = CharacterRecord.newBuilder()
            .setId("c-1").setName("Hero").setFound(true).setLevel(5).build()
        coEvery { characterRepo.getById("c-1", "acc-1") } returns record

        val result = service.getCharacter(
            GetCharacterRequest.newBuilder().setCharacterId("c-1").setAccountId("acc-1").build()
        )

        assertTrue(result.found)
        assertEquals("Hero", result.name)
        assertEquals(5, result.level)
    }

    @Test
    fun `getCharacter returns found=false when not found`() = runTest {
        coEvery { characterRepo.getById("c-missing", "acc-1") } returns null

        val result = service.getCharacter(
            GetCharacterRequest.newBuilder().setCharacterId("c-missing").setAccountId("acc-1").build()
        )

        assertFalse(result.found)
    }

    // --- createCharacter ---

    @Test
    fun `createCharacter returns success with ID`() = runTest {
        coEvery { characterRepo.create("acc-1", "NewHero", 0) } returns "c-new"

        val result = service.createCharacter(
            CreateCharacterRequest.newBuilder()
                .setAccountId("acc-1").setName("NewHero").setCharacterClass(0).build()
        )

        assertTrue(result.success)
        assertEquals("c-new", result.characterId)
    }

    @Test
    fun `createCharacter returns failure on 3-character limit`() = runTest {
        coEvery { characterRepo.create("acc-1", "FourthHero", 1) } throws
            IllegalStateException("Maximum of 3 characters per account")

        val result = service.createCharacter(
            CreateCharacterRequest.newBuilder()
                .setAccountId("acc-1").setName("FourthHero").setCharacterClass(1).build()
        )

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("3 characters"))
    }

    // --- saveCharacter ---

    @Test
    fun `saveCharacter calls repository`() = runTest {
        coEvery { characterRepo.save(any()) } returns Unit

        val request = SaveCharacterRequest.newBuilder()
            .setCharacterId("c-1").setHp(100).setMp(50).build()
        service.saveCharacter(request)

        coVerify(exactly = 1) { characterRepo.save(request) }
    }

    // --- deleteCharacter ---

    @Test
    fun `deleteCharacter calls softDelete`() = runTest {
        coEvery { characterRepo.softDelete("c-1", "acc-1") } returns Unit

        service.deleteCharacter(
            DeleteCharacterRequest.newBuilder().setCharacterId("c-1").setAccountId("acc-1").build()
        )

        coVerify(exactly = 1) { characterRepo.softDelete("c-1", "acc-1") }
    }

    // --- getCharacterSkills ---

    @Test
    fun `getCharacterSkills returns skill list`() = runTest {
        val skills = listOf(
            CharacterSkillRecord.newBuilder().setSkillId(1).setSkillLevel(2).build()
        )
        coEvery { gameDataRepo.getCharacterSkills("c-1") } returns skills

        val result = service.getCharacterSkills(
            GetCharacterSkillsRequest.newBuilder().setCharacterId("c-1").build()
        )

        assertEquals(1, result.skillsList.size)
        assertEquals(1, result.skillsList[0].skillId)
    }

    @Test
    fun `getCharacterSkills returns empty list for no skills`() = runTest {
        coEvery { gameDataRepo.getCharacterSkills("c-new") } returns emptyList()

        val result = service.getCharacterSkills(
            GetCharacterSkillsRequest.newBuilder().setCharacterId("c-new").build()
        )

        assertEquals(0, result.skillsList.size)
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :database-service:test --tests "com.flyagain.database.grpc.CharacterGrpcServiceTest" --info`

**Step 3: Commit**

```
git add server/database-service/src/test/kotlin/com/flyagain/database/grpc/CharacterGrpcServiceTest.kt
git commit -m "test: add CharacterGrpcServiceTest for CRUD, save, and skills"
```

---

### Task 7: GameDataGrpcServiceTest (database-service)

**Files:**
- Create: `server/database-service/src/test/kotlin/com/flyagain/database/grpc/GameDataGrpcServiceTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.GameDataRepository
import com.google.protobuf.Empty
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GameDataGrpcServiceTest {

    private val gameDataRepo = mockk<GameDataRepository>()
    private val service = GameDataGrpcService(gameDataRepo)

    @Test
    fun `getAllItemDefinitions returns items from repo`() = runTest {
        val items = listOf(
            ItemDefinitionRecord.newBuilder().setId(1).setName("Sword").build(),
            ItemDefinitionRecord.newBuilder().setId(2).setName("Shield").build()
        )
        coEvery { gameDataRepo.getAllItemDefinitions() } returns items

        val result = service.getAllItemDefinitions(Empty.getDefaultInstance())

        assertEquals(2, result.itemsList.size)
        assertEquals("Sword", result.itemsList[0].name)
    }

    @Test
    fun `getAllItemDefinitions returns empty list`() = runTest {
        coEvery { gameDataRepo.getAllItemDefinitions() } returns emptyList()

        val result = service.getAllItemDefinitions(Empty.getDefaultInstance())

        assertEquals(0, result.itemsList.size)
    }

    @Test
    fun `getAllMonsterDefinitions returns monsters from repo`() = runTest {
        val monsters = listOf(
            MonsterDefinitionRecord.newBuilder().setId(1).setName("Slime").build()
        )
        coEvery { gameDataRepo.getAllMonsterDefinitions() } returns monsters

        val result = service.getAllMonsterDefinitions(Empty.getDefaultInstance())

        assertEquals(1, result.monstersList.size)
        assertEquals("Slime", result.monstersList[0].name)
    }

    @Test
    fun `getAllMonsterSpawns returns spawns from repo`() = runTest {
        val spawns = listOf(
            MonsterSpawnRecord.newBuilder().setId(1).setMonsterId(1).setMapId(1).build()
        )
        coEvery { gameDataRepo.getAllMonsterSpawns() } returns spawns

        val result = service.getAllMonsterSpawns(Empty.getDefaultInstance())

        assertEquals(1, result.spawnsList.size)
    }

    @Test
    fun `getAllSkillDefinitions returns skills from repo`() = runTest {
        val skills = listOf(
            SkillDefinitionRecord.newBuilder().setId(1).setName("Fireball").build()
        )
        coEvery { gameDataRepo.getAllSkillDefinitions() } returns skills

        val result = service.getAllSkillDefinitions(Empty.getDefaultInstance())

        assertEquals(1, result.skillsList.size)
        assertEquals("Fireball", result.skillsList[0].name)
    }

    @Test
    fun `getAllLootTables returns loot entries from repo`() = runTest {
        val entries = listOf(
            LootTableRecord.newBuilder().setId(1).setMonsterId(1).setItemId(1).build()
        )
        coEvery { gameDataRepo.getAllLootTables() } returns entries

        val result = service.getAllLootTables(Empty.getDefaultInstance())

        assertEquals(1, result.entriesList.size)
    }

    @Test
    fun `getAllLootTables returns empty list`() = runTest {
        coEvery { gameDataRepo.getAllLootTables() } returns emptyList()

        val result = service.getAllLootTables(Empty.getDefaultInstance())

        assertEquals(0, result.entriesList.size)
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :database-service:test --tests "com.flyagain.database.grpc.GameDataGrpcServiceTest" --info`

**Step 3: Commit**

```
git add server/database-service/src/test/kotlin/com/flyagain/database/grpc/GameDataGrpcServiceTest.kt
git commit -m "test: add GameDataGrpcServiceTest for all static data queries"
```

---

### Task 8: InventoryGrpcServiceTest (database-service)

**Files:**
- Create: `server/database-service/src/test/kotlin/com/flyagain/database/grpc/InventoryGrpcServiceTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.database.grpc

import com.flyagain.common.grpc.*
import com.flyagain.database.repository.InventoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryGrpcServiceTest {

    private val inventoryRepo = mockk<InventoryRepository>()
    private val service = InventoryGrpcService(inventoryRepo)

    // --- getInventory ---

    @Test
    fun `getInventory returns slots from repo`() = runTest {
        val slots = listOf(
            InventorySlot.newBuilder().setSlot(0).setItemId(1).setAmount(5).build()
        )
        coEvery { inventoryRepo.getInventory("c-1") } returns slots

        val result = service.getInventory(
            GetInventoryRequest.newBuilder().setCharacterId("c-1").build()
        )

        assertEquals(1, result.slotsList.size)
        assertEquals(1, result.slotsList[0].itemId)
    }

    @Test
    fun `getInventory returns empty for no items`() = runTest {
        coEvery { inventoryRepo.getInventory("c-1") } returns emptyList()

        val result = service.getInventory(
            GetInventoryRequest.newBuilder().setCharacterId("c-1").build()
        )

        assertEquals(0, result.slotsList.size)
    }

    // --- getEquipment ---

    @Test
    fun `getEquipment returns equipped items`() = runTest {
        val slots = listOf(
            EquipmentSlot.newBuilder().setSlotType(1).setItemId(10).build()
        )
        coEvery { inventoryRepo.getEquipment("c-1") } returns slots

        val result = service.getEquipment(
            GetEquipmentRequest.newBuilder().setCharacterId("c-1").build()
        )

        assertEquals(1, result.slotsList.size)
    }

    // --- moveItem ---

    @Test
    fun `moveItem returns success on valid move`() = runTest {
        coEvery { inventoryRepo.moveItem("c-1", 0, 5) } returns true

        val result = service.moveItem(
            MoveItemRequest.newBuilder().setCharacterId("c-1").setFromSlot(0).setToSlot(5).build()
        )

        assertTrue(result.success)
    }

    @Test
    fun `moveItem returns failure when item not found`() = runTest {
        coEvery { inventoryRepo.moveItem("c-1", 0, 5) } returns false

        val result = service.moveItem(
            MoveItemRequest.newBuilder().setCharacterId("c-1").setFromSlot(0).setToSlot(5).build()
        )

        assertFalse(result.success)
        assertEquals("Item not found", result.errorMessage)
    }

    // --- addItem ---

    @Test
    fun `addItem returns assigned slot on success`() = runTest {
        coEvery { inventoryRepo.addItem("c-1", 42, 1) } returns 7

        val result = service.addItem(
            AddItemRequest.newBuilder().setCharacterId("c-1").setItemId(42).setAmount(1).build()
        )

        assertTrue(result.success)
        assertEquals(7, result.assignedSlot)
    }

    @Test
    fun `addItem returns failure when inventory full`() = runTest {
        coEvery { inventoryRepo.addItem("c-1", 42, 1) } throws
            NoSuchElementException("No free inventory slot")

        val result = service.addItem(
            AddItemRequest.newBuilder().setCharacterId("c-1").setItemId(42).setAmount(1).build()
        )

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("free inventory slot"))
    }

    // --- removeItem ---

    @Test
    fun `removeItem calls repository`() = runTest {
        coEvery { inventoryRepo.removeItem("c-1", 3, 1) } returns Unit

        service.removeItem(
            RemoveItemRequest.newBuilder().setCharacterId("c-1").setSlot(3).setAmount(1).build()
        )

        coVerify(exactly = 1) { inventoryRepo.removeItem("c-1", 3, 1) }
    }

    // --- equipItem ---

    @Test
    fun `equipItem returns success when valid`() = runTest {
        coEvery { inventoryRepo.equipItem("c-1", 0, 1) } returns true

        val result = service.equipItem(
            EquipItemRequest.newBuilder().setCharacterId("c-1").setInventorySlot(0).setEquipSlotType(1).build()
        )

        assertTrue(result.success)
    }

    @Test
    fun `equipItem returns failure when item not found`() = runTest {
        coEvery { inventoryRepo.equipItem("c-1", 99, 1) } returns false

        val result = service.equipItem(
            EquipItemRequest.newBuilder().setCharacterId("c-1").setInventorySlot(99).setEquipSlotType(1).build()
        )

        assertFalse(result.success)
        assertEquals("Item not found", result.errorMessage)
    }

    // --- unequipItem ---

    @Test
    fun `unequipItem returns success when equipped`() = runTest {
        coEvery { inventoryRepo.unequipItem("c-1", 1) } returns true

        val result = service.unequipItem(
            UnequipItemRequest.newBuilder().setCharacterId("c-1").setEquipSlotType(1).build()
        )

        assertTrue(result.success)
    }

    @Test
    fun `unequipItem returns failure when slot empty`() = runTest {
        coEvery { inventoryRepo.unequipItem("c-1", 1) } returns false

        val result = service.unequipItem(
            UnequipItemRequest.newBuilder().setCharacterId("c-1").setEquipSlotType(1).build()
        )

        assertFalse(result.success)
        assertEquals("No item equipped in that slot", result.errorMessage)
    }

    // --- NPC stubs ---

    @Test
    fun `npcBuy returns not implemented`() = runTest {
        val result = service.npcBuy(NpcBuyRequest.getDefaultInstance())

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("Not implemented"))
    }

    @Test
    fun `npcSell returns not implemented`() = runTest {
        val result = service.npcSell(NpcSellRequest.getDefaultInstance())

        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("Not implemented"))
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :database-service:test --tests "com.flyagain.database.grpc.InventoryGrpcServiceTest" --info`

**Step 3: Commit**

```
git add server/database-service/src/test/kotlin/com/flyagain/database/grpc/InventoryGrpcServiceTest.kt
git commit -m "test: add InventoryGrpcServiceTest for all inventory operations"
```

---

### Task 9: RegisterHandlerTest (login-service)

**Files:**
- Create: `server/login-service/src/test/kotlin/com/flyagain/login/handler/RegisterHandlerTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.login.handler

import com.flyagain.common.grpc.AccountDataServiceGrpcKt
import com.flyagain.common.grpc.CreateAccountResponse
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.RegisterRequest
import com.flyagain.common.proto.RegisterResponse
import com.flyagain.login.auth.PasswordHasher
import com.flyagain.login.ratelimit.RateLimiter
import io.mockk.*
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegisterHandlerTest {

    private val accountStub = mockk<AccountDataServiceGrpcKt.AccountDataServiceCoroutineStub>()
    private val passwordHasher = mockk<PasswordHasher>()
    private val rateLimiter = mockk<RateLimiter>()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private val handler = RegisterHandler(accountStub, passwordHasher, rateLimiter)

    init {
        val channel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns channel
        every { channel.remoteAddress() } returns InetSocketAddress("127.0.0.1", 12345)
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
    }

    private fun captureResponse(): RegisterResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return RegisterResponse.parseFrom(slot.captured.payload)
    }

    private fun makeRequest(
        username: String = "validuser",
        email: String = "valid@test.com",
        password: String = "securepass"
    ): RegisterRequest {
        return RegisterRequest.newBuilder()
            .setUsername(username)
            .setEmail(email)
            .setPassword(password)
            .build()
    }

    private fun setupAllowRate() {
        coEvery { rateLimiter.checkRegisterRateLimit(any()) } returns true
    }

    private fun setupSuccessfulCreate() {
        setupAllowRate()
        every { passwordHasher.hash(any()) } returns "bcrypt-hash"
        coEvery { accountStub.createAccount(any()) } returns
            CreateAccountResponse.newBuilder().setSuccess(true).setAccountId("acc-1").build()
    }

    // --- Rate limiting ---

    @Test
    fun `rejects when rate limited`() = runTest {
        coEvery { rateLimiter.checkRegisterRateLimit(any()) } returns false

        handler.handle(ctx, makeRequest())

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("Too many"))
    }

    // --- Username validation ---

    @Test
    fun `rejects blank username`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(username = ""))

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("Username"))
    }

    @Test
    fun `rejects username shorter than 3 chars`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(username = "ab"))

        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects username longer than 16 chars`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(username = "a".repeat(17)))

        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects username with special characters`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(username = "user name!"))

        assertFalse(captureResponse().success)
    }

    @Test
    fun `accepts username with hyphens`() = runTest {
        setupSuccessfulCreate()
        handler.handle(ctx, makeRequest(username = "my-user"))

        assertTrue(captureResponse().success)
    }

    // --- Email validation ---

    @Test
    fun `rejects blank email`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(email = ""))

        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects invalid email format`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(email = "not-an-email"))

        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects email longer than 254 chars`() = runTest {
        setupAllowRate()
        val longEmail = "a".repeat(250) + "@b.com"
        handler.handle(ctx, makeRequest(email = longEmail))

        assertFalse(captureResponse().success)
    }

    // --- Password validation ---

    @Test
    fun `rejects password shorter than 8 chars`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(password = "short"))

        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects password longer than 72 chars (bcrypt max)`() = runTest {
        setupAllowRate()
        handler.handle(ctx, makeRequest(password = "a".repeat(73)))

        assertFalse(captureResponse().success)
    }

    // --- Password hash failure ---

    @Test
    fun `returns service unavailable on hash failure`() = runTest {
        setupAllowRate()
        every { passwordHasher.hash(any()) } throws RuntimeException("hash error")

        handler.handle(ctx, makeRequest())

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("unavailable"))
    }

    // --- gRPC failure ---

    @Test
    fun `returns service unavailable on gRPC failure`() = runTest {
        setupAllowRate()
        every { passwordHasher.hash(any()) } returns "hash"
        coEvery { accountStub.createAccount(any()) } throws RuntimeException("connection refused")

        handler.handle(ctx, makeRequest())

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("unavailable"))
    }

    @Test
    fun `returns gRPC error message on create failure`() = runTest {
        setupAllowRate()
        every { passwordHasher.hash(any()) } returns "hash"
        coEvery { accountStub.createAccount(any()) } returns
            CreateAccountResponse.newBuilder().setSuccess(false).setErrorMessage("Username taken").build()

        handler.handle(ctx, makeRequest())

        val response = captureResponse()
        assertFalse(response.success)
        assertEquals("Username taken", response.errorMessage)
    }

    // --- Success ---

    @Test
    fun `returns success on valid registration`() = runTest {
        setupSuccessfulCreate()

        handler.handle(ctx, makeRequest())

        assertTrue(captureResponse().success)
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :login-service:test --tests "com.flyagain.login.handler.RegisterHandlerTest" --info`

**Step 3: Commit**

```
git add server/login-service/src/test/kotlin/com/flyagain/login/handler/RegisterHandlerTest.kt
git commit -m "test: add RegisterHandlerTest for validation, rate limiting, and gRPC paths"
```

---

### Task 10: RateLimiterTest (login-service)

**Files:**
- Create: `server/login-service/src/test/kotlin/com/flyagain/login/ratelimit/RateLimiterTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.login.ratelimit

import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    private val asyncCommands = mockk<RedisAsyncCommands<String, String>>()
    private val redisConnection = mockk<StatefulRedisConnection<String, String>> {
        every { async() } returns asyncCommands
    }
    private val rateLimiter = RateLimiter(redisConnection)

    private fun mockIncr(key: String, returnValue: Long) {
        val future = mockk<RedisFuture<Long>>()
        every { future.toCompletableFuture() } returns CompletableFuture.completedFuture(returnValue)
        every { asyncCommands.incr(key) } returns future
    }

    private fun mockExpire(key: String) {
        val future = mockk<RedisFuture<Boolean>>()
        every { future.toCompletableFuture() } returns CompletableFuture.completedFuture(true)
        every { asyncCommands.expire(key, any<Long>()) } returns future
    }

    private fun mockGet(key: String, value: String?) {
        val future = mockk<RedisFuture<String>>()
        every { future.toCompletableFuture() } returns CompletableFuture.completedFuture(value)
        every { asyncCommands.get(key) } returns future
    }

    // --- Login rate limiting ---

    @Test
    fun `login allows first attempt`() = runTest {
        val key = "rate_limit:10.0.0.1:login"
        mockIncr(key, 1L)
        mockExpire(key)

        assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.1"))
    }

    @Test
    fun `login allows up to 5 attempts`() = runTest {
        val key = "rate_limit:10.0.0.1:login"
        mockExpire(key)

        for (i in 1L..5L) {
            mockIncr(key, i)
            assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.1"), "Attempt $i should be allowed")
        }
    }

    @Test
    fun `login rejects 6th attempt`() = runTest {
        val key = "rate_limit:10.0.0.1:login"
        mockIncr(key, 6L)

        assertFalse(rateLimiter.checkLoginRateLimit("10.0.0.1"))
    }

    // --- Register rate limiting ---

    @Test
    fun `register allows up to 3 attempts`() = runTest {
        val key = "rate_limit:10.0.0.1:register"
        mockExpire(key)

        for (i in 1L..3L) {
            mockIncr(key, i)
            assertTrue(rateLimiter.checkRegisterRateLimit("10.0.0.1"), "Attempt $i should be allowed")
        }
    }

    @Test
    fun `register rejects 4th attempt`() = runTest {
        val key = "rate_limit:10.0.0.1:register"
        mockIncr(key, 4L)

        assertFalse(rateLimiter.checkRegisterRateLimit("10.0.0.1"))
    }

    // --- IP isolation ---

    @Test
    fun `different IPs have separate counters`() = runTest {
        val key1 = "rate_limit:10.0.0.1:login"
        val key2 = "rate_limit:10.0.0.2:login"
        mockIncr(key1, 5L)
        mockIncr(key2, 1L)
        mockExpire(key2)

        // IP1 at limit
        assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.1"))
        // IP2 fresh
        assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.2"))
    }

    // --- Fail-open ---

    @Test
    fun `fails open when Redis is unavailable`() = runTest {
        val future = mockk<RedisFuture<Long>>()
        every { future.toCompletableFuture() } returns CompletableFuture<Long>().apply {
            completeExceptionally(RuntimeException("Redis down"))
        }
        every { asyncCommands.incr(any()) } returns future

        assertTrue(rateLimiter.checkLoginRateLimit("10.0.0.1"))
    }

    // --- getRemainingAttempts ---

    @Test
    fun `getRemainingAttempts returns correct count`() = runTest {
        mockGet("rate_limit:10.0.0.1:login", "3")

        assertEquals(2, rateLimiter.getRemainingAttempts("10.0.0.1", "login", 5))
    }

    @Test
    fun `getRemainingAttempts returns max when no key exists`() = runTest {
        mockGet("rate_limit:10.0.0.1:login", null)

        assertEquals(5, rateLimiter.getRemainingAttempts("10.0.0.1", "login", 5))
    }

    @Test
    fun `getRemainingAttempts returns 0 when over limit`() = runTest {
        mockGet("rate_limit:10.0.0.1:login", "10")

        assertEquals(0, rateLimiter.getRemainingAttempts("10.0.0.1", "login", 5))
    }

    @Test
    fun `getRemainingAttempts returns max on Redis failure`() = runTest {
        val future = mockk<RedisFuture<String>>()
        every { future.toCompletableFuture() } returns CompletableFuture<String>().apply {
            completeExceptionally(RuntimeException("Redis down"))
        }
        every { asyncCommands.get(any()) } returns future

        assertEquals(5, rateLimiter.getRemainingAttempts("10.0.0.1", "login", 5))
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :login-service:test --tests "com.flyagain.login.ratelimit.RateLimiterTest" --info`

**Step 3: Commit**

```
git add server/login-service/src/test/kotlin/com/flyagain/login/ratelimit/RateLimiterTest.kt
git commit -m "test: add RateLimiterTest for login/register limits and fail-open"
```

---

### Task 11: SessionManagerTest (login-service)

**Files:**
- Create: `server/login-service/src/test/kotlin/com/flyagain/login/session/SessionManagerTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.login.session

import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    private val asyncCommands = mockk<RedisAsyncCommands<String, String>>(relaxed = true)
    private val redisConnection = mockk<StatefulRedisConnection<String, String>> {
        every { async() } returns asyncCommands
    }
    private val sessionManager = SessionManager(redisConnection, sessionTtlSeconds = 3600L)

    // Helper to make RedisFuture mocks
    private fun <T> redisFuture(value: T): RedisFuture<T> {
        val future = mockk<RedisFuture<T>>()
        every { future.toCompletableFuture() } returns CompletableFuture.completedFuture(value)
        return future
    }

    private fun <T> failedFuture(error: Throwable): RedisFuture<T> {
        val future = mockk<RedisFuture<T>>()
        every { future.toCompletableFuture() } returns CompletableFuture<T>().apply {
            completeExceptionally(error)
        }
        return future
    }

    private fun setupBasicRedis() {
        every { asyncCommands.get(any()) } returns redisFuture(null)
        every { asyncCommands.hset(any(), any<Map<String, String>>()) } returns redisFuture(6L)
        every { asyncCommands.expire(any(), any<Long>()) } returns redisFuture(true)
        every { asyncCommands.set(any(), any()) } returns redisFuture("OK")
        every { asyncCommands.del(any<String>()) } returns redisFuture(1L)
        every { asyncCommands.hget(any(), any()) } returns redisFuture(null)
        every { asyncCommands.hgetall(any()) } returns redisFuture(emptyMap())
        every { asyncCommands.exists(any<String>()) } returns redisFuture(1L)
    }

    // --- createSession ---

    @Test
    fun `createSession returns true on success`() = runTest {
        setupBasicRedis()

        val result = sessionManager.createSession("sess-1", 12345L, "acc-1", "127.0.0.1", "secret")

        assertTrue(result)
    }

    @Test
    fun `createSession deletes old session on multi-login`() = runTest {
        setupBasicRedis()
        // Account already has a session
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("old-session")
        every { asyncCommands.hget("session:old-session", "accountId") } returns redisFuture("acc-1")
        every { asyncCommands.get(match<String> { it == "session:account:acc-1" && true }) } returnsMany
            listOf(redisFuture("old-session"), redisFuture("old-session"), redisFuture(null))

        val result = sessionManager.createSession("sess-new", 99L, "acc-1", "127.0.0.1", "secret2")

        assertTrue(result)
        // Verify the old session was deleted
        verify { asyncCommands.del("session:old-session") }
    }

    @Test
    fun `createSession returns false on Redis failure`() = runTest {
        every { asyncCommands.get(any()) } returns failedFuture(RuntimeException("Redis down"))

        val result = sessionManager.createSession("sess-1", 12345L, "acc-1", "127.0.0.1", "secret")

        assertFalse(result)
    }

    // --- getSession ---

    @Test
    fun `getSession returns data when session exists`() = runTest {
        setupBasicRedis()
        val sessionData = mapOf("accountId" to "acc-1", "ip" to "127.0.0.1")
        every { asyncCommands.hgetall("session:sess-1") } returns redisFuture(sessionData)

        val result = sessionManager.getSession("sess-1")

        assertEquals("acc-1", result?.get("accountId"))
    }

    @Test
    fun `getSession returns null when session missing`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hgetall("session:nonexistent") } returns redisFuture(emptyMap())

        val result = sessionManager.getSession("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getSession returns null on Redis failure`() = runTest {
        every { asyncCommands.hgetall(any()) } returns failedFuture(RuntimeException("timeout"))

        val result = sessionManager.getSession("sess-1")

        assertNull(result)
    }

    // --- getExistingSession ---

    @Test
    fun `getExistingSession returns session ID when active`() = runTest {
        setupBasicRedis()
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("sess-1")
        every { asyncCommands.exists("session:sess-1") } returns redisFuture(1L)

        val result = sessionManager.getExistingSession("acc-1")

        assertEquals("sess-1", result)
    }

    @Test
    fun `getExistingSession returns null when no session`() = runTest {
        setupBasicRedis()
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture(null)

        val result = sessionManager.getExistingSession("acc-1")

        assertNull(result)
    }

    @Test
    fun `getExistingSession cleans up stale reverse lookup`() = runTest {
        setupBasicRedis()
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("expired-sess")
        every { asyncCommands.exists("session:expired-sess") } returns redisFuture(0L)

        val result = sessionManager.getExistingSession("acc-1")

        assertNull(result)
        verify { asyncCommands.del("session:account:acc-1") }
    }

    // --- deleteSession ---

    @Test
    fun `deleteSession removes session and reverse lookup`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hget("session:sess-1", "accountId") } returns redisFuture("acc-1")
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("sess-1")

        sessionManager.deleteSession("sess-1")

        verify { asyncCommands.del("session:sess-1") }
        verify { asyncCommands.del("session:account:acc-1") }
    }

    @Test
    fun `deleteSession preserves reverse lookup when pointing to different session`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hget("session:old-sess", "accountId") } returns redisFuture("acc-1")
        every { asyncCommands.get("session:account:acc-1") } returns redisFuture("new-sess")

        sessionManager.deleteSession("old-sess")

        verify { asyncCommands.del("session:old-sess") }
        verify(exactly = 0) { asyncCommands.del("session:account:acc-1") }
    }

    // --- updateCharacterId ---

    @Test
    fun `updateCharacterId sets field in Redis`() = runTest {
        setupBasicRedis()
        every { asyncCommands.hset("session:sess-1", "characterId", "c-42") } returns redisFuture(true)

        sessionManager.updateCharacterId("sess-1", "c-42")

        verify { asyncCommands.hset("session:sess-1", "characterId", "c-42") }
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :login-service:test --tests "com.flyagain.login.session.SessionManagerTest" --info`

**Step 3: Commit**

```
git add server/login-service/src/test/kotlin/com/flyagain/login/session/SessionManagerTest.kt
git commit -m "test: add SessionManagerTest for CRUD, multi-login, and stale cleanup"
```

---

### Task 12: CharacterSelectHandlerTest (account-service)

**Files:**
- Create: `server/account-service/src/test/kotlin/com/flyagain/account/handler/CharacterSelectHandlerTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.account.handler

import com.flyagain.common.grpc.CharacterDataServiceGrpcKt
import com.flyagain.common.grpc.CharacterRecord
import com.flyagain.common.network.Packet
import com.flyagain.common.proto.CharacterSelectRequest
import com.flyagain.common.proto.EnterWorldResponse
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

    private fun makePacket(characterId: String): Packet {
        val request = CharacterSelectRequest.newBuilder()
            .setCharacterId(characterId)
            .build()
        return Packet(0x0003, request.toByteArray())
    }

    private fun captureResponse(): EnterWorldResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return EnterWorldResponse.parseFrom(slot.captured.payload)
    }

    private fun makeCharacterRecord(charId: String, accountId: String): CharacterRecord {
        return CharacterRecord.newBuilder()
            .setFound(true)
            .setId(charId)
            .setAccountId(accountId)
            .setName("Hero")
            .setCharacterClass(0)
            .setLevel(5)
            .setHp(500)
            .setMaxHp(500)
            .setMp(100)
            .setMaxMp(100)
            .setStr(20)
            .setSta(15)
            .setDex(10)
            .setIntStat(10)
            .setStatPoints(3)
            .setXp(1000)
            .setMapId(1)
            .setPosX(100f)
            .setPosY(0f)
            .setPosZ(200f)
            .setRotation(1.5f)
            .setGold(500)
            .build()
    }

    // --- Validation ---

    @Test
    fun `rejects blank character ID`() = runTest {
        handler.handle(ctx, makePacket(""), "acc-1")

        assertFalse(captureResponse().success)
    }

    @Test
    fun `rejects invalid payload`() = runTest {
        val badPacket = Packet(0x0003, byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
        handler.handle(ctx, badPacket, "acc-1")

        assertFalse(captureResponse().success)
    }

    // --- gRPC failures ---

    @Test
    fun `returns error on gRPC failure`() = runTest {
        coEvery { characterStub.getCharacter(any()) } throws RuntimeException("connection refused")

        handler.handle(ctx, makePacket("c-1"), "acc-1")

        assertFalse(captureResponse().success)
    }

    @Test
    fun `returns error when character not found`() = runTest {
        coEvery { characterStub.getCharacter(any()) } returns
            CharacterRecord.newBuilder().setFound(false).build()

        handler.handle(ctx, makePacket("c-missing"), "acc-1")

        assertFalse(captureResponse().success)
    }

    @Test
    fun `returns error when character belongs to different account`() = runTest {
        coEvery { characterStub.getCharacter(any()) } returns makeCharacterRecord("c-1", "acc-other")

        handler.handle(ctx, makePacket("c-1"), "acc-1")

        assertFalse(captureResponse().success)
    }

    // --- Redis cache failure ---

    @Test
    fun `returns error when Redis cache fails`() = runTest {
        coEvery { characterStub.getCharacter(any()) } returns makeCharacterRecord("c-1", "acc-1")
        every { redisSync.hset(any(), any<Map<String, String>>()) } throws RuntimeException("Redis down")

        handler.handle(ctx, makePacket("c-1"), "acc-1")

        assertFalse(captureResponse().success)
    }

    // --- Success ---

    @Test
    fun `returns success with world service info on valid select`() = runTest {
        coEvery { characterStub.getCharacter(any()) } returns makeCharacterRecord("c-1", "acc-1")

        handler.handle(ctx, makePacket("c-1"), "acc-1")

        val response = captureResponse()
        assertTrue(response.success)
        assertEquals("127.0.0.1", response.worldServiceHost)
        assertEquals(7780, response.worldServiceTcpPort)
        assertEquals(7781, response.worldServiceUdpPort)
    }

    @Test
    fun `caches character data in Redis with correct key`() = runTest {
        coEvery { characterStub.getCharacter(any()) } returns makeCharacterRecord("c-1", "acc-1")

        handler.handle(ctx, makePacket("c-1"), "acc-1")

        verify { redisSync.hset("character:c-1", any<Map<String, String>>()) }
        verify { redisSync.expire("character:c-1", 300L) }
    }

    @Test
    fun `response contains character stats`() = runTest {
        coEvery { characterStub.getCharacter(any()) } returns makeCharacterRecord("c-1", "acc-1")

        handler.handle(ctx, makePacket("c-1"), "acc-1")

        val response = captureResponse()
        assertEquals(5, response.stats.level)
        assertEquals(500, response.stats.hp)
        assertEquals(100f, response.position.x)
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :account-service:test --tests "com.flyagain.account.handler.CharacterSelectHandlerTest" --info`

**Step 3: Commit**

```
git add server/account-service/src/test/kotlin/com/flyagain/account/handler/CharacterSelectHandlerTest.kt
git commit -m "test: add CharacterSelectHandlerTest for validation, gRPC, and caching"
```

---

### Task 13: SelectTargetHandlerTest (world-service)

**Files:**
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/handler/SelectTargetHandlerTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.SelectTargetRequest
import com.flyagain.common.proto.SelectTargetResponse
import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import io.mockk.*
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectTargetHandlerTest {

    private val entityManager = mockk<EntityManager>()
    private val zoneManager = mockk<ZoneManager>()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)
    private val handler = SelectTargetHandler(entityManager, zoneManager)

    init {
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
    }

    private fun makePlayer(entityId: Long = 1L, zoneId: Int = 1, channelId: Int = 0): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = "c-$entityId",
            accountId = "acc-$entityId",
            name = "Player$entityId",
            characterClass = 0,
            x = 100f, y = 0f, z = 100f,
            zoneId = zoneId,
            channelId = channelId
        )
    }

    private fun makeMonster(
        entityId: Long = 1_000_001L,
        hp: Int = 100,
        aiState: AIState = AIState.IDLE
    ): MonsterEntity {
        return MonsterEntity(
            entityId = entityId,
            definitionId = 1,
            name = "Slime",
            x = 110f, y = 0f, z = 110f,
            spawnX = 110f, spawnY = 0f, spawnZ = 110f,
            hp = hp, maxHp = 100,
            attack = 10, defense = 5,
            level = 3, xpReward = 50,
            aggroRange = 10f, attackRange = 2f,
            attackSpeedMs = 2000, moveSpeed = 3f,
            aiState = aiState
        )
    }

    private fun captureResponse(): SelectTargetResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return SelectTargetResponse.parseFrom(slot.captured.payload)
    }

    @Test
    fun `clear target with id 0 clears target and auto-attack`() {
        val player = makePlayer()
        player.targetEntityId = 5L
        player.autoAttacking = true

        val request = SelectTargetRequest.newBuilder().setTargetEntityId(0).build()
        handler.handle(ctx, player, request)

        assertNull(player.targetEntityId)
        assertFalse(player.autoAttacking)
        assertTrue(captureResponse().success)
    }

    @Test
    fun `selecting alive monster sets target and returns stats`() {
        val player = makePlayer()
        val monster = makeMonster(hp = 80)
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(1_000_001L) } returns monster

        val request = SelectTargetRequest.newBuilder().setTargetEntityId(1_000_001L).build()
        handler.handle(ctx, player, request)

        assertEquals(1_000_001L, player.targetEntityId)
        val response = captureResponse()
        assertTrue(response.success)
        assertEquals(80, response.targetHp)
        assertEquals(100, response.targetMaxHp)
        assertEquals("Slime", response.targetName)
        assertEquals(3, response.targetLevel)
    }

    @Test
    fun `selecting dead monster is rejected`() {
        val player = makePlayer()
        val monster = makeMonster(hp = 0, aiState = AIState.DEAD)
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(1_000_001L) } returns monster

        val request = SelectTargetRequest.newBuilder().setTargetEntityId(1_000_001L).build()
        handler.handle(ctx, player, request)

        assertFalse(captureResponse().success)
    }

    @Test
    fun `selecting another player sets target and returns stats`() {
        val player = makePlayer(entityId = 1L)
        val targetPlayer = PlayerEntity(
            entityId = 2L, characterId = "c-2", accountId = "acc-2",
            name = "OtherPlayer", characterClass = 1,
            x = 120f, y = 0f, z = 120f,
            level = 10, hp = 200, maxHp = 200
        )
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(2L) } returns null
        every { channel.getPlayer(2L) } returns targetPlayer

        val request = SelectTargetRequest.newBuilder().setTargetEntityId(2L).build()
        handler.handle(ctx, player, request)

        assertEquals(2L, player.targetEntityId)
        val response = captureResponse()
        assertTrue(response.success)
        assertEquals("OtherPlayer", response.targetName)
    }

    @Test
    fun `returns error when target not found`() {
        val player = makePlayer()
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(999L) } returns null
        every { channel.getPlayer(999L) } returns null

        val request = SelectTargetRequest.newBuilder().setTargetEntityId(999L).build()
        handler.handle(ctx, player, request)

        assertFalse(captureResponse().success)
    }

    @Test
    fun `returns error when not in valid zone channel`() {
        val player = makePlayer()
        every { zoneManager.getChannel(1, 0) } returns null

        val request = SelectTargetRequest.newBuilder().setTargetEntityId(1_000_001L).build()
        handler.handle(ctx, player, request)

        assertFalse(captureResponse().success)
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.handler.SelectTargetHandlerTest" --info`

**Step 3: Commit**

```
git add server/world-service/src/test/kotlin/com/flyagain/world/handler/SelectTargetHandlerTest.kt
git commit -m "test: add SelectTargetHandlerTest for target selection and validation"
```

---

### Task 14: UseSkillHandlerTest (world-service)

**Files:**
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/handler/UseSkillHandlerTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.UseSkillRequest
import com.flyagain.common.proto.UseSkillResponse
import com.flyagain.world.combat.CombatEngine
import com.flyagain.world.combat.SkillSystem
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import io.mockk.*
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UseSkillHandlerTest {

    private val skillSystem = mockk<SkillSystem>()
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val zoneManager = mockk<ZoneManager>()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private val handler = UseSkillHandler(skillSystem, broadcastService, zoneManager)

    init {
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
    }

    private fun makePlayer(
        entityId: Long = 1L,
        hp: Int = 100,
        zoneId: Int = 1,
        channelId: Int = 0
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = "c-1",
            accountId = "acc-1",
            name = "Hero",
            characterClass = 0,
            x = 100f, y = 0f, z = 100f,
            hp = hp,
            zoneId = zoneId,
            channelId = channelId
        )
    }

    private fun captureResponse(): UseSkillResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return UseSkillResponse.parseFrom(slot.captured.payload)
    }

    @Test
    fun `rejects skill use when player is dead`() {
        val player = makePlayer(hp = 0)
        val request = UseSkillRequest.newBuilder().setSkillId(1).setTargetEntityId(100L).build()

        handler.handle(ctx, player, request)

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("dead"))
        verify(exactly = 0) { skillSystem.useSkill(any(), any(), any()) }
    }

    @Test
    fun `forwards skill system error to client`() {
        val player = makePlayer()
        every { skillSystem.useSkill(player, 1, 100L) } returns
            SkillSystem.SkillResult.Error("Not enough MP")

        val request = UseSkillRequest.newBuilder().setSkillId(1).setTargetEntityId(100L).build()
        handler.handle(ctx, player, request)

        val response = captureResponse()
        assertFalse(response.success)
        assertTrue(response.errorMessage.contains("MP"))
    }

    @Test
    fun `sends success and broadcasts damage on skill success`() {
        val player = makePlayer()
        val damageResult = mockk<CombatEngine.DamageResult>()
        every { skillSystem.useSkill(player, 1, 100L) } returns
            SkillSystem.SkillResult.Success(damageResult)
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel

        val request = UseSkillRequest.newBuilder().setSkillId(1).setTargetEntityId(100L).build()
        handler.handle(ctx, player, request)

        val response = captureResponse()
        assertTrue(response.success)
        verify(exactly = 1) { broadcastService.broadcastDamageEvent(channel, damageResult) }
    }

    @Test
    fun `skips broadcast when channel is null but still sends success`() {
        val player = makePlayer()
        val damageResult = mockk<CombatEngine.DamageResult>()
        every { skillSystem.useSkill(player, 1, 100L) } returns
            SkillSystem.SkillResult.Success(damageResult)
        every { zoneManager.getChannel(1, 0) } returns null

        val request = UseSkillRequest.newBuilder().setSkillId(1).setTargetEntityId(100L).build()
        handler.handle(ctx, player, request)

        assertTrue(captureResponse().success)
        verify(exactly = 0) { broadcastService.broadcastDamageEvent(any(), any()) }
    }

    @Test
    fun `response contains correct skillId`() {
        val player = makePlayer()
        every { skillSystem.useSkill(player, 42, 100L) } returns
            SkillSystem.SkillResult.Error("Invalid skill")

        val request = UseSkillRequest.newBuilder().setSkillId(42).setTargetEntityId(100L).build()
        handler.handle(ctx, player, request)

        val response = captureResponse()
        kotlin.test.assertEquals(42, response.skillId)
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.handler.UseSkillHandlerTest" --info`

**Step 3: Commit**

```
git add server/world-service/src/test/kotlin/com/flyagain/world/handler/UseSkillHandlerTest.kt
git commit -m "test: add UseSkillHandlerTest for dead player, skill errors, and broadcast"
```

---

### Task 15: ToggleAutoAttackHandlerTest (world-service)

**Files:**
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/handler/ToggleAutoAttackHandlerTest.kt`

**Step 1: Write the test file**

```kotlin
package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ToggleAutoAttackRequest
import com.flyagain.common.proto.ToggleAutoAttackResponse
import com.flyagain.world.ai.AIState
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.MonsterEntity
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import io.mockk.*
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToggleAutoAttackHandlerTest {

    private val entityManager = mockk<EntityManager>()
    private val zoneManager = mockk<ZoneManager>()
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)
    private val handler = ToggleAutoAttackHandler(entityManager, zoneManager)

    init {
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
    }

    private fun makePlayer(
        entityId: Long = 1L,
        zoneId: Int = 1,
        channelId: Int = 0
    ): PlayerEntity {
        return PlayerEntity(
            entityId = entityId,
            characterId = "c-1",
            accountId = "acc-1",
            name = "Hero",
            characterClass = 0,
            x = 100f, y = 0f, z = 100f,
            zoneId = zoneId,
            channelId = channelId
        )
    }

    private fun makeMonster(
        entityId: Long = 1_000_001L,
        hp: Int = 100,
        aiState: AIState = AIState.IDLE
    ): MonsterEntity {
        return MonsterEntity(
            entityId = entityId, definitionId = 1, name = "Slime",
            x = 110f, y = 0f, z = 110f,
            spawnX = 110f, spawnY = 0f, spawnZ = 110f,
            hp = hp, maxHp = 100, attack = 10, defense = 5,
            level = 3, xpReward = 50,
            aggroRange = 10f, attackRange = 2f,
            attackSpeedMs = 2000, moveSpeed = 3f,
            aiState = aiState
        )
    }

    private fun captureResponse(): ToggleAutoAttackResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return ToggleAutoAttackResponse.parseFrom(slot.captured.payload)
    }

    @Test
    fun `disable auto-attack always succeeds`() {
        val player = makePlayer()
        player.autoAttacking = true

        val request = ToggleAutoAttackRequest.newBuilder().setEnable(false).build()
        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        assertFalse(captureResponse().autoAttacking)
    }

    @Test
    fun `enable with no target fails`() {
        val player = makePlayer()
        player.targetEntityId = null

        val request = ToggleAutoAttackRequest.newBuilder().setEnable(true).setTargetEntityId(0L).build()
        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        assertFalse(captureResponse().autoAttacking)
    }

    @Test
    fun `enable sets targetEntityId from request before validation`() {
        val player = makePlayer()
        player.targetEntityId = null
        val monster = makeMonster()
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(1_000_001L) } returns monster

        val request = ToggleAutoAttackRequest.newBuilder()
            .setEnable(true).setTargetEntityId(1_000_001L).build()
        handler.handle(ctx, player, request)

        assertTrue(player.autoAttacking)
        assertTrue(captureResponse().autoAttacking)
    }

    @Test
    fun `enable with alive monster succeeds`() {
        val player = makePlayer()
        player.targetEntityId = 1_000_001L
        val monster = makeMonster(hp = 50)
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(1_000_001L) } returns monster

        val request = ToggleAutoAttackRequest.newBuilder().setEnable(true).build()
        handler.handle(ctx, player, request)

        assertTrue(player.autoAttacking)
        assertTrue(captureResponse().autoAttacking)
    }

    @Test
    fun `enable with dead monster disables and clears target`() {
        val player = makePlayer()
        player.targetEntityId = 1_000_001L
        val monster = makeMonster(hp = 0, aiState = AIState.DEAD)
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(1_000_001L) } returns monster

        val request = ToggleAutoAttackRequest.newBuilder().setEnable(true).build()
        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        assertNull(player.targetEntityId)
        assertFalse(captureResponse().autoAttacking)
    }

    @Test
    fun `enable with invalid channel disables auto-attack`() {
        val player = makePlayer()
        player.targetEntityId = 1_000_001L
        every { zoneManager.getChannel(1, 0) } returns null

        val request = ToggleAutoAttackRequest.newBuilder().setEnable(true).build()
        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
    }

    @Test
    fun `enable with non-existent target disables and clears target`() {
        val player = makePlayer()
        player.targetEntityId = 999L
        val channel = mockk<ZoneChannel>()
        every { zoneManager.getChannel(1, 0) } returns channel
        every { channel.getMonster(999L) } returns null

        val request = ToggleAutoAttackRequest.newBuilder().setEnable(true).build()
        handler.handle(ctx, player, request)

        assertFalse(player.autoAttacking)
        assertNull(player.targetEntityId)
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.handler.ToggleAutoAttackHandlerTest" --info`

**Step 3: Commit**

```
git add server/world-service/src/test/kotlin/com/flyagain/world/handler/ToggleAutoAttackHandlerTest.kt
git commit -m "test: add ToggleAutoAttackHandlerTest for enable/disable and target validation"
```

---

### Task 16: EnterWorldHandlerTest (world-service)

**Files:**
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/handler/EnterWorldHandlerTest.kt`

**Note:** This is the most complex handler. It requires mocking JWT validation (java-jwt library), Redis sync commands, EntityManager, ZoneManager, and RedisSessionSecretProvider.

**Step 1: Write the test file**

```kotlin
package com.flyagain.world.handler

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.EnterWorldRequest
import com.flyagain.common.proto.EnterWorldResponse
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.RedisSessionSecretProvider
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.*
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnterWorldHandlerTest {

    private val entityManager = mockk<EntityManager>()
    private val zoneManager = mockk<ZoneManager>()
    private val redisSync = mockk<RedisCommands<String, String>>()
    private val redisAsync = mockk<RedisAsyncCommands<String, String>>(relaxed = true)
    private val redisConnection = mockk<StatefulRedisConnection<String, String>> {
        every { sync() } returns redisSync
        every { async() } returns redisAsync
    }
    private val sessionSecretProvider = mockk<RedisSessionSecretProvider>(relaxed = true)
    private val ctx = mockk<ChannelHandlerContext>(relaxed = true)

    private val jwtSecret = "test-jwt-secret-key-for-unit-tests"
    private val handler = EnterWorldHandler(entityManager, zoneManager, redisConnection, jwtSecret, sessionSecretProvider)

    init {
        every { ctx.writeAndFlush(any()) } returns mockk<ChannelFuture>()
        val nettyChannel = mockk<Channel>(relaxed = true)
        every { ctx.channel() } returns nettyChannel
    }

    private fun makeValidJwt(accountId: String): String {
        return JWT.create()
            .withSubject(accountId)
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    private fun makeRequest(
        jwt: String = makeValidJwt("acc-1"),
        characterId: String = "c-1",
        sessionId: String = "sess-1"
    ): EnterWorldRequest {
        return EnterWorldRequest.newBuilder()
            .setJwt(jwt)
            .setCharacterId(characterId)
            .setSessionId(sessionId)
            .build()
    }

    private fun setupRedisCharacter(characterId: String, accountId: String) {
        every { redisSync.hgetall("character:$characterId") } returns mapOf(
            "account_id" to accountId,
            "name" to "Hero",
            "class" to "0",
            "level" to "5",
            "hp" to "500",
            "max_hp" to "500",
            "mp" to "100",
            "max_mp" to "100",
            "str" to "20",
            "sta" to "15",
            "dex" to "10",
            "int" to "10",
            "stat_points" to "3",
            "xp" to "1000",
            "map_id" to "1",
            "pos_x" to "100.0",
            "pos_y" to "0.0",
            "pos_z" to "200.0",
            "rotation" to "1.5",
            "gold" to "500"
        )
    }

    private fun setupRedisSession(sessionId: String) {
        every { redisSync.hgetall("session:$sessionId") } returns mapOf(
            "accountId" to "acc-1",
            "sessionToken" to "12345",
            "hmacSecret" to "secret-key"
        )
    }

    private fun setupFullSuccess() {
        setupRedisCharacter("c-1", "acc-1")
        setupRedisSession("sess-1")
        every { entityManager.nextPlayerId() } returns 1L
        every { entityManager.tryAddPlayer(any()) } returns true
        every { entityManager.getPlayer(any()) } returns null
        every { entityManager.getMonster(any()) } returns null
        every { zoneManager.zoneExists(1) } returns true
        every { zoneManager.getZoneName(any()) } returns "Aerheim"
        val channel = mockk<ZoneChannel>(relaxed = true)
        every { channel.channelId } returns 0
        every { channel.getNearbyEntities(any(), any()) } returns emptySet()
        every { zoneManager.addPlayerToZone(any(), any()) } returns channel
        // Mock Redis async for online status
        val saddFuture = mockk<RedisFuture<Long>>()
        every { saddFuture.toCompletableFuture() } returns CompletableFuture.completedFuture(1L)
        every { redisAsync.sadd(any(), any()) } returns saddFuture
    }

    private fun captureResponse(): EnterWorldResponse {
        val slot = slot<Packet>()
        verify { ctx.writeAndFlush(capture(slot)) }
        return EnterWorldResponse.parseFrom(slot.captured.payload)
    }

    // --- JWT validation ---

    @Test
    fun `rejects invalid JWT`() = runTest {
        val result = handler.handle(ctx, makeRequest(jwt = "invalid-jwt"))

        assertNull(result)
    }

    @Test
    fun `rejects JWT signed with wrong secret`() = runTest {
        val badJwt = JWT.create().withSubject("acc-1").sign(Algorithm.HMAC256("wrong-secret"))
        val result = handler.handle(ctx, makeRequest(jwt = badJwt))

        assertNull(result)
    }

    // --- Character cache ---

    @Test
    fun `rejects when character not in Redis cache`() = runTest {
        every { redisSync.hgetall("character:c-1") } returns emptyMap()

        val result = handler.handle(ctx, makeRequest())

        assertNull(result)
    }

    @Test
    fun `rejects when character belongs to different account`() = runTest {
        setupRedisCharacter("c-1", "acc-other")

        val result = handler.handle(ctx, makeRequest())

        assertNull(result)
    }

    // --- Session validation ---

    @Test
    fun `rejects when session not in Redis`() = runTest {
        setupRedisCharacter("c-1", "acc-1")
        every { redisSync.hgetall("session:sess-1") } returns emptyMap()

        val result = handler.handle(ctx, makeRequest())

        assertNull(result)
    }

    @Test
    fun `rejects when session missing token or secret`() = runTest {
        setupRedisCharacter("c-1", "acc-1")
        every { redisSync.hgetall("session:sess-1") } returns mapOf(
            "accountId" to "acc-1",
            "sessionToken" to "0",
            "hmacSecret" to ""
        )

        val result = handler.handle(ctx, makeRequest())

        assertNull(result)
    }

    // --- Duplicate login ---

    @Test
    fun `rejects duplicate login for same account`() = runTest {
        setupRedisCharacter("c-1", "acc-1")
        setupRedisSession("sess-1")
        every { entityManager.nextPlayerId() } returns 1L
        every { entityManager.tryAddPlayer(any()) } returns false

        val result = handler.handle(ctx, makeRequest())

        assertNull(result)
    }

    // --- Zone failure ---

    @Test
    fun `rejects when zone is full and cleans up entity`() = runTest {
        setupRedisCharacter("c-1", "acc-1")
        setupRedisSession("sess-1")
        every { entityManager.nextPlayerId() } returns 1L
        every { entityManager.tryAddPlayer(any()) } returns true
        every { zoneManager.zoneExists(1) } returns true
        every { zoneManager.addPlayerToZone(any(), any()) } returns null
        every { entityManager.removePlayer(1L) } returns null

        val result = handler.handle(ctx, makeRequest())

        assertNull(result)
        verify { entityManager.removePlayer(1L) }
    }

    // --- Zone fallback ---

    @Test
    fun `falls back to Aerheim when zone does not exist`() = runTest {
        setupFullSuccess()
        // Character has map_id=99 which doesn't exist
        every { redisSync.hgetall("character:c-1") } returns mapOf(
            "account_id" to "acc-1", "name" to "Hero", "class" to "0",
            "level" to "1", "hp" to "100", "max_hp" to "100",
            "mp" to "50", "max_mp" to "50", "map_id" to "99",
            "pos_x" to "0", "pos_y" to "0", "pos_z" to "0"
        )
        setupRedisSession("sess-1")
        every { zoneManager.zoneExists(99) } returns false

        val result = handler.handle(ctx, makeRequest())

        assertNotNull(result)
        verify { zoneManager.addPlayerToZone(any(), ZoneManager.ZONE_AERHEIM) }
    }

    // --- Success ---

    @Test
    fun `returns player entity on successful entry`() = runTest {
        setupFullSuccess()

        val result = handler.handle(ctx, makeRequest())

        assertNotNull(result)
        verify { sessionSecretProvider.registerSecret(12345L, any()) }
    }
}
```

**Step 2: Run test**

Run: `cd server && ./gradlew :world-service:test --tests "com.flyagain.world.handler.EnterWorldHandlerTest" --info`

**Step 3: Commit**

```
git add server/world-service/src/test/kotlin/com/flyagain/world/handler/EnterWorldHandlerTest.kt
git commit -m "test: add EnterWorldHandlerTest for JWT, cache, session, and zone validation"
```

---

### Task 17: Run all tests and verify

**Step 1: Build and test all modules**

Run: `cd server && ./gradlew test --info`

Expected: All tests pass (existing + 15 new test files)

**Step 2: Fix any compilation or test failures**

If any tests fail, investigate and fix. Common issues:
- Protobuf field names may differ slightly (check generated code)
- Import paths may need adjustment
- Mock setup may need additional stubs

**Step 3: Final commit if any fixes were needed**

```
git add -A
git commit -m "fix: resolve test compilation issues"
```
