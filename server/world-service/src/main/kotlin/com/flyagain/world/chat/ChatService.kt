package com.flyagain.world.chat

import com.flyagain.world.entity.PlayerEntity
import io.netty.channel.ChannelHandlerContext

interface ChatService {
    fun handleSay(ctx: ChannelHandlerContext, player: PlayerEntity, text: String)
    fun handleShout(ctx: ChannelHandlerContext, player: PlayerEntity, text: String)
    fun handleWhisper(ctx: ChannelHandlerContext, player: PlayerEntity, targetName: String, text: String)
}
