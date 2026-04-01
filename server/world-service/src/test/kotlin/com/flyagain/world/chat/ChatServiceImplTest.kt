package com.flyagain.world.chat

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ChatBroadcastMessage
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.EntityManager
import com.flyagain.world.entity.PlayerEntity
import com.flyagain.world.network.BroadcastService
import com.flyagain.world.zone.ZoneChannel
import com.flyagain.world.zone.ZoneManager
import io.mockk.*
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatServiceImplTest {
    private val zoneManager = mockk<ZoneManager>()
    private val entityManager = mockk<EntityManager>()
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val service = ChatServiceImpl(zoneManager, entityManager, broadcastService)

    private fun makePlayer(entityId: Long = 1L, name: String = "Sender", zoneId: Int = 1, channelId: Int = 0): PlayerEntity {
        val player = PlayerEntity(entityId = entityId, characterId = "c1", accountId = "a1", name = name, characterClass = 1, x = 100f, y = 0f, z = 100f)
        player.zoneId = zoneId
        player.channelId = channelId
        return player
    }

    private fun mockCtx(): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        every { ctx.channel() } returns mockk<Channel>(relaxed = true)
        return ctx
    }

    @Test fun `handleSay broadcasts to nearby players`() {
        val player = makePlayer()
        val channel = mockk<ZoneChannel>(relaxed = true)
        every { zoneManager.getChannel(1, 0) } returns channel
        service.handleSay(mockCtx(), player, "Hello nearby")
        verify(exactly = 1) { broadcastService.broadcastChatToNearby(channel, 100f, 100f, any()) }
    }

    @Test fun `handleSay does nothing when zone channel not found`() {
        every { zoneManager.getChannel(1, 0) } returns null
        service.handleSay(mockCtx(), makePlayer(), "Hello")
        verify(exactly = 0) { broadcastService.broadcastChatToNearby(any(), any(), any(), any()) }
    }

    @Test fun `handleShout broadcasts to all players in channel`() {
        val channel = mockk<ZoneChannel>(relaxed = true)
        every { zoneManager.getChannel(1, 0) } returns channel
        service.handleShout(mockCtx(), makePlayer(), "Hello zone!")
        verify(exactly = 1) { broadcastService.broadcastChatToChannel(channel, any()) }
    }

    @Test fun `handleWhisper sends to target and echo to sender`() {
        val sender = makePlayer(entityId = 1L, name = "Sender")
        val target = makePlayer(entityId = 2L, name = "Target")
        val ctx = mockCtx()
        every { entityManager.getPlayerByName("Target") } returns target
        service.handleWhisper(ctx, sender, "Target", "Secret message")
        verify(exactly = 1) { broadcastService.sendToPlayer(target, any()) }
        verify(exactly = 1) { ctx.writeAndFlush(any()) }
    }

    @Test fun `handleWhisper sends error when target not found`() {
        val ctx = mockCtx()
        every { entityManager.getPlayerByName("Nobody") } returns null
        service.handleWhisper(ctx, makePlayer(), "Nobody", "Hello?")
        verify(exactly = 1) { ctx.writeAndFlush(match<Packet> { it.opcode == Opcode.ERROR_RESPONSE_VALUE && ErrorResponse.parseFrom(it.payload).errorCode == 404 }) }
    }

    @Test fun `handleWhisper sends error when whispering to self`() {
        val sender = makePlayer(entityId = 1L, name = "Sender")
        val ctx = mockCtx()
        every { entityManager.getPlayerByName("Sender") } returns sender
        service.handleWhisper(ctx, sender, "Sender", "Talking to myself")
        verify(exactly = 1) { ctx.writeAndFlush(match<Packet> { it.opcode == Opcode.ERROR_RESPONSE_VALUE && ErrorResponse.parseFrom(it.payload).errorCode == 400 }) }
    }

    @Test fun `handleSay broadcast contains correct channel_type 0`() {
        val channel = mockk<ZoneChannel>(relaxed = true)
        every { zoneManager.getChannel(1, 0) } returns channel
        val packetSlot = slot<Packet>()
        every { broadcastService.broadcastChatToNearby(any(), any(), any(), capture(packetSlot)) } just Runs
        service.handleSay(mockCtx(), makePlayer(), "Test")
        val msg = ChatBroadcastMessage.parseFrom(packetSlot.captured.payload)
        assertEquals(0, msg.channelType)
        assertEquals("Sender", msg.senderName)
        assertEquals("Test", msg.text)
    }

    @Test fun `handleShout broadcast contains correct channel_type 1`() {
        val channel = mockk<ZoneChannel>(relaxed = true)
        every { zoneManager.getChannel(1, 0) } returns channel
        val packetSlot = slot<Packet>()
        every { broadcastService.broadcastChatToChannel(any(), capture(packetSlot)) } just Runs
        service.handleShout(mockCtx(), makePlayer(), "Shout test")
        val msg = ChatBroadcastMessage.parseFrom(packetSlot.captured.payload)
        assertEquals(1, msg.channelType)
        assertEquals("Shout test", msg.text)
    }
}
