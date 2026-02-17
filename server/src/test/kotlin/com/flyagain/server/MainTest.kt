package com.flyagain.server

import com.typesafe.config.ConfigFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {

    @Test
    fun `config loads with correct default tcp port`() {
        val config = ConfigFactory.load()
        assertEquals(7777, config.getInt("flyagain.network.tcp-port"))
    }

    @Test
    fun `config loads with correct default udp port`() {
        val config = ConfigFactory.load()
        assertEquals(7778, config.getInt("flyagain.network.udp-port"))
    }

    @Test
    fun `config loads with correct default tick rate`() {
        val config = ConfigFactory.load()
        assertEquals(20, config.getInt("flyagain.gameloop.tick-rate"))
    }
}
