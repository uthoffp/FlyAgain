package com.flyagain.common.network

import io.netty.channel.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks heartbeat timestamps per channel and disconnects clients
 * that fail to send a heartbeat within [timeoutMs].
 *
 * Per ARCHITECTURE.md section 4.7:
 * - Heartbeat-Timeout: 15 seconds without heartbeat -> Disconnect
 * - The check runs every [checkIntervalMs] (default: 5s)
 *
 * Usage:
 * 1. Call [register] when a client connects (or after authentication).
 * 2. Call [recordHeartbeat] each time a HEARTBEAT packet is received.
 * 3. Call [unregister] when the client disconnects.
 * 4. Call [start] to begin the background timeout checker.
 * 5. Call [stop] during shutdown.
 *
 * @param timeoutMs time in milliseconds before a client without heartbeat is disconnected (default: 15000)
 * @param checkIntervalMs how often to scan for timed-out clients (default: 5000)
 */
class HeartbeatTracker(
    private val timeoutMs: Long = 15_000,
    private val checkIntervalMs: Long = 5_000
) {
    private val logger = LoggerFactory.getLogger(HeartbeatTracker::class.java)
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Maps channel ID to last heartbeat timestamp (epoch millis). */
    private val lastHeartbeat = ConcurrentHashMap<Channel, Long>()

    /**
     * Registers a channel for heartbeat tracking.
     * Sets the initial heartbeat time to now.
     */
    fun register(channel: Channel) {
        lastHeartbeat[channel] = System.currentTimeMillis()
        logger.debug("Heartbeat tracking started for {}", channel.remoteAddress())
    }

    /**
     * Records a heartbeat from the given channel.
     */
    fun recordHeartbeat(channel: Channel) {
        lastHeartbeat.computeIfPresent(channel) { _, _ -> System.currentTimeMillis() }
    }

    /**
     * Unregisters a channel from heartbeat tracking.
     */
    fun unregister(channel: Channel) {
        lastHeartbeat.remove(channel)
        logger.debug("Heartbeat tracking stopped for {}", channel.remoteAddress())
    }

    /**
     * Returns the number of channels currently being tracked.
     */
    fun getTrackedCount(): Int = lastHeartbeat.size

    /**
     * Returns the time in millis since the last heartbeat for the given channel,
     * or -1 if the channel is not tracked.
     */
    fun getTimeSinceLastHeartbeat(channel: Channel): Long {
        val last = lastHeartbeat[channel] ?: return -1
        return System.currentTimeMillis() - last
    }

    /**
     * Starts the background coroutine that periodically checks for timed-out channels.
     */
    fun start() {
        // Recreate scope in case stop() was called previously
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        }
        logger.info("HeartbeatTracker started (timeout={}ms, checkInterval={}ms)", timeoutMs, checkIntervalMs)
        scope.launch {
            while (isActive) {
                delay(checkIntervalMs)
                checkTimeouts()
            }
        }
    }

    /**
     * Stops the background checker.
     */
    fun stop() {
        logger.info("HeartbeatTracker stopping...")
        scope.cancel()
        lastHeartbeat.clear()
    }

    private fun checkTimeouts() {
        val now = System.currentTimeMillis()
        val iterator = lastHeartbeat.entries.iterator()

        while (iterator.hasNext()) {
            val (channel, lastTime) = iterator.next()
            val elapsed = now - lastTime

            if (elapsed > timeoutMs) {
                logger.info("Heartbeat timeout ({}ms) for {}, disconnecting", elapsed, channel.remoteAddress())
                iterator.remove()
                channel.close()
            }
        }
    }
}
