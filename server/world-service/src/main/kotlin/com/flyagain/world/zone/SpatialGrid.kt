package com.flyagain.world.zone

import org.slf4j.LoggerFactory

/**
 * Spatial partitioning grid for interest management.
 * Divides the game world into cells of CELL_SIZE x CELL_SIZE units.
 * Used to efficiently find nearby entities and limit network broadcast fan-out.
 *
 * Only entities within the 9 surrounding cells (3x3 neighborhood) of a
 * given position are considered "nearby" for broadcast purposes.
 */
class SpatialGrid(
    private val worldWidth: Float = 10000f,
    private val worldHeight: Float = 10000f,
    private val cellSize: Float = CELL_SIZE
) {

    companion object {
        const val CELL_SIZE = 50f
    }

    private val logger = LoggerFactory.getLogger(SpatialGrid::class.java)

    private val gridWidth = (worldWidth / cellSize).toInt() + 1
    private val gridHeight = (worldHeight / cellSize).toInt() + 1

    // Map from cell key to set of entity IDs in that cell
    private val cells = HashMap<Long, MutableSet<Long>>()

    // Map from entity ID to its current cell key
    private val entityCells = HashMap<Long, Long>()

    /**
     * Compute the cell key for a given world position.
     */
    private fun cellKey(x: Float, z: Float): Long {
        val cellX = (x / cellSize).toInt().coerceIn(0, gridWidth - 1)
        val cellZ = (z / cellSize).toInt().coerceIn(0, gridHeight - 1)
        return cellX.toLong() * gridHeight + cellZ
    }

    /**
     * Extract cell X coordinate from a cell key.
     */
    private fun cellX(key: Long): Int = (key / gridHeight).toInt()

    /**
     * Extract cell Z coordinate from a cell key.
     */
    private fun cellZ(key: Long): Int = (key % gridHeight).toInt()

    /**
     * Add an entity to the grid at the given position.
     */
    fun addEntity(entityId: Long, x: Float, z: Float) {
        val key = cellKey(x, z)
        cells.getOrPut(key) { mutableSetOf() }.add(entityId)
        entityCells[entityId] = key
    }

    /**
     * Remove an entity from the grid entirely.
     */
    fun removeEntity(entityId: Long) {
        val key = entityCells.remove(entityId) ?: return
        cells[key]?.remove(entityId)
        if (cells[key]?.isEmpty() == true) {
            cells.remove(key)
        }
    }

    /**
     * Update an entity's position in the grid.
     * Only performs a cell transfer if the entity has moved to a different cell.
     * Returns true if the cell changed.
     */
    fun updateEntity(entityId: Long, x: Float, z: Float): Boolean {
        val newKey = cellKey(x, z)
        val oldKey = entityCells[entityId]

        if (oldKey == newKey) {
            return false
        }

        // Remove from old cell
        if (oldKey != null) {
            cells[oldKey]?.remove(entityId)
            if (cells[oldKey]?.isEmpty() == true) {
                cells.remove(oldKey)
            }
        }

        // Add to new cell
        cells.getOrPut(newKey) { mutableSetOf() }.add(entityId)
        entityCells[entityId] = newKey

        return true
    }

    /**
     * Get all entity IDs in the 3x3 neighborhood (9 cells) around a position.
     * This is the core interest management query.
     */
    fun getNearbyEntities(x: Float, z: Float): Set<Long> {
        val centerCellX = (x / cellSize).toInt().coerceIn(0, gridWidth - 1)
        val centerCellZ = (z / cellSize).toInt().coerceIn(0, gridHeight - 1)

        val result = mutableSetOf<Long>()

        for (dx in -1..1) {
            for (dz in -1..1) {
                val cx = centerCellX + dx
                val cz = centerCellZ + dz

                if (cx < 0 || cx >= gridWidth || cz < 0 || cz >= gridHeight) {
                    continue
                }

                val key = cx.toLong() * gridHeight + cz
                cells[key]?.let { result.addAll(it) }
            }
        }

        return result
    }

    /**
     * Get all entity IDs in the same cell as the given position.
     */
    fun getEntitiesInCell(x: Float, z: Float): Set<Long> {
        val key = cellKey(x, z)
        return cells[key]?.toSet() ?: emptySet()
    }

    /**
     * Get all entity IDs in the 3x3 neighborhood of a given entity.
     */
    fun getNearbyEntities(entityId: Long): Set<Long> {
        val key = entityCells[entityId] ?: return emptySet()
        val cx = cellX(key)
        val cz = cellZ(key)
        val worldX = cx * cellSize + cellSize / 2
        val worldZ = cz * cellSize + cellSize / 2
        return getNearbyEntities(worldX, worldZ)
    }

    /**
     * Get the total number of tracked entities.
     */
    fun getEntityCount(): Int = entityCells.size

    /**
     * Clear all entities from the grid.
     */
    fun clear() {
        cells.clear()
        entityCells.clear()
    }
}
