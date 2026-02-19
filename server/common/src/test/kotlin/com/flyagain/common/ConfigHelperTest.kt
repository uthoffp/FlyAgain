package com.flyagain.common

import com.flyagain.common.config.ConfigHelper
import com.flyagain.common.config.ConfigHelper.getIntOrDefault
import com.flyagain.common.config.ConfigHelper.getStringOrDefault
import com.typesafe.config.ConfigFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigHelperTest {

    @Test
    fun `load returns a non-null config`() {
        val config = ConfigHelper.load()
        // ConfigFactory.load() always returns a config, even if empty
        assertEquals(false, config.isEmpty)
    }

    @Test
    fun `getStringOrDefault returns value when path exists`() {
        val config = ConfigFactory.parseMap(mapOf("test.key" to "hello"))
        assertEquals("hello", config.getStringOrDefault("test.key", "fallback"))
    }

    @Test
    fun `getStringOrDefault returns default when path missing`() {
        val config = ConfigFactory.empty()
        assertEquals("fallback", config.getStringOrDefault("missing.key", "fallback"))
    }

    @Test
    fun `getIntOrDefault returns value when path exists`() {
        val config = ConfigFactory.parseMap(mapOf("test.port" to 9090))
        assertEquals(9090, config.getIntOrDefault("test.port", 1234))
    }

    @Test
    fun `getIntOrDefault returns default when path missing`() {
        val config = ConfigFactory.empty()
        assertEquals(1234, config.getIntOrDefault("missing.port", 1234))
    }
}
