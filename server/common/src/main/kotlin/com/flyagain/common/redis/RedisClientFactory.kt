package com.flyagain.common.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.slf4j.LoggerFactory

object RedisClientFactory {

    private val logger = LoggerFactory.getLogger(RedisClientFactory::class.java)

    fun create(redisUrl: String): RedisClient {
        logger.info("Connecting to Redis at {}", redisUrl)
        return RedisClient.create(redisUrl)
    }

    fun createConnection(client: RedisClient): StatefulRedisConnection<String, String> {
        val connection = client.connect()
        logger.info("Redis connection established")
        return connection
    }
}
