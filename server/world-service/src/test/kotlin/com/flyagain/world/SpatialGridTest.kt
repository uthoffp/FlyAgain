package com.flyagain.world

import com.flyagain.world.zone.SpatialGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpatialGridTest {

    @Test
    fun `addEntity and getEntityCount`() {
        val grid = SpatialGrid()
        assertEquals(0, grid.getEntityCount())
        grid.addEntity(1L, 100f, 100f)
        assertEquals(1, grid.getEntityCount())
        grid.addEntity(2L, 200f, 200f)
        assertEquals(2, grid.getEntityCount())
    }

    @Test
    fun `removeEntity decreases count`() {
        val grid = SpatialGrid()
        grid.addEntity(1L, 100f, 100f)
        grid.addEntity(2L, 100f, 100f)
        assertEquals(2, grid.getEntityCount())
        grid.removeEntity(1L)
        assertEquals(1, grid.getEntityCount())
    }

    @Test
    fun `removeEntity for non-existent entity is a no-op`() {
        val grid = SpatialGrid()
        grid.removeEntity(999L) // should not throw
        assertEquals(0, grid.getEntityCount())
    }

    @Test
    fun `getEntitiesInCell returns entities at same position`() {
        val grid = SpatialGrid()
        // Both entities in the same cell (within 50x50 cell)
        grid.addEntity(1L, 10f, 10f)
        grid.addEntity(2L, 15f, 15f)
        val entities = grid.getEntitiesInCell(10f, 10f)
        assertTrue(entities.contains(1L))
        assertTrue(entities.contains(2L))
    }

    @Test
    fun `getEntitiesInCell does not return entities in distant cells`() {
        val grid = SpatialGrid()
        grid.addEntity(1L, 10f, 10f)
        grid.addEntity(2L, 500f, 500f) // far away
        val entities = grid.getEntitiesInCell(10f, 10f)
        assertTrue(entities.contains(1L))
        assertFalse(entities.contains(2L))
    }

    @Test
    fun `getNearbyEntities returns entities in adjacent cells`() {
        val grid = SpatialGrid()
        // Place entities in adjacent cells (cell size is 50)
        grid.addEntity(1L, 25f, 25f)    // cell (0,0)
        grid.addEntity(2L, 75f, 25f)    // cell (1,0) - adjacent
        grid.addEntity(3L, 25f, 75f)    // cell (0,1) - adjacent
        grid.addEntity(4L, 75f, 75f)    // cell (1,1) - diagonally adjacent
        grid.addEntity(5L, 500f, 500f)  // far away

        val nearby = grid.getNearbyEntities(25f, 25f)
        assertTrue(nearby.contains(1L))
        assertTrue(nearby.contains(2L))
        assertTrue(nearby.contains(3L))
        assertTrue(nearby.contains(4L))
        assertFalse(nearby.contains(5L))
    }

    @Test
    fun `updateEntity returns false when staying in same cell`() {
        val grid = SpatialGrid()
        grid.addEntity(1L, 10f, 10f)
        // Move within the same cell
        val changed = grid.updateEntity(1L, 15f, 15f)
        assertFalse(changed)
    }

    @Test
    fun `updateEntity returns true when moving to new cell`() {
        val grid = SpatialGrid()
        grid.addEntity(1L, 10f, 10f) // cell (0,0)
        // Move to a different cell
        val changed = grid.updateEntity(1L, 60f, 60f) // cell (1,1)
        assertTrue(changed)
    }

    @Test
    fun `updateEntity moves entity between cells correctly`() {
        val grid = SpatialGrid()
        grid.addEntity(1L, 10f, 10f) // cell (0,0)
        grid.updateEntity(1L, 200f, 200f) // cell (4,4)

        // Should no longer be in old cell
        val oldCell = grid.getEntitiesInCell(10f, 10f)
        assertFalse(oldCell.contains(1L))

        // Should be in new cell
        val newCell = grid.getEntitiesInCell(200f, 200f)
        assertTrue(newCell.contains(1L))
    }

    @Test
    fun `clear removes all entities`() {
        val grid = SpatialGrid()
        grid.addEntity(1L, 10f, 10f)
        grid.addEntity(2L, 100f, 100f)
        grid.addEntity(3L, 500f, 500f)
        assertEquals(3, grid.getEntityCount())
        grid.clear()
        assertEquals(0, grid.getEntityCount())
    }

    @Test
    fun `entities at world boundary are handled`() {
        val grid = SpatialGrid()
        // Place entity at origin
        grid.addEntity(1L, 0f, 0f)
        // Place entity at max boundary
        grid.addEntity(2L, 9999f, 9999f)
        assertEquals(2, grid.getEntityCount())

        // Nearby search at origin should find entity 1
        val nearOrigin = grid.getNearbyEntities(0f, 0f)
        assertTrue(nearOrigin.contains(1L))
        assertFalse(nearOrigin.contains(2L))
    }

    @Test
    fun `getNearbyEntities by entityId`() {
        val grid = SpatialGrid()
        grid.addEntity(1L, 25f, 25f)
        grid.addEntity(2L, 75f, 25f) // adjacent cell
        grid.addEntity(3L, 500f, 500f) // far away

        val nearby = grid.getNearbyEntities(1L)
        assertTrue(nearby.contains(1L))
        assertTrue(nearby.contains(2L))
        assertFalse(nearby.contains(3L))
    }

    @Test
    fun `getNearbyEntities by non-existent entityId returns empty`() {
        val grid = SpatialGrid()
        val nearby = grid.getNearbyEntities(999L)
        assertTrue(nearby.isEmpty())
    }
}
