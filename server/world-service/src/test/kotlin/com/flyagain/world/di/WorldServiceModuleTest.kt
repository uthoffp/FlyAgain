package com.flyagain.world.di

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

@OptIn(KoinExperimentalAPI::class)
class WorldServiceModuleTest {

    @Test
    fun `world service module verifies successfully`() {
        worldServiceModule.verify(
            extraTypes = listOf(
                com.typesafe.config.Config::class,
                Float::class,
                Int::class,
                Long::class,
                String::class,
                Boolean::class,
                io.grpc.Channel::class,
                io.grpc.CallOptions::class,
                io.grpc.ManagedChannel::class,
                io.netty.channel.ChannelHandler::class,
                io.lettuce.core.RedisClient::class,
                io.lettuce.core.api.StatefulRedisConnection::class,
                kotlinx.coroutines.CoroutineScope::class,
                com.flyagain.common.network.UdpPacketHandler::class,
                com.flyagain.common.network.SessionSecretProvider::class,
            )
        )
    }
}
