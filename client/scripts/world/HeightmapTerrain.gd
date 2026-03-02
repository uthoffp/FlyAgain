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
	# Resolve the heightmap path: if it starts with "res://" we globalize
	# the project root and resolve relative segments so we can reach
	# shared/heightmaps/ which lives outside the Godot project tree.
	var resolved_path := heightmap_path
	if heightmap_path.begins_with("res://"):
		var project_root := ProjectSettings.globalize_path("res://")
		var relative := heightmap_path.substr(len("res://"))
		resolved_path = project_root.path_join(relative)
		# Normalize ".." segments via simplify_path
		resolved_path = resolved_path.simplify_path()

	var file := FileAccess.open(resolved_path, FileAccess.READ)
	if not file:
		push_warning("Heightmap not found: %s — using flat terrain" % resolved_path)
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
	shader_mat.set_shader_parameter("base_color", Vector3(terrain_base_color.r, terrain_base_color.g, terrain_base_color.b))
	shader_mat.set_shader_parameter("valley_color", Vector3(terrain_valley_color.r, terrain_valley_color.g, terrain_valley_color.b))
	shader_mat.set_shader_parameter("hill_color", Vector3(terrain_hill_color.r, terrain_hill_color.g, terrain_hill_color.b))
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
