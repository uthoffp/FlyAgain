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
            )
        )
    }
}
