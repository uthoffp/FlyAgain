package com.flyagain.world.inventory

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides per-character Mutex locks to serialize inventory operations.
 * Prevents concurrent inventory mutations for the same character
 * from causing race conditions (duplicate slots, gold duplication).
 *
 * Mutexes are lazily created and retained for the player's session lifetime.
 * Cleanup happens when the player disconnects.
 */
class InventoryLockManager {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Get or create a Mutex for the given character ID. */
    fun getLock(characterId: String): Mutex = locks.computeIfAbsent(characterId) { Mutex() }

    /** Remove the lock when a player disconnects. */
    fun removeLock(characterId: String) { locks.remove(characterId) }
}
