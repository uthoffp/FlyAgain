## AerheimTerrain.gd
## Zone-specific terrain for Aerheim (zone_id=1).
## Sparse outskirts vegetation around the city with low grass and clover.
## Terrain sculpted via Terrain3D plugin.
extends Node3D

const SPAWN := Vector3(500.0, 0.0, 500.0)
const TERRAIN_DATA_DIR := "res://assets/terrain_data/aerheim/"

var _terrain3d: Terrain3D = null


func _ready() -> void:
	_find_or_create_terrain()
	_setup_scatter()


func _find_or_create_terrain() -> void:
	for child in get_children():
		if child is Terrain3D:
			_terrain3d = child
			return
	_terrain3d = Terrain3D.new()
	_terrain3d.name = "Terrain3D"
	_terrain3d.data_directory = TERRAIN_DATA_DIR
	_terrain3d.assets = Terrain3DAssets.new()
	add_child(_terrain3d)


func get_height_at(world_x: float, world_z: float) -> float:
	if _terrain3d and _terrain3d.data:
		var h: float = _terrain3d.data.get_height(Vector3(world_x, 0.0, world_z))
		if is_finite(h):
			return h
	return 0.0


func _setup_scatter() -> void:
	var scatter := ZoneScatter.new()
	scatter.initialize(self, 42)
	add_child(scatter)
	var area_min := Vector2(SPAWN.x - 200, SPAWN.z - 200)
	var area_max := Vector2(SPAWN.x + 200, SPAWN.z + 200)

	# Grass (MultiMesh) — short grass only
	var grass_rule := ScatterRule.new()
	grass_rule.scenes = [
		preload("res://assets/nature/models/grass/Grass_Common_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wide_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wispy_Short.tscn"),
	]
	grass_rule.density = 8.0
	grass_rule.use_multimesh = true
	grass_rule.scale_variation = 0.2
	grass_rule.visibility_range = 80.0
	scatter.scatter(grass_rule, area_min, area_max)

	# Ground cover — Clover (MultiMesh)
	var ground_rule := ScatterRule.new()
	ground_rule.scenes = [
		preload("res://assets/nature/models/ground_cover/Clover_1.tscn"),
		preload("res://assets/nature/models/ground_cover/Clover_2.tscn"),
	]
	ground_rule.density = 2.0
	ground_rule.use_multimesh = true
	ground_rule.scale_variation = 0.15
	ground_rule.visibility_range = 100.0
	scatter.scatter(ground_rule, area_min, area_max)

	# Flowers (MultiMesh) — sparse selection
	var flower_rule := ScatterRule.new()
	flower_rule.scenes = [
		preload("res://assets/nature/models/flowers/Flower_1_Single.tscn"),
		preload("res://assets/nature/models/flowers/Flower_3_Single.tscn"),
		preload("res://assets/nature/models/flowers/Flower_7_Single.tscn"),
	]
	flower_rule.density = 0.8
	flower_rule.use_multimesh = true
	flower_rule.scale_variation = 0.2
	flower_rule.visibility_range = 100.0
	scatter.scatter(flower_rule, area_min, area_max)
