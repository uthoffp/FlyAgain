package com.flyagain.common.logging

import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey
import org.slf4j.MDC

/**
 * Centralised MDC key constants and convenience methods for request tracing.
 *
 * MDC keys use short names to keep log lines compact:
 *   ip  — client IP address
 *   sid — session ID
 *   acc — account ID (UUID)
 *   chr — character ID (UUID)
 *   player — character display name
 *
 * Because Netty event-loop threads are shared across many channels, thread-local
 * MDC values set in one handler invocation can be overwritten by a different
 * channel's event before the next invocation.  To avoid mis-attributed log lines
 * we persist MDC values as **Netty channel attributes** and restore them at the
 * start of every [channelRead0] / lifecycle callback via [restoreMdc].
 */
object MdcHelper {

    // ---- MDC key names (used in logback patterns) ----
    const val IP = "ip"
    const val SESSION_ID = "sid"
    const val ACCOUNT_ID = "acc"
    const val CHARACTER_ID = "chr"
    const val PLAYER_NAME = "player"

    // ---- Netty channel attribute keys ----
    private val ATTR_IP: AttributeKey<String> = AttributeKey.valueOf("mdc.ip")
    private val ATTR_SESSION_ID: AttributeKey<String> = AttributeKey.valueOf("mdc.sid")
    private val ATTR_ACCOUNT_ID: AttributeKey<String> = AttributeKey.valueOf("mdc.acc")
    private val ATTR_CHARACTER_ID: AttributeKey<String> = AttributeKey.valueOf("mdc.chr")
    private val ATTR_PLAYER_NAME: AttributeKey<String> = AttributeKey.valueOf("mdc.player")

    /**
     * Restore the thread-local MDC from the channel's persisted attributes.
     * **Must** be called at the start of every [channelRead0], [channelActive],
     * and [channelInactive] to ensure correct attribution on shared I/O threads.
     */
    fun restoreMdc(ctx: ChannelHandlerContext) {
        MDC.clear()
        val ch = ctx.channel()
        ch.attr(ATTR_IP).get()?.let { MDC.put(IP, it) }
        ch.attr(ATTR_ACCOUNT_ID).get()?.let { MDC.put(ACCOUNT_ID, it) }
        ch.attr(ATTR_SESSION_ID).get()?.let { MDC.put(SESSION_ID, it) }
        ch.attr(ATTR_CHARACTER_ID).get()?.let { MDC.put(CHARACTER_ID, it) }
        ch.attr(ATTR_PLAYER_NAME).get()?.let { MDC.put(PLAYER_NAME, it) }
    }

    /** Store and set the client IP when a TCP channel becomes active. */
    fun setConnection(ctx: ChannelHandlerContext, ip: String) {
        ctx.channel().attr(ATTR_IP).set(ip)
        MDC.put(IP, ip)
    }

    /** Clear all MDC state when a TCP channel becomes inactive. */
    fun clearAll() {
        MDC.clear()
    }

    /**
     * Populate identity fields after authentication.
     * Values are persisted as channel attributes *and* set on the current thread's MDC.
     * Null parameters are ignored (existing values are kept).
     */
    fun setPlayer(
        ctx: ChannelHandlerContext,
        accountId: String? = null,
        sessionId: String? = null,
        characterId: String? = null,
        playerName: String? = null
    ) {
        val ch = ctx.channel()
        accountId?.let { ch.attr(ATTR_ACCOUNT_ID).set(it); MDC.put(ACCOUNT_ID, it) }
        sessionId?.let { ch.attr(ATTR_SESSION_ID).set(it); MDC.put(SESSION_ID, it) }
        characterId?.let { ch.attr(ATTR_CHARACTER_ID).set(it); MDC.put(CHARACTER_ID, it) }
        playerName?.let { ch.attr(ATTR_PLAYER_NAME).set(it); MDC.put(PLAYER_NAME, it) }
    }

    /** Execute [block] with temporary MDC entries, restoring previous state afterwards. */
    inline fun <T> withContext(vararg pairs: Pair<String, String>, block: () -> T): T {
        val previous = pairs.map { (key, _) -> key to MDC.get(key) }
        pairs.forEach { (key, value) -> MDC.put(key, value) }
        return try {
            block()
        } finally {
            previous.forEach { (key, oldValue) ->
                if (oldValue == null) MDC.remove(key) else MDC.put(key, oldValue)
            }
        }
    }
}
