## AerheimTerrain.gd
## Zone-specific terrain for Aerheim (zone_id=1).
extends HeightmapTerrain


func _ready() -> void:
	heightmap_path = "res://../../shared/heightmaps/aerheim.heightmap"
	terrain_base_color = Color(0.45, 0.42, 0.35)
	terrain_valley_color = Color(0.38, 0.35, 0.28)
	terrain_hill_color = Color(0.5, 0.47, 0.38)
	terrain_amplitude = 3.0
	terrain_roughness = 0.85
	setup_terrain()
	_load_city()


func _load_city() -> void:
	var city_scene := preload("res://scenes/game/terrain/AerheimCity.tscn")
	var city := city_scene.instantiate()
	add_child(city)
