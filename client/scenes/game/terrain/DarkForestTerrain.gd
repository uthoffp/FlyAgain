## DarkForestTerrain.gd
## Zone-specific terrain for Dark Forest (zone_id=3).
## Dense, dark canopy with twisted trees, mushrooms, ferns, and scattered petals.
extends HeightmapTerrain

const SPAWN := Vector3(100.0, 0.0, 100.0)


func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/dark_forest.heightmap"
	terrain_base_color = Color(0.15, 0.2, 0.1)
	terrain_valley_color = Color(0.1, 0.15, 0.08)
	terrain_hill_color = Color(0.2, 0.25, 0.14)
	terrain_amplitude = 12.0
	terrain_roughness = 0.95
	setup_terrain()
	_setup_scatter()


func _setup_scatter() -> void:
	var scatter := ZoneScatter.new()
	scatter.initialize(self, 256)
	add_child(scatter)
	var area_min := Vector2(SPAWN.x - 200, SPAWN.z - 200)
	var area_max := Vector2(SPAWN.x + 200, SPAWN.z + 200)

	# Trees — TwistedTree, TallThick, DeadTree, GiantPine (high density)
	var tree_rule := ScatterRule.new()
	tree_rule.scenes = [
		preload("res://assets/nature/models/trees/TwistedTree_1.tscn"),
		preload("res://assets/nature/models/trees/TwistedTree_2.tscn"),
		preload("res://assets/nature/models/trees/TwistedTree_3.tscn"),
		preload("res://assets/nature/models/trees/TallThick_1.tscn"),
		preload("res://assets/nature/models/trees/TallThick_2.tscn"),
		preload("res://assets/nature/models/trees/TallThick_3.tscn"),
		preload("res://assets/nature/models/trees/DeadTree_1.tscn"),
		preload("res://assets/nature/models/trees/DeadTree_2.tscn"),
		preload("res://assets/nature/models/trees/GiantPine_1.tscn"),
		preload("res://assets/nature/models/trees/GiantPine_2.tscn"),
		preload("res://assets/nature/models/trees/GiantPine_3.tscn"),
	]
	tree_rule.density = 0.5
	tree_rule.scale_variation = 0.3
	tree_rule.collision_enabled = true
	tree_rule.min_spacing = 6.0
	scatter.scatter(tree_rule, area_min, area_max)

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

	# Rocks — Big rocks + Medium rocks
	var rock_rule := ScatterRule.new()
	rock_rule.scenes = [
		preload("res://assets/nature/models/rocks/Rock_Big_1.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Big_2.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Medium_1.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Medium_2.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Medium_3.tscn"),
		preload("res://assets/nature/models/rocks/Rock_Medium_4.tscn"),
	]
	rock_rule.density = 0.25
	rock_rule.collision_enabled = true
	rock_rule.scale_variation = 0.3
	scatter.scatter(rock_rule, area_min, area_max)

	# Bushes — Bush_Common (no flowers), Bush_Long
	var bush_rule := ScatterRule.new()
	bush_rule.scenes = [
		preload("res://assets/nature/models/bushes/Bush_Common.tscn"),
		preload("res://assets/nature/models/bushes/Bush_Long_1.tscn"),
		preload("res://assets/nature/models/bushes/Bush_Long_2.tscn"),
	]
	bush_rule.density = 0.2
	bush_rule.scale_variation = 0.2
	scatter.scatter(bush_rule, area_min, area_max)
