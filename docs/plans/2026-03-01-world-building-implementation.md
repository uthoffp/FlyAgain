# World Building System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace flat placeholder terrain and primitive shapes with heightmap-based terrain, Quaternius MegaKit assets, and a hybrid scatter/manual placement system across all three zones.

**Architecture:** Pre-computed heightmaps (512x512 raw float binary) shared by client and server. Client renders terrain mesh + HeightMapShape3D collision. Server loads same files for movement validation. MegaKit assets imported from Godot source zip with organized folder structure. Scatter system uses MultiMeshInstance3D for mass vegetation and individual scenes for trees/rocks with collision.

**Tech Stack:** GDScript (Godot 4.6), Kotlin (JVM 21, Netty), Protocol Buffers, HeightMapShape3D, MultiMeshInstance3D

**Design Doc:** `docs/plans/2026-03-01-world-building-design.md`

---

## Task 1: Heightmap Generator Tool

**Files:**
- Create: `client/tools/heightmap_generator.gd`
- Create: `shared/heightmaps/.gitkeep`

**Purpose:** A GDScript tool that generates 512x512 heightmap binary files from noise parameters. Both client and server load these files.

**Step 1: Create directory structure**

```bash
mkdir -p shared/heightmaps
touch shared/heightmaps/.gitkeep
mkdir -p client/tools
```

**Step 2: Write the heightmap generator**

Create `client/tools/heightmap_generator.gd`:

```gdscript
## heightmap_generator.gd
## Editor tool script to generate heightmap binary files.
## Run via: Godot editor -> Script tab -> Run (Ctrl+Shift+X)
##
## Generates 512x512 raw float32 binary files (.heightmap) for each zone.
## Format: 512*512 little-endian float32 values, row-major (Z outer, X inner).
## Both client and server load the same files for consistent height data.
@tool
extends EditorScript

const SIZE := 512
const WORLD_SIZE := 10200.0

## Zone configurations: { zone_name: { seed, amplitude, frequency, octaves, lacunarity, gain } }
const ZONE_CONFIGS := {
	"aerheim": {
		"seed": 42,
		"amplitude": 3.0,
		"frequency": 0.0008,
		"octaves": 4,
		"lacunarity": 2.0,
		"gain": 0.5,
	},
	"green_plains": {
		"seed": 137,
		"amplitude": 8.0,
		"frequency": 0.0012,
		"octaves": 5,
		"lacunarity": 2.0,
		"gain": 0.45,
	},
	"dark_forest": {
		"seed": 256,
		"amplitude": 12.0,
		"frequency": 0.0018,
		"octaves": 6,
		"lacunarity": 2.1,
		"gain": 0.5,
	},
}

func _run() -> void:
	for zone_name in ZONE_CONFIGS:
		var config: Dictionary = ZONE_CONFIGS[zone_name]
		var heights := _generate_heightmap(config)
		var path := "res://../../shared/heightmaps/%s.heightmap" % zone_name
		_save_heightmap(path, heights)
		print("Generated heightmap: %s (%d samples, amplitude=%.1f)" % [
			zone_name, heights.size(), config["amplitude"]])
	print("All heightmaps generated.")


func _generate_heightmap(config: Dictionary) -> PackedFloat32Array:
	var noise := FastNoiseLite.new()
	noise.seed = config["seed"]
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	noise.frequency = config["frequency"]
	noise.fractal_type = FastNoiseLite.FRACTAL_FBM
	noise.fractal_octaves = config["octaves"]
	noise.fractal_lacunarity = config["lacunarity"]
	noise.fractal_gain = config["gain"]

	var heights := PackedFloat32Array()
	heights.resize(SIZE * SIZE)
	var amplitude: float = config["amplitude"]
	var cell_size: float = WORLD_SIZE / float(SIZE)

	for z in range(SIZE):
		for x in range(SIZE):
			var world_x: float = float(x) * cell_size
			var world_z: float = float(z) * cell_size
			var h: float = noise.get_noise_2d(world_x, world_z) * amplitude
			# Clamp to non-negative (terrain always at or above sea level)
			heights[z * SIZE + x] = maxf(h, 0.0)

	return heights


func _save_heightmap(path: String, heights: PackedFloat32Array) -> void:
	var abs_path := ProjectSettings.globalize_path(path)
	var file := FileAccess.open(abs_path, FileAccess.WRITE)
	if not file:
		push_error("Failed to open %s for writing: %s" % [abs_path, FileAccess.get_open_error()])
		return
	# Write raw float32 bytes
	var bytes := heights.to_byte_array()
	file.store_buffer(bytes)
	file.close()
```

**Step 3: Run the generator and verify output**

Run from Godot editor (Script tab → Run). Verify files exist:

```bash
ls -la shared/heightmaps/
# Expected: aerheim.heightmap, green_plains.heightmap, dark_forest.heightmap
# Each should be exactly 1,048,576 bytes (512 * 512 * 4)
```

**Step 4: Commit**

```bash
git add client/tools/heightmap_generator.gd shared/heightmaps/
git commit -m "feat: add heightmap generator tool and initial heightmap data"
```

---

## Task 2: Server HeightmapManager (TDD)

**Files:**
- Create: `server/world-service/src/main/kotlin/com/flyagain/world/terrain/HeightmapManager.kt`
- Create: `server/world-service/src/test/kotlin/com/flyagain/world/terrain/HeightmapManagerTest.kt`
- Create: `server/world-service/src/test/resources/test_heightmap.heightmap` (test fixture)

**Purpose:** Server-side heightmap loading and bilinear interpolation lookup.

**Step 1: Write the failing test**

Create `server/world-service/src/test/kotlin/com/flyagain/world/terrain/HeightmapManagerTest.kt`:

```kotlin
package com.flyagain.world.terrain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

class HeightmapManagerTest {

    private lateinit var manager: HeightmapManager

    @TempDir
    lateinit var tempDir: Path

    private fun createTestHeightmap(size: Int, fillValue: Float): File {
        val file = tempDir.resolve("test.heightmap").toFile()
        val buffer = ByteBuffer.allocate(size * size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until size * size) {
            buffer.putFloat(fillValue)
        }
        file.writeBytes(buffer.array())
        return file
    }

    private fun createGradientHeightmap(size: Int): File {
        val file = tempDir.resolve("gradient.heightmap").toFile()
        val buffer = ByteBuffer.allocate(size * size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (z in 0 until size) {
            for (x in 0 until size) {
                // Height increases linearly with X: 0.0 at x=0, 10.0 at x=size-1
                val height = (x.toFloat() / (size - 1).toFloat()) * 10.0f
                buffer.putFloat(height)
            }
        }
        file.writeBytes(buffer.array())
        return file
    }

    @Test
    fun `loads heightmap file and returns height at center`() {
        val file = createTestHeightmap(512, 5.0f)
        manager = HeightmapManager(512, 10200.0f)
        manager.loadZone(1, file.absolutePath)

        val height = manager.getHeightAt(1, 5100.0f, 5100.0f)
        assertEquals(5.0f, height, 0.01f)
    }

    @Test
    fun `returns 0 for unloaded zone`() {
        manager = HeightmapManager(512, 10200.0f)
        val height = manager.getHeightAt(99, 100.0f, 100.0f)
        assertEquals(0.0f, height)
    }

    @Test
    fun `clamps coordinates to world bounds`() {
        val file = createTestHeightmap(512, 3.0f)
        manager = HeightmapManager(512, 10200.0f)
        manager.loadZone(1, file.absolutePath)

        // Out of bounds coordinates should clamp, not crash
        val h1 = manager.getHeightAt(1, -500.0f, -500.0f)
        assertEquals(3.0f, h1, 0.01f)

        val h2 = manager.getHeightAt(1, 20000.0f, 20000.0f)
        assertEquals(3.0f, h2, 0.01f)
    }

    @Test
    fun `bilinear interpolation between grid points`() {
        val file = createGradientHeightmap(512)
        manager = HeightmapManager(512, 10200.0f)
        manager.loadZone(1, file.absolutePath)

        // At world X=0 -> grid X=0 -> height=0.0
        val h0 = manager.getHeightAt(1, 0.0f, 0.0f)
        assertEquals(0.0f, h0, 0.1f)

        // At world X=10200 -> grid X=511 -> height=10.0
        val h10200 = manager.getHeightAt(1, 10200.0f, 0.0f)
        assertEquals(10.0f, h10200, 0.1f)

        // At world X=5100 -> grid X≈255.5 -> height≈5.0
        val hMid = manager.getHeightAt(1, 5100.0f, 0.0f)
        assertEquals(5.0f, hMid, 0.2f)
    }
}
```

**Step 2: Run test to verify it fails**

```bash
cd server && ./gradlew :world-service:test --tests "com.flyagain.world.terrain.HeightmapManagerTest" -i
```

Expected: Compilation error — `HeightmapManager` does not exist.

**Step 3: Write HeightmapManager implementation**

Create `server/world-service/src/main/kotlin/com/flyagain/world/terrain/HeightmapManager.kt`:

```kotlin
package com.flyagain.world.terrain

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads pre-computed heightmap binary files and provides O(1) height lookups
 * via bilinear interpolation. Used by MovementHandler for position validation.
 *
 * Heightmap format: [size * size] little-endian float32 values, row-major (Z outer, X inner).
 * World coordinates (0..worldSize) map to grid indices (0..size-1).
 */
class HeightmapManager(
    private val size: Int = 512,
    private val worldSize: Float = 10200.0f,
) {
    private val logger = LoggerFactory.getLogger(HeightmapManager::class.java)
    private val zoneHeightmaps = mutableMapOf<Int, FloatArray>()
    private val cellSize: Float = worldSize / size.toFloat()

    fun loadZone(zoneId: Int, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            logger.error("Heightmap file not found: {}", filePath)
            return
        }

        val expectedBytes = size * size * 4
        val bytes = file.readBytes()
        if (bytes.size != expectedBytes) {
            logger.error(
                "Heightmap file size mismatch for zone {}: expected {} bytes, got {}",
                zoneId, expectedBytes, bytes.size
            )
            return
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val heights = FloatArray(size * size)
        for (i in heights.indices) {
            heights[i] = buffer.getFloat()
        }

        zoneHeightmaps[zoneId] = heights
        logger.info("Loaded heightmap for zone {} ({} x {} from {})", zoneId, size, size, filePath)
    }

    /**
     * Returns the interpolated terrain height at the given world coordinates.
     * Uses bilinear interpolation between the four nearest grid samples.
     * Returns 0.0f if the zone has no loaded heightmap.
     */
    fun getHeightAt(zoneId: Int, worldX: Float, worldZ: Float): Float {
        val heights = zoneHeightmaps[zoneId] ?: return 0.0f

        // Convert world coordinates to grid coordinates
        val gx = (worldX / cellSize).coerceIn(0.0f, (size - 1).toFloat())
        val gz = (worldZ / cellSize).coerceIn(0.0f, (size - 1).toFloat())

        val x0 = gx.toInt().coerceIn(0, size - 2)
        val z0 = gz.toInt().coerceIn(0, size - 2)
        val x1 = x0 + 1
        val z1 = z0 + 1

        val fx = gx - x0.toFloat()
        val fz = gz - z0.toFloat()

        val h00 = heights[z0 * size + x0]
        val h10 = heights[z0 * size + x1]
        val h01 = heights[z1 * size + x0]
        val h11 = heights[z1 * size + x1]

        // Bilinear interpolation
        val h0 = h00 + (h10 - h00) * fx
        val h1 = h01 + (h11 - h01) * fx
        return h0 + (h1 - h0) * fz
    }

    fun isZoneLoaded(zoneId: Int): Boolean = zoneId in zoneHeightmaps
}
```

**Step 4: Run tests to verify they pass**

```bash
cd server && ./gradlew :world-service:test --tests "com.flyagain.world.terrain.HeightmapManagerTest" -i
```

Expected: All 4 tests PASS.

**Step 5: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/terrain/HeightmapManager.kt \
       server/world-service/src/test/kotlin/com/flyagain/world/terrain/HeightmapManagerTest.kt
git commit -m "feat: add HeightmapManager with bilinear interpolation and tests"
```

---

## Task 3: Server Movement Validation Update (TDD)

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/handler/MovementHandler.kt`
- Modify: `server/world-service/src/test/kotlin/com/flyagain/world/handler/MovementHandlerTest.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/di/WorldServiceModule.kt`

**Purpose:** Replace flat ground check (`newY > 1.0f`) with heightmap lookup.

**Step 1: Write the failing tests**

Add to `MovementHandlerTest.kt`:

```kotlin
@Test
fun `applyMovement snaps grounded player to terrain height`() {
    // Setup: heightmap returns height=5.0 at the player's position
    every { heightmapManager.getHeightAt(any(), any(), any()) } returns 5.0f

    player.x = 100.0f
    player.y = 5.0f
    player.z = 100.0f
    player.inputDx = 1.0f
    player.inputDz = 0.0f
    player.isMoving = true
    player.isFlying = false

    val result = movementHandler.applyMovement(player, channel, 50)
    assertTrue(result)
    // Grounded player Y should be snapped to terrain height
    assertEquals(5.0f, player.y, 0.1f)
}

@Test
fun `applyMovement allows flying player above terrain`() {
    every { heightmapManager.getHeightAt(any(), any(), any()) } returns 5.0f

    player.x = 100.0f
    player.y = 50.0f
    player.z = 100.0f
    player.inputDx = 1.0f
    player.inputDy = 0.0f
    player.inputDz = 0.0f
    player.isMoving = true
    player.isFlying = true

    val result = movementHandler.applyMovement(player, channel, 50)
    assertTrue(result)
    assertTrue(player.y >= 5.0f)
}

@Test
fun `applyMovement rejects flying player below terrain`() {
    every { heightmapManager.getHeightAt(any(), any(), any()) } returns 10.0f

    player.x = 100.0f
    player.y = 5.0f  // below terrain height of 10.0
    player.z = 100.0f
    player.inputDx = 0.0f
    player.inputDy = -1.0f
    player.inputDz = 0.0f
    player.isMoving = true
    player.isFlying = true

    val result = movementHandler.applyMovement(player, channel, 50)
    // Should correct position — player can't fly below terrain
    assertTrue(player.y >= 10.0f)
}
```

**Step 2: Run tests to verify they fail**

```bash
cd server && ./gradlew :world-service:test --tests "com.flyagain.world.handler.MovementHandlerTest" -i
```

Expected: FAIL — HeightmapManager not injected yet.

**Step 3: Update MovementHandler**

Modify `MovementHandler.kt`:

1. Add `HeightmapManager` as constructor parameter
2. In `applyMovement()`: replace `val dy = if (player.isFlying) ... else 0f` with terrain-aware logic
3. In `validatePosition()`: replace `if (!player.isFlying && newY > 1.0f)` with heightmap lookup

Key changes to `validatePosition()`:

```kotlin
// OLD (line ~183):
// if (!player.isFlying && newY > 1.0f) { return "above_ground" }

// NEW:
val terrainHeight = heightmapManager.getHeightAt(player.zoneId, newX, newZ)
if (!player.isFlying) {
    // Grounded: snap to terrain, reject if too far above
    if (abs(newY - terrainHeight) > GROUND_TOLERANCE) {
        return "invalid_ground_height"
    }
} else {
    // Flying: must be above terrain and below ceiling
    if (newY < terrainHeight) {
        return "below_terrain"
    }
}
```

Key changes to `applyMovement()`:

```kotlin
// OLD:
// val dy = if (player.isFlying) player.inputDy * flySpeed * deltaSeconds else 0f

// NEW:
val terrainHeight = heightmapManager.getHeightAt(player.zoneId, newX, newZ)
val newY = if (player.isFlying) {
    val flyY = player.y + player.inputDy * flySpeed * deltaSeconds
    maxOf(flyY, terrainHeight) // can't fly below terrain
} else {
    terrainHeight // grounded players snap to terrain
}
```

Add companion constant:

```kotlin
private const val GROUND_TOLERANCE = 2.0f
```

**Step 4: Update DI module**

In `WorldServiceModule.kt`, add HeightmapManager as a singleton and inject it into MovementHandler:

```kotlin
single { HeightmapManager() }
single { MovementHandler(get(), get(), get(), get()) } // add HeightmapManager
```

**Step 5: Update test setup**

In `MovementHandlerTest.kt`, mock `HeightmapManager` and inject it:

```kotlin
private val heightmapManager = mockk<HeightmapManager>()

// In setup:
every { heightmapManager.getHeightAt(any(), any(), any()) } returns 0.0f // default flat

movementHandler = MovementHandler(entityManager, zoneManager, broadcastService, heightmapManager)
```

**Step 6: Run all tests**

```bash
cd server && ./gradlew :world-service:test --tests "com.flyagain.world.handler.MovementHandlerTest" -i
```

Expected: All tests PASS (old tests still pass with mocked heightmap returning 0.0f).

**Step 7: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/handler/MovementHandler.kt \
       server/world-service/src/test/kotlin/com/flyagain/world/handler/MovementHandlerTest.kt \
       server/world-service/src/main/kotlin/com/flyagain/world/di/WorldServiceModule.kt
git commit -m "feat: integrate heightmap into server movement validation"
```

---

## Task 4: Server Spawn Position and Monster AI Update

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/handler/ZoneChangeHandler.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/handler/EnterWorldHandler.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/ai/MonsterAI.kt`
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/zone/ZoneManager.kt`

**Purpose:** All server-side Y=0 hardcodes replaced with heightmap lookups.

**Step 1: Update ZoneChangeHandler.setDefaultSpawnPosition()**

Replace hardcoded `y = 0f` with:

```kotlin
player.y = heightmapManager.getHeightAt(targetZoneId, player.x, player.z)
```

Inject `HeightmapManager` into `ZoneChangeHandler` via constructor.

**Step 2: Update EnterWorldHandler**

Replace fallback `DEFAULT_SPAWN_Y = 0f` with heightmap lookup for the spawn position.

**Step 3: Update MonsterAI.moveToward()**

After calculating new X/Z, snap Y to terrain:

```kotlin
val newY = heightmapManager.getHeightAt(zoneId, newX, newZ)
monster.x = newX
monster.y = newY
monster.z = newZ
```

Inject `HeightmapManager` into `MonsterAI` via constructor.

**Step 4: Update ZoneManager spawn constants**

Remove hardcoded `DEFAULT_SPAWN_Y = 0f`. Spawn Y values will come from heightmap at runtime.

**Step 5: Update DI module**

Add `HeightmapManager` injection to `ZoneChangeHandler`, `EnterWorldHandler`, `MonsterAI`.

**Step 6: Run full test suite**

```bash
cd server && ./gradlew :world-service:test -i
```

Expected: All tests PASS. Update any tests that assumed Y=0.

**Step 7: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/
git commit -m "feat: update spawn positions and monster AI to use heightmap"
```

---

## Task 5: Server Heightmap Loading on Startup

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/WorldServiceMain.kt`
- Modify: `server/world-service/src/main/resources/application.conf`

**Purpose:** Load heightmap files on server startup.

**Step 1: Add heightmap config to application.conf**

```hocon
heightmaps {
    directory = "../../shared/heightmaps"
    zones {
        1 = "aerheim.heightmap"
        2 = "green_plains.heightmap"
        3 = "dark_forest.heightmap"
    }
}
```

**Step 2: Load heightmaps in startup sequence**

In the server startup (after Koin init), load all zone heightmaps:

```kotlin
val heightmapManager: HeightmapManager by inject()
val heightmapDir = config.getString("heightmaps.directory")
val zones = config.getObject("heightmaps.zones")
zones.forEach { (zoneId, filename) ->
    heightmapManager.loadZone(zoneId.toInt(), "$heightmapDir/${filename.unwrapped()}")
}
```

**Step 3: Verify server starts with heightmaps**

```bash
cd server && ./gradlew :world-service:run
```

Expected: Log output shows "Loaded heightmap for zone 1/2/3".

**Step 4: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/WorldServiceMain.kt \
       server/world-service/src/main/resources/application.conf
git commit -m "feat: load heightmaps on server startup from config"
```

---

## Task 6: Import MegaKit Assets into Client

**Files:**
- Create: `client/assets/nature/materials/` (all shader + material files)
- Create: `client/assets/nature/textures/` (all PNG files)
- Create: `client/assets/nature/models/trees/` (tree .tscn files)
- Create: `client/assets/nature/models/bushes/` (bush .tscn files)
- Create: `client/assets/nature/models/plants/` (plant .tscn files)
- Create: `client/assets/nature/models/flowers/` (flower .tscn files)
- Create: `client/assets/nature/models/grass/` (grass .tscn files)
- Create: `client/assets/nature/models/ground_cover/` (clover, fern, mushroom, petal)
- Create: `client/assets/nature/models/rocks/` (all rock/pebble/path .tscn files)

**Purpose:** Extract the Godot source version of the MegaKit and organize into the client asset structure.

**Step 1: Extract the Godot zip**

```bash
cd client
mkdir -p assets/nature/{materials,textures,models/{trees,bushes,plants,flowers,grass,ground_cover,rocks}}
cd /tmp && unzip "/Users/puthoff/Development/vibe-coding/FlyAgain/Stylized Nature MegaKit/Engine Projects/Stylized Nature MegaKit[Godot].zip"
```

**Step 2: Copy materials (shaders + material instances)**

```bash
cp "/tmp/Stylized Nature MegaKit[Godot]/Materials/"*.gdshader client/assets/nature/materials/
cp "/tmp/Stylized Nature MegaKit[Godot]/Materials/"*.tres client/assets/nature/materials/
```

**Step 3: Copy textures**

```bash
cp "/tmp/Stylized Nature MegaKit[Godot]/assets/"*.png client/assets/nature/textures/
```

**Step 4: Copy and sort mesh scenes by category**

```bash
# Trees
for name in Birch CommonTree CherryBlossom DeadTree GiantPine Pine TallThick TwistedTree; do
    cp "/tmp/Stylized Nature MegaKit[Godot]/Mesh Scenes/${name}_"*.tscn client/assets/nature/models/trees/
done

# Bushes
cp "/tmp/Stylized Nature MegaKit[Godot]/Mesh Scenes/Bush_"*.tscn client/assets/nature/models/bushes/

# Plants
cp "/tmp/Stylized Nature MegaKit[Godot]/Mesh Scenes/Plant_"*.tscn client/assets/nature/models/plants/

# Flowers
cp "/tmp/Stylized Nature MegaKit[Godot]/Mesh Scenes/Flower_"*.tscn client/assets/nature/models/flowers/

# Grass
cp "/tmp/Stylized Nature MegaKit[Godot]/Mesh Scenes/Grass_"*.tscn client/assets/nature/models/grass/

# Ground cover (clover, fern, mushroom, petal)
for name in Clover Fern Mushroom Petal; do
    cp "/tmp/Stylized Nature MegaKit[Godot]/Mesh Scenes/${name}_"*.tscn client/assets/nature/models/ground_cover/
done

# Rocks (rocks, pebbles, rock paths)
for name in Rock Pebble RockPath; do
    cp "/tmp/Stylized Nature MegaKit[Godot]/Mesh Scenes/${name}_"*.tscn client/assets/nature/models/rocks/ 2>/dev/null
done
```

**Step 5: Fix resource paths in .tres and .tscn files**

The MegaKit files reference paths like `res://assets/Bark_BirchTree.png` and `res://Materials/M_Bark.gdshader`. These need to be updated to:
- `res://assets/nature/textures/Bark_BirchTree.png`
- `res://assets/nature/materials/M_Bark.gdshader`

Use find-and-replace on all `.tres` and `.tscn` files:

```bash
# Fix texture paths
find client/assets/nature/ -name "*.tres" -o -name "*.tscn" | \
    xargs sed -i '' 's|res://assets/|res://assets/nature/textures/|g'

# Fix material/shader paths
find client/assets/nature/ -name "*.tres" -o -name "*.tscn" | \
    xargs sed -i '' 's|res://Materials/|res://assets/nature/materials/|g'
```

**Step 6: Open Godot and verify assets load**

Open the Godot project, navigate to `assets/nature/models/trees/`, double-click any `.tscn` to verify the model renders correctly with shaders and textures. Check for missing resource errors in the Output panel.

**Step 7: Commit**

```bash
git add client/assets/nature/
git commit -m "feat: import Quaternius Stylized Nature MegaKit into client"
```

---

## Task 7: Client Heightmap Terrain

**Files:**
- Create: `client/scripts/world/HeightmapTerrain.gd`
- Modify: `client/scenes/game/terrain/GreenPlainsTerrain.gd`
- Modify: `client/scenes/game/terrain/GreenPlainsTerrain.tscn`
- Modify: `client/scenes/game/terrain/DarkForestTerrain.gd`
- Modify: `client/scenes/game/terrain/DarkForestTerrain.tscn`
- Modify: `client/scenes/game/terrain/AerheimTerrain.gd`
- Modify: `client/scenes/game/terrain/AerheimTerrain.tscn`
- Modify: `client/scripts/world/WorldConstants.gd`

**Purpose:** Replace flat PlaneMesh + WorldBoundaryShape3D with heightmap-based terrain mesh and HeightMapShape3D collision.

**Step 1: Create HeightmapTerrain base class**

Create `client/scripts/world/HeightmapTerrain.gd`:

```gdscript
## HeightmapTerrain.gd
## Base class for heightmap-based terrain zones.
## Loads a pre-computed .heightmap binary file, generates an ArrayMesh with
## vertex displacement, and creates a HeightMapShape3D for physics collision.
class_name HeightmapTerrain
extends StaticBody3D

const HEIGHTMAP_SIZE := 512
const WORLD_SIZE := 10200.0
const CELL_SIZE := WORLD_SIZE / float(HEIGHTMAP_SIZE)

## Subclasses set these before calling setup_terrain()
var heightmap_path: String = ""
var terrain_base_color: Color = Color(0.28, 0.52, 0.18)
var terrain_valley_color: Color = Color(0.22, 0.42, 0.14)
var terrain_hill_color: Color = Color(0.34, 0.58, 0.22)
var terrain_roughness: float = 0.9

var _heights: PackedFloat32Array
var _mesh_instance: MeshInstance3D = null
var _collision_shape: CollisionShape3D = null


func setup_terrain() -> void:
	_load_heightmap()
	_create_collision()
	_create_visual_mesh()


func _load_heightmap() -> void:
	var file := FileAccess.open(heightmap_path, FileAccess.READ)
	if not file:
		push_error("Failed to load heightmap: %s" % heightmap_path)
		_heights = PackedFloat32Array()
		_heights.resize(HEIGHTMAP_SIZE * HEIGHTMAP_SIZE)
		return
	_heights = file.get_buffer(HEIGHTMAP_SIZE * HEIGHTMAP_SIZE * 4).to_float32_array()
	file.close()


func _create_collision() -> void:
	var shape := HeightMapShape3D.new()
	shape.map_width = HEIGHTMAP_SIZE
	shape.map_depth = HEIGHTMAP_SIZE

	# HeightMapShape3D expects data as PackedFloat32Array, centered at origin
	shape.map_data = _heights

	_collision_shape = CollisionShape3D.new()
	_collision_shape.shape = shape
	# Scale to match world size. HeightMapShape3D maps [-width/2..width/2] to the shape,
	# so we scale to cover WORLD_SIZE and offset to center at (WORLD_SIZE/2, 0, WORLD_SIZE/2)
	var scale_factor := WORLD_SIZE / float(HEIGHTMAP_SIZE)
	_collision_shape.scale = Vector3(scale_factor, 1.0, scale_factor)
	_collision_shape.position = Vector3(WORLD_SIZE / 2.0, 0.0, WORLD_SIZE / 2.0)
	add_child(_collision_shape)


func _create_visual_mesh() -> void:
	# Use a PlaneMesh with the terrain_noise shader for visual rendering.
	# The shader displaces vertices based on UV-mapped heightmap data.
	# For now, keep the existing noise shader but increase amplitude to match heightmap.
	# TODO: Replace with heightmap-sampled shader for exact visual match.
	_mesh_instance = MeshInstance3D.new()
	var plane := PlaneMesh.new()
	plane.size = Vector2(WORLD_SIZE, WORLD_SIZE)
	plane.subdivide_width = 200
	plane.subdivide_depth = 200

	var shader_mat := ShaderMaterial.new()
	shader_mat.shader = preload("res://scenes/game/terrain/terrain_noise.gdshader")
	shader_mat.set_shader_parameter("base_color", terrain_base_color)
	shader_mat.set_shader_parameter("valley_color", terrain_valley_color)
	shader_mat.set_shader_parameter("hill_color", terrain_hill_color)
	shader_mat.set_shader_parameter("roughness", terrain_roughness)
	# Amplitude/frequency will be set by subclass to roughly match heightmap
	plane.material = shader_mat

	_mesh_instance.mesh = plane
	_mesh_instance.position = Vector3(WORLD_SIZE / 2.0, 0.0, WORLD_SIZE / 2.0)
	add_child(_mesh_instance)


## Returns the interpolated height at world coordinates.
## Used by scatter system to place assets on terrain.
func get_height_at(world_x: float, world_z: float) -> float:
	if _heights.is_empty():
		return 0.0
	var gx := clampf(world_x / CELL_SIZE, 0.0, float(HEIGHTMAP_SIZE - 1))
	var gz := clampf(world_z / CELL_SIZE, 0.0, float(HEIGHTMAP_SIZE - 1))
	var x0 := int(gx)
	var z0 := int(gz)
	var x1 := mini(x0 + 1, HEIGHTMAP_SIZE - 1)
	var z1 := mini(z0 + 1, HEIGHTMAP_SIZE - 1)
	var fx := gx - float(x0)
	var fz := gz - float(z0)
	var h00 := _heights[z0 * HEIGHTMAP_SIZE + x0]
	var h10 := _heights[z0 * HEIGHTMAP_SIZE + x1]
	var h01 := _heights[z1 * HEIGHTMAP_SIZE + x0]
	var h11 := _heights[z1 * HEIGHTMAP_SIZE + x1]
	var h0 := h00 + (h10 - h00) * fx
	var h1 := h01 + (h11 - h01) * fx
	return h0 + (h1 - h0) * fz
```

**Step 2: Rewrite zone terrain scripts**

Rewrite `GreenPlainsTerrain.gd` to extend `HeightmapTerrain`:

```gdscript
## GreenPlainsTerrain.gd
## Zone-specific terrain for Green Plains (zone_id=2).
extends HeightmapTerrain

func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/green_plains.heightmap"
	terrain_base_color = Color(0.28, 0.52, 0.18)
	terrain_valley_color = Color(0.22, 0.42, 0.14)
	terrain_hill_color = Color(0.34, 0.58, 0.22)
	terrain_roughness = 0.9
	setup_terrain()
```

Similarly for `DarkForestTerrain.gd` and `AerheimTerrain.gd` with their respective colors and heightmap paths.

**Step 3: Update .tscn files**

Remove the old PlaneMesh, ShaderMaterial, and WorldBoundaryShape3D from each `.tscn`. The terrain script now creates these programmatically. Keep the root StaticBody3D node with the script reference.

**Step 4: Update WorldConstants.gd**

```gdscript
# Remove:
# const GROUND_Y := 0.0
# const MAX_GROUND_Y := 1.0

# Add:
const GROUND_TOLERANCE := 2.0  # max height difference for grounded validation
```

**Step 5: Update PlayerCharacter.gd click-to-move**

In `_try_click_to_move()`, the raycast currently hardcodes `_click_target.y = 0.0`. Remove that line — let the raycast hit the actual terrain collision shape and use the real Y coordinate.

**Step 6: Update spawn positions**

In `WorldConstants.gd`, update `ZONE_SPAWNS` Y values to match the heightmap heights at spawn coordinates. These values will need to be read from the generated heightmaps (or approximated after generating).

**Step 7: Verify in Godot**

Open the project, enter a zone, and verify:
- Terrain has height variation
- Player stands on the terrain surface
- Click-to-move targets terrain surface
- Physics collision matches visual mesh

**Step 8: Commit**

```bash
git add client/scripts/world/HeightmapTerrain.gd \
       client/scenes/game/terrain/ \
       client/scripts/world/WorldConstants.gd \
       client/scenes/game/PlayerCharacter.gd
git commit -m "feat: replace flat terrain with heightmap-based terrain"
```

---

## Task 8: Scatter System — Resources and Core Logic

**Files:**
- Create: `client/scripts/world/ScatterRule.gd`
- Create: `client/scripts/world/ZoneScatter.gd`

**Purpose:** Scatter system that places assets on heightmap terrain using rules (density, height/slope filters, MultiMesh for grass, individual scenes for trees/rocks).

**Step 1: Create ScatterRule resource**

Create `client/scripts/world/ScatterRule.gd`:

```gdscript
## ScatterRule.gd
## Defines placement rules for one category of scattered objects.
class_name ScatterRule
extends Resource

## Asset scenes to randomly pick from (weighted equally).
@export var scenes: Array[PackedScene] = []
## Instances per 100m² (controls density).
@export var density: float = 1.0
## Min/max terrain height for placement.
@export var height_range: Vector2 = Vector2(0.0, 500.0)
## Min/max terrain slope in degrees for placement.
@export var slope_range: Vector2 = Vector2(0.0, 90.0)
## Random Y-rotation range (degrees). 360 = full random rotation.
@export var rotation_range: float = 360.0
## Scale variation: 1.0 ± this value (e.g. 0.2 = 80%-120% scale).
@export var scale_variation: float = 0.2
## If true, use MultiMeshInstance3D for GPU instancing (no collision).
@export var use_multimesh: bool = false
## If true, add collision shapes to individual instances.
@export var collision_enabled: bool = false
## Min distance between instances of this rule.
@export var min_spacing: float = 1.0
```

**Step 2: Create ZoneScatter system**

Create `client/scripts/world/ZoneScatter.gd`:

```gdscript
## ZoneScatter.gd
## Places assets on terrain using scatter rules.
## Supports both MultiMeshInstance3D (grass, flowers) and individual scenes (trees, rocks).
class_name ZoneScatter
extends Node3D

var _terrain: HeightmapTerrain = null
var _rng: RandomNumberGenerator = RandomNumberGenerator.new()


func initialize(terrain: HeightmapTerrain, seed: int) -> void:
	_terrain = terrain
	_rng.seed = seed


## Scatter assets within a rectangular area using the given rule.
## area_min/area_max define the XZ bounds in world coordinates.
func scatter(rule: ScatterRule, area_min: Vector2, area_max: Vector2) -> void:
	if rule.scenes.is_empty():
		return

	var area_size := (area_max - area_min)
	var area_m2 := area_size.x * area_size.y
	var count := int(area_m2 * rule.density / 100.0)

	if count <= 0:
		return

	if rule.use_multimesh:
		_scatter_multimesh(rule, area_min, area_max, count)
	else:
		_scatter_individual(rule, area_min, area_max, count)


func _scatter_multimesh(rule: ScatterRule, area_min: Vector2, area_max: Vector2, count: int) -> void:
	# Group by scene for separate MultiMesh instances
	for scene in rule.scenes:
		var per_scene_count := count / rule.scenes.size()
		if per_scene_count <= 0:
			continue

		# Get the mesh from the first MeshInstance3D child
		var temp := scene.instantiate()
		var mesh: Mesh = _find_mesh(temp)
		temp.queue_free()
		if not mesh:
			continue

		var mmi := MultiMeshInstance3D.new()
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.mesh = mesh
		mm.instance_count = per_scene_count

		var placed := 0
		for i in range(per_scene_count * 3):  # try up to 3x to find valid spots
			if placed >= per_scene_count:
				break
			var x := _rng.randf_range(area_min.x, area_max.x)
			var z := _rng.randf_range(area_min.y, area_max.y)
			var y := _terrain.get_height_at(x, z)

			if y < rule.height_range.x or y > rule.height_range.y:
				continue

			var rot := deg_to_rad(_rng.randf_range(0.0, rule.rotation_range))
			var scale_val := 1.0 + _rng.randf_range(-rule.scale_variation, rule.scale_variation)

			var xform := Transform3D.IDENTITY
			xform = xform.rotated(Vector3.UP, rot)
			xform = xform.scaled(Vector3.ONE * scale_val)
			xform.origin = Vector3(x, y, z)

			mm.set_instance_transform(placed, xform)
			placed += 1

		mm.instance_count = placed  # trim unused instances
		mmi.multimesh = mm
		add_child(mmi)


func _scatter_individual(rule: ScatterRule, area_min: Vector2, area_max: Vector2, count: int) -> void:
	for i in range(count * 3):  # try up to 3x for valid spots
		if get_child_count() >= count + 10:  # rough limit
			break
		var x := _rng.randf_range(area_min.x, area_max.x)
		var z := _rng.randf_range(area_min.y, area_max.y)
		var y := _terrain.get_height_at(x, z)

		if y < rule.height_range.x or y > rule.height_range.y:
			continue

		var scene: PackedScene = rule.scenes[_rng.randi() % rule.scenes.size()]
		var instance := scene.instantiate()

		var rot := deg_to_rad(_rng.randf_range(0.0, rule.rotation_range))
		var scale_val := 1.0 + _rng.randf_range(-rule.scale_variation, rule.scale_variation)

		instance.position = Vector3(x, y, z)
		instance.rotation.y = rot
		instance.scale = Vector3.ONE * scale_val
		add_child(instance)


## Find the first Mesh resource in a scene tree (for MultiMesh extraction).
func _find_mesh(node: Node) -> Mesh:
	if node is MeshInstance3D and node.mesh:
		return node.mesh
	for child in node.get_children():
		var m := _find_mesh(child)
		if m:
			return m
	return null
```

**Step 3: Commit**

```bash
git add client/scripts/world/ScatterRule.gd client/scripts/world/ZoneScatter.gd
git commit -m "feat: add scatter system with MultiMesh and individual placement"
```

---

## Task 9: Zone Scatter Configurations

**Files:**
- Modify: `client/scenes/game/terrain/GreenPlainsTerrain.gd`
- Modify: `client/scenes/game/terrain/DarkForestTerrain.gd`
- Modify: `client/scenes/game/terrain/AerheimTerrain.gd`

**Purpose:** Configure each zone with specific scatter rules using MegaKit assets. Remove old primitive shape decorations.

**Step 1: Update GreenPlainsTerrain with scatter**

Replace the old `_place_decorations()` with scatter rules:

```gdscript
extends HeightmapTerrain

const SPAWN := Vector3(200.0, 0.0, 200.0)

func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/green_plains.heightmap"
	terrain_base_color = Color(0.28, 0.52, 0.18)
	terrain_valley_color = Color(0.22, 0.42, 0.14)
	terrain_hill_color = Color(0.34, 0.58, 0.22)
	terrain_roughness = 0.9
	setup_terrain()
	_setup_scatter()


func _setup_scatter() -> void:
	var scatter := ZoneScatter.new()
	scatter.initialize(self, 137)
	add_child(scatter)

	# Scatter area around spawn (400x400 units centered on spawn)
	var area_min := Vector2(SPAWN.x - 200, SPAWN.z - 200)
	var area_max := Vector2(SPAWN.x + 200, SPAWN.z + 200)

	# Trees (individual, with collision)
	var tree_rule := ScatterRule.new()
	tree_rule.scenes = [
		preload("res://assets/nature/models/trees/CommonTree_1.tscn"),
		preload("res://assets/nature/models/trees/CommonTree_2.tscn"),
		preload("res://assets/nature/models/trees/CommonTree_3.tscn"),
		preload("res://assets/nature/models/trees/Birch_1.tscn"),
		preload("res://assets/nature/models/trees/Birch_2.tscn"),
		preload("res://assets/nature/models/trees/CherryBlossom_1.tscn"),
		preload("res://assets/nature/models/trees/CherryBlossom_2.tscn"),
	]
	tree_rule.density = 0.3  # ~0.3 trees per 100m²
	tree_rule.scale_variation = 0.25
	tree_rule.collision_enabled = true
	tree_rule.min_spacing = 8.0
	scatter.scatter(tree_rule, area_min, area_max)

	# Grass (MultiMesh, no collision)
	var grass_rule := ScatterRule.new()
	grass_rule.scenes = [
		preload("res://assets/nature/models/grass/Grass_Common_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Common_Tall.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wide_Short.tscn"),
	]
	grass_rule.density = 50.0  # 50 per 100m²
	grass_rule.use_multimesh = true
	grass_rule.scale_variation = 0.3
	scatter.scatter(grass_rule, area_min, area_max)

	# Flowers (MultiMesh)
	var flower_rule := ScatterRule.new()
	flower_rule.scenes = [
		preload("res://assets/nature/models/flowers/Flower_1_Group.tscn"),
		preload("res://assets/nature/models/flowers/Flower_2_Group.tscn"),
		preload("res://assets/nature/models/flowers/Flower_3_Single.tscn"),
	]
	flower_rule.density = 5.0
	flower_rule.use_multimesh = true
	scatter.scatter(flower_rule, area_min, area_max)

	# Rocks (individual, with collision)
	var rock_rule := ScatterRule.new()
	rock_rule.scenes = [
		preload("res://assets/nature/models/rocks/Rock_Medium_1.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Medium_2.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Round_1.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Round_2.tscn"),
	]
	rock_rule.density = 0.2
	rock_rule.collision_enabled = true
	scatter.scatter(rock_rule, area_min, area_max)

	# Bushes (individual)
	var bush_rule := ScatterRule.new()
	bush_rule.scenes = [
		preload("res://assets/nature/models/bushes/Bush_Common_Flowers.tscn"),
		preload("res://assets/nature/models/bushes/Bush_Large_Flowers.tscn"),
	]
	bush_rule.density = 0.15
	scatter.scatter(bush_rule, area_min, area_max)
```

**Step 2: Update DarkForestTerrain similarly**

Use dark forest asset palette: TwistedTree, TallThick, DeadTree, GiantPine, Fern, Mushrooms. Higher tree density (0.5), no flowers, darker atmosphere.

**Step 3: Update AerheimTerrain**

Sparse nature scatter for outskirts. CommonTree, Pine, Birch with low density (0.1). Short grass only. Keep AerheimCity loading.

**Step 4: Verify visually in Godot**

Load each zone and verify assets are scattered correctly on the heightmap terrain.

**Step 5: Commit**

```bash
git add client/scenes/game/terrain/
git commit -m "feat: add zone-specific scatter configurations with MegaKit assets"
```

---

## Task 10: Update GameWorld and Portal Positions

**Files:**
- Modify: `client/scenes/game/GameWorld.gd`
- Modify: `client/scenes/game/PlayerCharacter.gd`

**Purpose:** Update portal Y positions for heightmap terrain. Ensure player spawns at correct terrain height.

**Step 1: Update GameWorld portal positions**

Portal positions in `ZONE_PORTALS` currently hardcode Y=0. After terrain loads, portal Y should be set from the heightmap:

```gdscript
func _spawn_portals(zone_id: int) -> void:
	_clear_portals()
	var portal_defs: Array = ZONE_PORTALS.get(zone_id, [])
	for def in portal_defs:
		var portal: Area3D = ZonePortalScene.instantiate()
		portal.target_zone_id = def["target"]
		portal.portal_label = WorldConstants.get_zone_name(def["target"])
		portal.portal_color = def.get("color", Color(0.3, 0.5, 1.0, 0.8))
		var pos: Vector3 = def["position"]
		# Adjust Y to terrain height
		if _terrain and _terrain is HeightmapTerrain:
			pos.y = _terrain.get_height_at(pos.x, pos.z)
		portal.position = pos
		portal.portal_entered.connect(_on_portal_entered)
		add_child(portal)
		_portals.append(portal)
```

**Step 2: Update player spawn height**

In `_on_zone_data()`, after setting spawn position, adjust Y to terrain height:

```gdscript
if _player and is_instance_valid(_player):
	var spawn_pos: Vector3 = WorldConstants.ZONE_SPAWNS.get(
		GameState.current_zone_id, WorldConstants.DEFAULT_SPAWN)
	if _terrain and _terrain is HeightmapTerrain:
		spawn_pos.y = _terrain.get_height_at(spawn_pos.x, spawn_pos.z)
	_player.teleport_to(spawn_pos)
	GameState.player_position = spawn_pos
```

**Step 3: Update PlayerCharacter click-to-move**

In `_try_click_to_move()`, remove the `_click_target.y = 0.0` line. The raycast now hits the HeightMapShape3D collision and returns the correct terrain Y.

**Step 4: Update MovementPredictor**

The `MovementPredictor` currently clamps grounded Y to `GROUND_Y (0.0)`. It should instead use the terrain height. This may require passing a height callback or accepting that the server corrects minor Y differences.

**Step 5: Verify**

- Zone transitions: player appears at correct height
- Portals float at correct height above terrain
- Click-to-move works on sloped terrain
- Movement feels smooth on hills

**Step 6: Commit**

```bash
git add client/scenes/game/GameWorld.gd client/scenes/game/PlayerCharacter.gd
git commit -m "feat: update portals and player spawning for heightmap terrain"
```

---

## Task 11: Integration Testing and Polish

**Files:**
- All modified files from previous tasks

**Purpose:** End-to-end verification that client and server work together with heightmap terrain.

**Step 1: Run server tests**

```bash
cd server && ./gradlew test
```

Expected: All tests PASS.

**Step 2: Run client tests**

```bash
cd client && godot --headless --run-tests
```

Expected: Existing tests still pass.

**Step 3: Start full stack and test**

```bash
docker-compose up -d  # PostgreSQL + Redis
cd server && ./gradlew :world-service:run &
cd server && ./gradlew :login-service:run &
cd server && ./gradlew :account-service:run &
cd server && ./gradlew :database-service:run &
# Open Godot and run the game
```

Verify:
- Login → character select → enter world
- Player stands on heightmap terrain (not floating, not underground)
- Walk up/down hills — movement is smooth
- Zone transition — portal at correct height, player spawns at correct height
- Remote entities appear at correct heights
- Trees, grass, rocks are scattered on terrain
- MegaKit shaders render correctly (wind animation on grass/leaves)
- Server log shows no position correction spam

**Step 4: Tune scatter parameters**

Adjust density, scale, and spacing values based on visual results. This is iterative.

**Step 5: Final commit**

```bash
git add -A
git commit -m "feat: complete world building system integration"
```

---

## Summary

| Task | Description | Scope |
|------|-------------|-------|
| 1 | Heightmap generator tool | Client tool |
| 2 | Server HeightmapManager (TDD) | Server |
| 3 | Server movement validation update (TDD) | Server |
| 4 | Server spawn + monster AI update | Server |
| 5 | Server heightmap loading on startup | Server |
| 6 | Import MegaKit assets | Client |
| 7 | Client heightmap terrain | Client |
| 8 | Scatter system core | Client |
| 9 | Zone scatter configurations | Client |
| 10 | GameWorld + portal + player updates | Client |
| 11 | Integration testing | Full stack |
