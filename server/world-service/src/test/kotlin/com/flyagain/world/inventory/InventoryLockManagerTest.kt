package com.flyagain.world.inventory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class InventoryLockManagerTest {

    private val lockManager = InventoryLockManager()

    @Test
    fun `getLock returns same Mutex for same characterId`() {
        val lock1 = lockManager.getLock("char-1")
        val lock2 = lockManager.getLock("char-1")
        assertSame(lock1, lock2, "Same characterId should return the same Mutex instance")
    }

    @Test
    fun `getLock returns different Mutexes for different characterIds`() {
        val lock1 = lockManager.getLock("char-1")
        val lock2 = lockManager.getLock("char-2")
        assertNotSame(lock1, lock2, "Different characterIds should return different Mutex instances")
    }

    @Test
    fun `removeLock removes the lock for a characterId`() {
        val lockBefore = lockManager.getLock("char-1")
        lockManager.removeLock("char-1")
        val lockAfter = lockManager.getLock("char-1")
        assertNotSame(lockBefore, lockAfter, "After removeLock, getLock should return a new Mutex instance")
    }

    @Test
    fun `removeLock is safe for non-existent characterId`() {
        // Should not throw
        lockManager.removeLock("non-existent")
    }

    @Test
    fun `getLock after removeLock returns a new Mutex`() {
        lockManager.getLock("char-42")
        lockManager.removeLock("char-42")
        val newLock = lockManager.getLock("char-42")
        // Just verify it returns a valid Mutex (not null)
        assertEquals(false, newLock.isLocked, "New Mutex should not be locked")
    }
}
