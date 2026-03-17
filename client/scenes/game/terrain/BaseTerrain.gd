## BaseTerrain.gd
## Simple flat terrain placeholder for Phase 1.4.
## A large flat plane at Y=0 covering the world boundaries.
extends StaticBody3D


func _ready() -> void:
	_apply_ground_material()


func get_height_at(_world_x: float, _world_z: float) -> float:
	return 0.0


func _apply_ground_material() -> void:
	var mesh_instance: MeshInstance3D = $MeshInstance3D
	if not mesh_instance:
		return
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.28, 0.52, 0.18)  # Grass green
	mat.roughness = 0.9
	mesh_instance.material_override = mat
