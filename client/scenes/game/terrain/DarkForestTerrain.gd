## DarkForestTerrain.gd
## Zone-specific terrain for Dark Forest (zone_id=3).
## Dense, dark canopy with mushrooms, ferns, and scattered petals.
## Terrain sculpted via Terrain3D plugin.
extends Node3D

const SPAWN := Vector3(100.0, 0.0, 100.0)
const TERRAIN_DATA_DIR := "res://assets/terrain_data/dark_forest/"

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
	scatter.initialize(self, 256)
	add_child(scatter)
	var area_min := Vector2(SPAWN.x - 200, SPAWN.z - 200)
	var area_max := Vector2(SPAWN.x + 200, SPAWN.z + 200)

	# Ground cover — Fern, Mushrooms (MultiMesh)
	var ground_rule := ScatterRule.new()
	ground_rule.scenes = [
		preload("res://assets/nature/models/ground_cover/Fern_1.tscn"),
		preload("res://assets/nature/models/ground_cover/Fern_2.tscn"),
		preload("res://assets/nature/models/ground_cover/Mushroom_Common.tscn"),
		preload("res://assets/nature/models/ground_cover/Mushroom_Laetiporus.tscn"),
		preload("res://assets/nature/models/ground_cover/Mushroom_Oyster.tscn"),
		preload("res://assets/nature/models/ground_cover/Mushroom_RedCap.tscn"),
	]
	ground_rule.density = 12.0
	ground_rule.use_multimesh = true
	ground_rule.scale_variation = 0.25
	scatter.scatter(ground_rule, area_min, area_max)

	# Petals only (MultiMesh) — no flowers, dark forest atmosphere
	var petal_rule := ScatterRule.new()
	petal_rule.scenes = [
		preload("res://assets/nature/models/ground_cover/Petal_1.tscn"),
		preload("res://assets/nature/models/ground_cover/Petal_2.tscn"),
		preload("res://assets/nature/models/ground_cover/Petal_3.tscn"),
		preload("res://assets/nature/models/ground_cover/Petal_4.tscn"),
		preload("res://assets/nature/models/ground_cover/Petal_5.tscn"),
		preload("res://assets/nature/models/ground_cover/Petal_6.tscn"),
	]
	petal_rule.density = 3.0
	petal_rule.use_multimesh = true
	petal_rule.scale_variation = 0.2
	scatter.scatter(petal_rule, area_min, area_max)
