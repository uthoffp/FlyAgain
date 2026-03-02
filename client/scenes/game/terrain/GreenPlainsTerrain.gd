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
