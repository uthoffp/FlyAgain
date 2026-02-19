package com.flyagain.common.network

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * In-memory UDP flood protection that rate-limits packets per IP address.
 *
 * Per ARCHITECTURE.md section 4.5:
 * - Max [maxPacketsPerSecond] UDP packets per IP per second (default: 100)
 * - Excess packets are silently dropped
 * - Executed **before** any Redis lookup or crypto to prevent resource exhaustion
 *
 * Uses a fixed-window approach: each IP gets a counter that resets every second.
 * A background cleanup task removes stale entries every [cleanupIntervalSec] seconds.
 *
 * @param maxPacketsPerSecond maximum allowed packets per second per IP address
 * @param cleanupIntervalSec how often stale entries are removed (default: 60)
 */
class UdpFloodProtection(
    private val maxPacketsPerSecond: Int = 100,
    private val cleanupIntervalSec: Long = 60
) {
    private val logger = LoggerFactory.getLogger(UdpFloodProtection::class.java)

    private class IpCounter {
        var count: Int = 0
        var windowStart: Long = System.currentTimeMillis()
    }

    private val counters = ConcurrentHashMap<String, IpCounter>()
    private var scheduler: ScheduledExecutorService? = null

    /**
     * Returns `true` if the packet from [ip] should be processed,
     * `false` if the IP has exceeded its rate limit for the current second.
     */
    fun allowPacket(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val counter = counters.computeIfAbsent(ip) { IpCounter() }

        synchronized(counter) {
            if (now - counter.windowStart >= 1000) {
                counter.count = 0
                counter.windowStart = now
            }
            counter.count++
            return counter.count <= maxPacketsPerSecond
        }
    }

    /**
     * Returns the current packet count for a given IP in this window.
     * Useful for monitoring/testing.
     */
    fun getPacketCount(ip: String): Int {
        val counter = counters[ip] ?: return 0
        synchronized(counter) {
            return counter.count
        }
    }

    /**
     * Starts the periodic cleanup task that removes stale entries.
     */
    fun start() {
        val exec = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "udp-flood-cleanup").apply { isDaemon = true }
        }
        scheduler = exec
        exec.scheduleAtFixedRate(
            { cleanup() },
            cleanupIntervalSec,
            cleanupIntervalSec,
            TimeUnit.SECONDS
        )
        logger.info("UdpFloodProtection started (max={}/s, cleanup={}s)", maxPacketsPerSecond, cleanupIntervalSec)
    }

    /**
     * Stops the periodic cleanup task.
     */
    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
        counters.clear()
        logger.info("UdpFloodProtection stopped")
    }

    /**
     * Removes stale entries that haven't been updated in the last [staleThresholdMs].
     */
    fun cleanup(staleThresholdMs: Long = 60_000) {
        val now = System.currentTimeMillis()
        val iterator = counters.entries.iterator()
        var removed = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            synchronized(entry.value) {
                if (now - entry.value.windowStart > staleThresholdMs) {
                    iterator.remove()
                    removed++
                }
            }
        }
        if (removed > 0) {
            logger.debug("UdpFloodProtection: cleaned up {} stale IP entries", removed)
        }
    }

    /** Returns the number of tracked IPs. */
    fun getTrackedIpCount(): Int = counters.size
}
