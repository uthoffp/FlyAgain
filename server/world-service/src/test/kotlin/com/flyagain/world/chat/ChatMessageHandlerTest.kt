package com.flyagain.world.chat

import com.flyagain.common.network.Packet
import com.flyagain.common.proto.ChatMessageRequest
import com.flyagain.common.proto.ErrorResponse
import com.flyagain.common.proto.Opcode
import com.flyagain.world.entity.PlayerEntity
import io.mockk.*
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlin.test.Test

class ChatMessageHandlerTest {
    private val chatService = mockk<ChatService>(relaxed = true)
    private val sanitizer = ChatMessageSanitizer()
    private val handler = ChatMessageHandler(chatService, sanitizer)

    private fun makePlayer(name: String = "TestPlayer"): PlayerEntity =
        PlayerEntity(entityId = 1L, characterId = "c1", accountId = "a1", name = name, characterClass = 1, x = 0f, y = 0f, z = 0f)

    private fun mockCtx(): ChannelHandlerContext {
        val ctx = mockk<ChannelHandlerContext>(relaxed = true)
        every { ctx.channel() } returns mockk<Channel>(relaxed = true)
        return ctx
    }

    private fun buildRequest(text: String, channelType: Int = 0, targetName: String = ""): Packet {
        val req = ChatMessageRequest.newBuilder().setChannelType(channelType).setText(text).setTargetName(targetName).build()
        return Packet(Opcode.CHAT_MESSAGE_VALUE, req.toByteArray())
    }

    @Test fun `routes say message to chatService handleSay`() {
        val ctx = mockCtx(); val player = makePlayer()
        handler.handle(ctx, player, buildRequest("Hello", channelType = 0))
        verify(exactly = 1) { chatService.handleSay(ctx, player, "Hello") }
    }

    @Test fun `routes shout message to chatService handleShout`() {
        val ctx = mockCtx(); val player = makePlayer()
        handler.handle(ctx, player, buildRequest("Hello zone!", channelType = 1))
        verify(exactly = 1) { chatService.handleShout(ctx, player, "Hello zone!") }
    }

    @Test fun `routes whisper message to chatService handleWhisper`() {
        val ctx = mockCtx(); val player = makePlayer()
        handler.handle(ctx, player, buildRequest("Secret", channelType = 2, targetName = "Target"))
        verify(exactly = 1) { chatService.handleWhisper(ctx, player, "Target", "Secret") }
    }

    @Test fun `rejects empty message after sanitization`() {
        val ctx = mockCtx(); val player = makePlayer()
        handler.handle(ctx, player, buildRequest("   ", channelType = 0))
        verify(exactly = 0) { chatService.handleSay(any(), any(), any()) }
        verify(exactly = 1) { ctx.writeAndFlush(match<Packet> { it.opcode == Opcode.ERROR_RESPONSE_VALUE }) }
    }

    @Test fun `rejects message exceeding 200 characters`() {
        val ctx = mockCtx(); val player = makePlayer()
        handler.handle(ctx, player, buildRequest("a".repeat(201), channelType = 0))
        verify(exactly = 0) { chatService.handleSay(any(), any(), any()) }
    }

    @Test fun `rate limits after 10 messages in 10 seconds`() {
        val ctx = mockCtx(); val player = makePlayer()
        repeat(10) { handler.handle(ctx, player, buildRequest("msg$it", channelType = 0)) }
        verify(exactly = 10) { chatService.handleSay(any(), any(), any()) }
        handler.handle(ctx, player, buildRequest("spam", channelType = 0))
        verify(exactly = 10) { chatService.handleSay(any(), any(), any()) }
        verify(atLeast = 1) { ctx.writeAndFlush(match<Packet> { it.opcode == Opcode.ERROR_RESPONSE_VALUE && ErrorResponse.parseFrom(it.payload).errorCode == 429 }) }
    }

    @Test fun `rejects invalid channel type`() {
        val ctx = mockCtx(); val player = makePlayer()
        handler.handle(ctx, player, buildRequest("test", channelType = 5))
        verify(exactly = 0) { chatService.handleSay(any(), any(), any()) }
        verify(exactly = 0) { chatService.handleShout(any(), any(), any()) }
        verify(exactly = 0) { chatService.handleWhisper(any(), any(), any(), any()) }
    }

    @Test fun `rejects whisper with empty target name`() {
        val ctx = mockCtx(); val player = makePlayer()
        handler.handle(ctx, player, buildRequest("Hello", channelType = 2, targetName = ""))
        verify(exactly = 0) { chatService.handleWhisper(any(), any(), any(), any()) }
    }
}
