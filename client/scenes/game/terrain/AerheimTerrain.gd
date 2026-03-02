## AerheimTerrain.gd
## Zone-specific terrain for Aerheim (zone_id=1).
## Sparse outskirts vegetation around the city with rock paths and low grass.
extends HeightmapTerrain

const SPAWN := Vector3(500.0, 0.0, 500.0)


func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/aerheim.heightmap"
	terrain_base_color = Color(0.45, 0.42, 0.35)
	terrain_valley_color = Color(0.38, 0.35, 0.28)
	terrain_hill_color = Color(0.5, 0.47, 0.38)
	terrain_amplitude = 3.0
	terrain_roughness = 0.85
	setup_terrain()
	_load_city()
	_setup_scatter()


func _load_city() -> void:
	var city_scene := preload("res://scenes/game/terrain/AerheimCity.tscn")
	var city := city_scene.instantiate()
	add_child(city)


func _setup_scatter() -> void:
	var scatter := ZoneScatter.new()
	scatter.initialize(self, 42)
	add_child(scatter)
	var area_min := Vector2(SPAWN.x - 200, SPAWN.z - 200)
	var area_max := Vector2(SPAWN.x + 200, SPAWN.z + 200)

	# Trees — CommonTree, Pine, Birch (sparse)
	var tree_rule := ScatterRule.new()
	tree_rule.scenes = [
		preload("res://assets/nature/models/trees/CommonTree_1.tscn"),
		preload("res://assets/nature/models/trees/CommonTree_2.tscn"),
		preload("res://assets/nature/models/trees/Pine_1.tscn"),
		preload("res://assets/nature/models/trees/Pine_2.tscn"),
		preload("res://assets/nature/models/trees/Birch_1.tscn"),
		preload("res://assets/nature/models/trees/Birch_2.tscn"),
	]
	tree_rule.density = 0.1
	tree_rule.scale_variation = 0.2
	tree_rule.collision_enabled = true
	tree_rule.min_spacing = 10.0
	scatter.scatter(tree_rule, area_min, area_max)

	# Grass (MultiMesh) — short grass only
	var grass_rule := ScatterRule.new()
	grass_rule.scenes = [
		preload("res://assets/nature/models/grass/Grass_Common_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wide_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wispy_Short.tscn"),
	]
	grass_rule.density = 20.0
	grass_rule.use_multimesh = true
	grass_rule.scale_variation = 0.2
	scatter.scatter(grass_rule, area_min, area_max)

	# Ground cover — Clover (MultiMesh)
	var ground_rule := ScatterRule.new()
	ground_rule.scenes = [
		preload("res://assets/nature/models/ground_cover/Clover_1.tscn"),
		preload("res://assets/nature/models/ground_cover/Clover_2.tscn"),
	]
	ground_rule.density = 4.0
	ground_rule.use_multimesh = true
	ground_rule.scale_variation = 0.15
	scatter.scatter(ground_rule, area_min, area_max)

	# Flowers (MultiMesh) — sparse selection
	var flower_rule := ScatterRule.new()
	flower_rule.scenes = [
		preload("res://assets/nature/models/flowers/Flower_1_Single.tscn"),
		preload("res://assets/nature/models/flowers/Flower_3_Single.tscn"),
		preload("res://assets/nature/models/flowers/Flower_7_Single.tscn"),
	]
	flower_rule.density = 1.5
	flower_rule.use_multimesh = true
	flower_rule.scale_variation = 0.2
	scatter.scatter(flower_rule, area_min, area_max)

	# Rocks — Rock paths, Pebbles
	var rock_rule := ScatterRule.new()
	rock_rule.scenes = [
		preload("res://assets/nature/models/rocks/RockPath_Round_Small_1.tscn"),
		preload("res://assets/nature/models/rocks/RockPath_Round_Small_2.tscn"),
		preload("res://assets/nature/models/rocks/RockPath_Round_Small_3.tscn"),
		preload("res://assets/nature/models/rocks/RockPath_Square_Small_1.tscn"),
		preload("res://assets/nature/models/rocks/RockPath_Square_Small_2.tscn"),
		preload("res://assets/nature/models/rocks/RockPath_Square_Small_3.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Round_1.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Round_2.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Square_1.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Square_2.tscn"),
	]
	rock_rule.density = 0.15
	rock_rule.collision_enabled = true
	rock_rule.scale_variation = 0.2
	scatter.scatter(rock_rule, area_min, area_max)

	# Bushes — Bush_Common, Bush_Large
	var bush_rule := ScatterRule.new()
	bush_rule.scenes = [
		preload("res://assets/nature/models/bushes/Bush_Common.tscn"),
		preload("res://assets/nature/models/bushes/Bush_Large.tscn"),
	]
	bush_rule.density = 0.1
	bush_rule.scale_variation = 0.15
	scatter.scatter(bush_rule, area_min, area_max)
