## AerheimTerrain.gd
## Zone-specific terrain for Aerheim (zone_id=1).
## Flat stone/earth ground with city geometry loaded as child scene.
extends StaticBody3D


func _ready() -> void:
	_apply_ground_material()
	_load_city()


func _apply_ground_material() -> void:
	var mesh_instance: MeshInstance3D = $MeshInstance3D
	if not mesh_instance:
		return
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.45, 0.42, 0.35)  # Light stone/earth
	mat.roughness = 0.85
	mesh_instance.material_override = mat


func _load_city() -> void:
	var city_scene := preload("res://scenes/game/terrain/AerheimCity.tscn")
	var city := city_scene.instantiate()
	add_child(city)
