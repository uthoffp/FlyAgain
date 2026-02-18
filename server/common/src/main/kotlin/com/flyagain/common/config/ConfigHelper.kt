package com.flyagain.common.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object ConfigHelper {

    fun load(): Config = ConfigFactory.load()

    fun Config.getStringOrDefault(path: String, default: String): String =
        if (hasPath(path)) getString(path) else default

    fun Config.getIntOrDefault(path: String, default: Int): Int =
        if (hasPath(path)) getInt(path) else default
}
