# World Building System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace flat placeholder terrain and primitive shapes with heightmap-based terrain, Quaternius MegaKit assets, and a hybrid scatter/manual placement system across all three zones.

**Architecture:** Pre-computed heightmaps (512x512 raw float binary) used by the client for terrain mesh and collision. Server uses a generous Y-tolerance instead of exact heightmap lookup (heightmap server integration deferred as future anti-cheat hardening). MegaKit assets imported from Godot source zip. Scatter system uses MultiMeshInstance3D for mass vegetation and individual scenes for trees/rocks with collision.

**Tech Stack:** GDScript (Godot 4.6), Kotlin (JVM 21, Netty), Protocol Buffers, HeightMapShape3D, MultiMeshInstance3D

**Design Doc:** `docs/plans/2026-03-01-world-building-design.md`

---

## Task 1: Heightmap Generator Tool

**Files:**
- Create: `client/tools/heightmap_generator.gd`
- Create: `shared/heightmaps/.gitkeep`

**Purpose:** A GDScript tool that generates 512x512 heightmap binary files from noise parameters. The client loads these files for terrain rendering and collision.

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
	var bytes := heights.to_byte_array()
	file.store_buffer(bytes)
	file.close()
```

**Step 3: Run the generator and verify output**

Run from Godot editor (Script tab -> Run). Verify files exist:

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

## Task 2: Server Movement Validation — Generous Y-Tolerance

**Files:**
- Modify: `server/world-service/src/main/kotlin/com/flyagain/world/handler/MovementHandler.kt`
- Modify: `server/world-service/src/test/kotlin/com/flyagain/world/handler/MovementHandlerTest.kt`

**Purpose:** Replace the flat ground check (`newY > 1.0f`) with a generous tolerance that accommodates heightmap terrain without requiring the server to load heightmap files. Future anti-cheat hardening can add exact heightmap lookup later.

**Step 1: Write the failing test**

Add to `MovementHandlerTest.kt`:

```kotlin
@Test
fun `applyMovement allows grounded player at heightmap elevation`() {
    // A player walking on a hill at Y=10 should be valid
    player.x = 100.0f
    player.y = 10.0f
    player.z = 100.0f
    player.inputDx = 1.0f
    player.inputDz = 0.0f
    player.isMoving = true
    player.isFlying = false

    val result = movementHandler.applyMovement(player, channel, 50)
    assertTrue(result)
}

@Test
fun `applyMovement rejects grounded player above max terrain height`() {
    // A grounded player at Y=50 is impossible (max terrain amplitude is ~15m + tolerance)
    player.x = 100.0f
    player.y = 50.0f
    player.z = 100.0f
    player.inputDx = 0.0f
    player.inputDz = 0.0f
    player.isMoving = true
    player.isFlying = false

    val result = movementHandler.applyMovement(player, channel, 50)
    // Should be corrected — 50.0 is way above any terrain
    assertFalse(result)
}
```

**Step 2: Run test to verify it fails**

```bash
cd server && ./gradlew :world-service:test --tests "com.flyagain.world.handler.MovementHandlerTest" -i
```

Expected: First test FAILS (current code rejects Y > 1.0 for grounded players).

**Step 3: Update MovementHandler.validatePosition()**

In `MovementHandler.kt`, replace the ground check:

```kotlin
// OLD (line ~183):
// if (!player.isFlying && newY > 1.0f) { return "above_ground" }

// NEW — generous tolerance for heightmap terrain (max amplitude ~15m + 2m tolerance)
private const val MAX_TERRAIN_HEIGHT = 15.0f
private const val GROUND_TOLERANCE = 2.0f

// In validatePosition():
if (!player.isFlying && newY > MAX_TERRAIN_HEIGHT + GROUND_TOLERANCE) {
    return "above_ground"
}
```

**Step 4: Run tests to verify they pass**

```bash
cd server && ./gradlew :world-service:test --tests "com.flyagain.world.handler.MovementHandlerTest" -i
```

Expected: All tests PASS.

**Step 5: Commit**

```bash
git add server/world-service/src/main/kotlin/com/flyagain/world/handler/MovementHandler.kt \
       server/world-service/src/test/kotlin/com/flyagain/world/handler/MovementHandlerTest.kt
git commit -m "feat: update server ground validation with generous Y-tolerance for heightmap terrain"
```

---

## Task 3: Import MegaKit Assets into Client

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
cd /tmp && unzip "/Users/puthoff/Development/vibe-coding/FlyAgain/Stylized Nature MegaKit/Engine Projects/Stylized Nature MegaKit[Godot].zip"
```

**Step 2: Create directory structure**

```bash
cd /Users/puthoff/Development/vibe-coding/FlyAgain/client
mkdir -p assets/nature/{materials,textures,models/{trees,bushes,plants,flowers,grass,ground_cover,rocks}}
```

**Step 3: Copy materials (shaders + material instances)**

```bash
cp "/tmp/Stylized Nature MegaKit[Godot]/Materials/"*.gdshader client/assets/nature/materials/
cp "/tmp/Stylized Nature MegaKit[Godot]/Materials/"*.tres client/assets/nature/materials/
```

**Step 4: Copy textures**

```bash
cp "/tmp/Stylized Nature MegaKit[Godot]/assets/"*.png client/assets/nature/textures/
```

**Step 5: Copy and sort mesh scenes by category**

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
    cp "/tmp/Stylized Nature MegaKit[Godot]/Mesh Scenes/${name}"*.tscn client/assets/nature/models/rocks/ 2>/dev/null
done
```

**Step 6: Fix resource paths in .tres and .tscn files**

The MegaKit files reference paths like `res://assets/Bark_BirchTree.png` and `res://Materials/M_Bark.gdshader`. Update to new structure:

```bash
# Fix texture paths
find client/assets/nature/ -name "*.tres" -o -name "*.tscn" | \
    xargs sed -i '' 's|res://assets/|res://assets/nature/textures/|g'

# Fix material/shader paths
find client/assets/nature/ -name "*.tres" -o -name "*.tscn" | \
    xargs sed -i '' 's|res://Materials/|res://assets/nature/materials/|g'
```

**Step 7: Verify in Godot**

Open the Godot project, navigate to `assets/nature/models/trees/`, double-click any `.tscn` to verify the model renders correctly with shaders and textures. Check for missing resource errors in the Output panel.

**Step 8: Commit**

```bash
git add client/assets/nature/
git commit -m "feat: import Quaternius Stylized Nature MegaKit into client"
```

---

## Task 4: Client Heightmap Terrain

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
## Loads a pre-computed .heightmap binary file, generates terrain collision
## via HeightMapShape3D, and visual mesh via PlaneMesh with the terrain shader.
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
var terrain_amplitude: float = 8.0
var terrain_frequency: float = 0.002

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
	shape.map_data = _heights

	_collision_shape = CollisionShape3D.new()
	_collision_shape.shape = shape
	# HeightMapShape3D maps [-width/2..width/2], so scale to cover WORLD_SIZE
	var scale_factor := WORLD_SIZE / float(HEIGHTMAP_SIZE)
	_collision_shape.scale = Vector3(scale_factor, 1.0, scale_factor)
	_collision_shape.position = Vector3(WORLD_SIZE / 2.0, 0.0, WORLD_SIZE / 2.0)
	add_child(_collision_shape)


func _create_visual_mesh() -> void:
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
	shader_mat.set_shader_parameter("amplitude", terrain_amplitude)
	shader_mat.set_shader_parameter("frequency", terrain_frequency)
	shader_mat.set_shader_parameter("roughness", terrain_roughness)
	plane.material = shader_mat

	_mesh_instance.mesh = plane
	_mesh_instance.position = Vector3(WORLD_SIZE / 2.0, 0.0, WORLD_SIZE / 2.0)
	add_child(_mesh_instance)


## Returns the interpolated height at world coordinates.
## Used by scatter system and GameWorld for placing objects on terrain.
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

Rewrite `GreenPlainsTerrain.gd`:

```gdscript
## GreenPlainsTerrain.gd
## Zone-specific terrain for Green Plains (zone_id=2).
extends HeightmapTerrain

func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/green_plains.heightmap"
	terrain_base_color = Color(0.28, 0.52, 0.18)
	terrain_valley_color = Color(0.22, 0.42, 0.14)
	terrain_hill_color = Color(0.34, 0.58, 0.22)
	terrain_amplitude = 8.0
	terrain_frequency = 0.0012
	terrain_roughness = 0.9
	setup_terrain()
```

Rewrite `DarkForestTerrain.gd`:

```gdscript
## DarkForestTerrain.gd
## Zone-specific terrain for Dark Forest (zone_id=3).
extends HeightmapTerrain

func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/dark_forest.heightmap"
	terrain_base_color = Color(0.15, 0.2, 0.1)
	terrain_valley_color = Color(0.1, 0.15, 0.08)
	terrain_hill_color = Color(0.2, 0.25, 0.14)
	terrain_amplitude = 12.0
	terrain_frequency = 0.0018
	terrain_roughness = 0.95
	setup_terrain()
```

Rewrite `AerheimTerrain.gd`:

```gdscript
## AerheimTerrain.gd
## Zone-specific terrain for Aerheim (zone_id=1).
extends HeightmapTerrain

func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/aerheim.heightmap"
	terrain_base_color = Color(0.45, 0.42, 0.35)
	terrain_valley_color = Color(0.38, 0.35, 0.28)
	terrain_hill_color = Color(0.5, 0.47, 0.38)
	terrain_amplitude = 3.0
	terrain_frequency = 0.0008
	terrain_roughness = 0.85
	setup_terrain()
	_load_city()

func _load_city() -> void:
	var city_scene := preload("res://scenes/game/terrain/AerheimCity.tscn")
	var city := city_scene.instantiate()
	add_child(city)
```

**Step 3: Simplify .tscn files**

Each terrain `.tscn` only needs a root `StaticBody3D` with the script. Remove old `PlaneMesh`, `ShaderMaterial`, and `WorldBoundaryShape3D` sub-resources — terrain is now created programmatically.

**Step 4: Update WorldConstants.gd**

```gdscript
# Remove:
# const GROUND_Y := 0.0
# const MAX_GROUND_Y := 1.0

# Add:
const GROUND_TOLERANCE := 2.0
const MAX_TERRAIN_HEIGHT := 15.0
```

**Step 5: Verify in Godot**

Open the project, enter a zone, and verify:
- Terrain has height variation (collision follows visual mesh)
- Camera and player interact with terrain height

**Step 6: Commit**

```bash
git add client/scripts/world/HeightmapTerrain.gd \
       client/scenes/game/terrain/ \
       client/scripts/world/WorldConstants.gd
git commit -m "feat: replace flat terrain with heightmap-based terrain"
```

---

## Task 5: Scatter System — Resources and Core Logic

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

@export var scenes: Array[PackedScene] = []
@export var density: float = 1.0           # instances per 100m²
@export var height_range: Vector2 = Vector2(0.0, 500.0)
@export var slope_range: Vector2 = Vector2(0.0, 90.0)
@export var rotation_range: float = 360.0  # random Y-rotation degrees
@export var scale_variation: float = 0.2   # ± percentage
@export var use_multimesh: bool = false     # GPU instancing (no collision)
@export var collision_enabled: bool = false
@export var min_spacing: float = 1.0
```

**Step 2: Create ZoneScatter system**

Create `client/scripts/world/ZoneScatter.gd`:

```gdscript
## ZoneScatter.gd
## Places assets on terrain using scatter rules.
## MultiMeshInstance3D for grass/flowers, individual scenes for trees/rocks.
class_name ZoneScatter
extends Node3D

var _terrain: HeightmapTerrain = null
var _rng: RandomNumberGenerator = RandomNumberGenerator.new()


func initialize(terrain: HeightmapTerrain, seed: int) -> void:
	_terrain = terrain
	_rng.seed = seed


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
	for scene in rule.scenes:
		var per_scene_count := count / rule.scenes.size()
		if per_scene_count <= 0:
			continue

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
		for i in range(per_scene_count * 3):
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

		mm.instance_count = placed
		mmi.multimesh = mm
		add_child(mmi)


func _scatter_individual(rule: ScatterRule, area_min: Vector2, area_max: Vector2, count: int) -> void:
	var placed := 0
	for i in range(count * 3):
		if placed >= count:
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
		placed += 1


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

## Task 6: Zone Scatter Configurations

**Files:**
- Modify: `client/scenes/game/terrain/GreenPlainsTerrain.gd`
- Modify: `client/scenes/game/terrain/DarkForestTerrain.gd`
- Modify: `client/scenes/game/terrain/AerheimTerrain.gd`

**Purpose:** Configure each zone with specific scatter rules using MegaKit assets. Remove old primitive shape decorations.

**Step 1: Update GreenPlainsTerrain with scatter**

```gdscript
extends HeightmapTerrain

const SPAWN := Vector3(200.0, 0.0, 200.0)

func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/green_plains.heightmap"
	terrain_base_color = Color(0.28, 0.52, 0.18)
	terrain_valley_color = Color(0.22, 0.42, 0.14)
	terrain_hill_color = Color(0.34, 0.58, 0.22)
	terrain_amplitude = 8.0
	terrain_frequency = 0.0012
	terrain_roughness = 0.9
	setup_terrain()
	_setup_scatter()

func _setup_scatter() -> void:
	var scatter := ZoneScatter.new()
	scatter.initialize(self, 137)
	add_child(scatter)
	var area_min := Vector2(SPAWN.x - 200, SPAWN.z - 200)
	var area_max := Vector2(SPAWN.x + 200, SPAWN.z + 200)

	# Trees
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
	tree_rule.density = 0.3
	tree_rule.scale_variation = 0.25
	tree_rule.collision_enabled = true
	tree_rule.min_spacing = 8.0
	scatter.scatter(tree_rule, area_min, area_max)

	# Grass (MultiMesh)
	var grass_rule := ScatterRule.new()
	grass_rule.scenes = [
		preload("res://assets/nature/models/grass/Grass_Common_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Common_Tall.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wide_Short.tscn"),
	]
	grass_rule.density = 50.0
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

	# Rocks
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

	# Bushes
	var bush_rule := ScatterRule.new()
	bush_rule.scenes = [
		preload("res://assets/nature/models/bushes/Bush_Common_Flowers.tscn"),
		preload("res://assets/nature/models/bushes/Bush_Large_Flowers.tscn"),
	]
	bush_rule.density = 0.15
	scatter.scatter(bush_rule, area_min, area_max)
```

**Step 2: Update DarkForestTerrain similarly**

Use dark forest palette: TwistedTree, TallThick, DeadTree, GiantPine, Fern, Mushrooms. Higher tree density (0.5), no flowers.

**Step 3: Update AerheimTerrain**

Sparse nature for outskirts. CommonTree, Pine, Birch with low density (0.1). Short grass only. Keep AerheimCity loading.

**Step 4: Verify visually in Godot**

Load each zone and check asset placement on terrain.

**Step 5: Commit**

```bash
git add client/scenes/game/terrain/
git commit -m "feat: add zone-specific scatter configurations with MegaKit assets"
```

---

## Task 7: Update GameWorld, Portals, and Player for Heightmap

**Files:**
- Modify: `client/scenes/game/GameWorld.gd`
- Modify: `client/scenes/game/PlayerCharacter.gd`

**Purpose:** Portal Y positions follow terrain height. Player spawns at correct terrain height. Click-to-move uses real terrain collision.

**Step 1: Update portal spawning in GameWorld.gd**

In `_spawn_portals()`, adjust portal Y to terrain height:

```gdscript
var pos: Vector3 = def["position"]
if _terrain and _terrain is HeightmapTerrain:
	pos.y = _terrain.get_height_at(pos.x, pos.z)
portal.position = pos
```

**Step 2: Update player spawn height**

In `_on_zone_data()`, adjust spawn position Y:

```gdscript
var spawn_pos: Vector3 = WorldConstants.ZONE_SPAWNS.get(
	GameState.current_zone_id, WorldConstants.DEFAULT_SPAWN)
if _terrain and _terrain is HeightmapTerrain:
	spawn_pos.y = _terrain.get_height_at(spawn_pos.x, spawn_pos.z)
_player.teleport_to(spawn_pos)
```

**Step 3: Fix click-to-move in PlayerCharacter.gd**

Remove `_click_target.y = 0.0` line in `_try_click_to_move()`. The raycast now hits HeightMapShape3D and returns the correct terrain Y.

**Step 4: Verify**

- Zone transitions: player at correct height
- Portals float at correct height
- Click-to-move works on slopes

**Step 5: Commit**

```bash
git add client/scenes/game/GameWorld.gd client/scenes/game/PlayerCharacter.gd
git commit -m "feat: update portals and player spawning for heightmap terrain"
```

---

## Task 8: Integration Testing and Polish

**Purpose:** End-to-end verification.

**Step 1: Run server tests**

```bash
cd server && ./gradlew test
```

Expected: All tests PASS.

**Step 2: Start full stack and test**

```bash
docker-compose up -d
cd server && ./gradlew :world-service:run &
cd server && ./gradlew :login-service:run &
cd server && ./gradlew :account-service:run &
cd server && ./gradlew :database-service:run &
```

Verify:
- Login -> character select -> enter world
- Player stands on heightmap terrain
- Walk up/down hills smoothly
- Zone transition — correct heights
- MegaKit shaders render correctly (wind on grass/leaves)
- Server log: no excessive position correction spam

**Step 3: Tune scatter parameters**

Adjust density, scale, spacing based on visual results.

**Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete world building system integration"
```

---

## Summary

| Task | Description | Scope |
|------|-------------|-------|
| 1 | Heightmap generator tool | Client tool |
| 2 | Server Y-tolerance update (simple) | Server |
| 3 | Import MegaKit assets | Client |
| 4 | Client heightmap terrain | Client |
| 5 | Scatter system core | Client |
| 6 | Zone scatter configurations | Client |
| 7 | GameWorld + portal + player updates | Client |
| 8 | Integration testing | Full stack |

## Deferred (Future Anti-Cheat Hardening)

- Server-side HeightmapManager with exact heightmap loading
- Exact ground-height validation in MovementHandler
- Monster AI Y-snapping to terrain
- Spawn position Y from heightmap on server
