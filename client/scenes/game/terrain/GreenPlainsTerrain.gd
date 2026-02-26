## GreenPlainsTerrain.gd
## Zone-specific terrain for Green Plains (zone_id=2).
## Flat grassland with gentle visual hills via noise shader.
## Physics collision remains flat at Y=0 (server expects MAX_GROUND_Y=1.0).
extends StaticBody3D


const SPAWN := Vector3(200.0, 0.0, 200.0)


func _ready() -> void:
	_place_decorations()


func _place_decorations() -> void:
	# Scattered trees around spawn
	_place_tree(SPAWN + Vector3(25, 0, 15))
	_place_tree(SPAWN + Vector3(-30, 0, 20))
	_place_tree(SPAWN + Vector3(40, 0, -25))
	_place_tree(SPAWN + Vector3(-15, 0, -40))
	_place_tree(SPAWN + Vector3(50, 0, 35))
	_place_tree(SPAWN + Vector3(-45, 0, -10))
	_place_tree(SPAWN + Vector3(10, 0, 55))
	_place_tree(SPAWN + Vector3(-55, 0, 30))
	_place_tree(SPAWN + Vector3(35, 0, -50))
	_place_tree(SPAWN + Vector3(-20, 0, 45))

	# Scattered rocks
	_place_rock(SPAWN + Vector3(12, 0.3, -8), 0.6)
	_place_rock(SPAWN + Vector3(-18, 0.4, 12), 0.8)
	_place_rock(SPAWN + Vector3(30, 0.25, 5), 0.5)
	_place_rock(SPAWN + Vector3(-8, 0.5, -20), 1.0)
	_place_rock(SPAWN + Vector3(22, 0.3, 28), 0.6)
	_place_rock(SPAWN + Vector3(-35, 0.35, -5), 0.7)
	_place_rock(SPAWN + Vector3(5, 0.2, 35), 0.4)
	_place_rock(SPAWN + Vector3(-25, 0.45, -30), 0.9)


func _place_tree(base_pos: Vector3) -> void:
	# Trunk
	var trunk := StaticBody3D.new()
	trunk.position = base_pos + Vector3(0, 1.5, 0)

	var trunk_mesh := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	cyl.top_radius = 0.25
	cyl.bottom_radius = 0.3
	cyl.height = 3.0
	trunk_mesh.mesh = cyl
	var trunk_mat := StandardMaterial3D.new()
	trunk_mat.albedo_color = Color(0.4, 0.26, 0.13)
	trunk_mesh.material_override = trunk_mat

	var trunk_col := CollisionShape3D.new()
	var trunk_shape := CylinderShape3D.new()
	trunk_shape.radius = 0.3
	trunk_shape.height = 3.0
	trunk_col.shape = trunk_shape

	trunk.add_child(trunk_mesh)
	trunk.add_child(trunk_col)
	add_child(trunk)

	# Canopy
	var canopy := MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = 1.5
	sphere.height = 3.0
	canopy.mesh = sphere
	canopy.position = base_pos + Vector3(0, 3.8, 0)
	var canopy_mat := StandardMaterial3D.new()
	canopy_mat.albedo_color = Color(0.15, 0.45, 0.12)
	canopy.material_override = canopy_mat
	add_child(canopy)


func _place_rock(pos: Vector3, radius: float) -> void:
	var body := StaticBody3D.new()
	body.position = pos

	var mesh_inst := MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = radius
	sphere.height = radius * 2.0
	mesh_inst.mesh = sphere
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.45, 0.43, 0.4)
	mat.roughness = 0.95
	mesh_inst.material_override = mat

	var col := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = radius
	col.shape = shape

	body.add_child(mesh_inst)
	body.add_child(col)
	add_child(body)
