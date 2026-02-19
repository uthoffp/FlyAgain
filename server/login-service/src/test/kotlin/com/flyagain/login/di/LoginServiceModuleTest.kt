package com.flyagain.login.di

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

@OptIn(KoinExperimentalAPI::class)
class LoginServiceModuleTest {

    @Test
    fun `login service module verifies successfully`() {
        loginServiceModule.verify(
            extraTypes = listOf(
                io.grpc.ManagedChannel::class,
                io.grpc.Channel::class,
                io.grpc.CallOptions::class,
                io.lettuce.core.RedisClient::class,
                io.lettuce.core.api.StatefulRedisConnection::class,
                kotlinx.coroutines.CoroutineScope::class,
                com.flyagain.common.network.TcpServer::class,
                com.typesafe.config.Config::class,
                io.netty.channel.ChannelHandler::class,
                String::class,
                Int::class,
                Long::class,
                Float::class,
            )
        )
    }
}
