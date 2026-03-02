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
