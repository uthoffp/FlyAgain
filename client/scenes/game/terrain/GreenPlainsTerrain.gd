## GreenPlainsTerrain.gd
## Zone-specific terrain for Green Plains (zone_id=2).
## Lush meadows with common trees, cherry blossoms, wildflowers, and grass.
extends HeightmapTerrain

const SPAWN := Vector3(200.0, 0.0, 200.0)


func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/green_plains.heightmap"
	terrain_base_color = Color(0.28, 0.52, 0.18)
	terrain_valley_color = Color(0.22, 0.42, 0.14)
	terrain_hill_color = Color(0.34, 0.58, 0.22)
	terrain_amplitude = 8.0
	terrain_roughness = 0.9
	setup_terrain()
	_setup_scatter()


func _setup_scatter() -> void:
	var scatter := ZoneScatter.new()
	scatter.initialize(self, 137)
	add_child(scatter)
	var area_min := Vector2(SPAWN.x - 200, SPAWN.z - 200)
	var area_max := Vector2(SPAWN.x + 200, SPAWN.z + 200)

	# Trees — CommonTree, Birch, CherryBlossom
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

	# Grass (MultiMesh) — all grass types
	var grass_rule := ScatterRule.new()
	grass_rule.scenes = [
		preload("res://assets/nature/models/grass/Grass_Common_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Common_Tall.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wide_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wide_Tall.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wispy_Short.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wispy_Tall.tscn"),
		preload("res://assets/nature/models/grass/Grass_Wheat.tscn"),
	]
	grass_rule.density = 50.0
	grass_rule.use_multimesh = true
	grass_rule.scale_variation = 0.3
	scatter.scatter(grass_rule, area_min, area_max)

	# Ground cover — Clover, Fern (MultiMesh)
	var ground_rule := ScatterRule.new()
	ground_rule.scenes = [
		preload("res://assets/nature/models/ground_cover/Clover_1.tscn"),
		preload("res://assets/nature/models/ground_cover/Clover_2.tscn"),
		preload("res://assets/nature/models/ground_cover/Fern_1.tscn"),
		preload("res://assets/nature/models/ground_cover/Fern_2.tscn"),
	]
	ground_rule.density = 8.0
	ground_rule.use_multimesh = true
	ground_rule.scale_variation = 0.2
	scatter.scatter(ground_rule, area_min, area_max)

	# Flowers (MultiMesh) — all flower types
	var flower_rule := ScatterRule.new()
	flower_rule.scenes = [
		preload("res://assets/nature/models/flowers/Flower_1_Group.tscn"),
		preload("res://assets/nature/models/flowers/Flower_1_Single.tscn"),
		preload("res://assets/nature/models/flowers/Flower_2_Group.tscn"),
		preload("res://assets/nature/models/flowers/Flower_2_Single.tscn"),
		preload("res://assets/nature/models/flowers/Flower_3_Group.tscn"),
		preload("res://assets/nature/models/flowers/Flower_3_Single.tscn"),
		preload("res://assets/nature/models/flowers/Flower_4_Group.tscn"),
		preload("res://assets/nature/models/flowers/Flower_4_Single.tscn"),
		preload("res://assets/nature/models/flowers/Flower_6.tscn"),
		preload("res://assets/nature/models/flowers/Flower_6_2.tscn"),
		preload("res://assets/nature/models/flowers/Flower_7_Group.tscn"),
		preload("res://assets/nature/models/flowers/Flower_7_Single.tscn"),
	]
	flower_rule.density = 5.0
	flower_rule.use_multimesh = true
	flower_rule.scale_variation = 0.2
	scatter.scatter(flower_rule, area_min, area_max)

	# Rocks — Medium rocks, Pebbles
	var rock_rule := ScatterRule.new()
	rock_rule.scenes = [
		preload("res://assets/nature/models/rocks/Rock_Medium_1.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Medium_2.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Medium_3.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Medium_4.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Round_1.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Round_2.tscn"),
		preload("res://assets/nature/models/rocks/Pebble_Round_3.tscn"),
	]
	rock_rule.density = 0.2
	rock_rule.collision_enabled = true
	rock_rule.scale_variation = 0.3
	scatter.scatter(rock_rule, area_min, area_max)

	# Bushes — flower variants
	var bush_rule := ScatterRule.new()
	bush_rule.scenes = [
		preload("res://assets/nature/models/bushes/Bush_Common_Flowers.tscn"),
		preload("res://assets/nature/models/bushes/Bush_Large_Flowers.tscn"),
	]
	bush_rule.density = 0.15
	bush_rule.scale_variation = 0.2
	scatter.scatter(bush_rule, area_min, area_max)
