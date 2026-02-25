## DarkForestTerrain.gd
## Zone-specific terrain for Dark Forest (zone_id=3).
## Dark, rugged atmosphere with dense tree placement.
## Physics collision remains flat at Y=0.
extends StaticBody3D


const SPAWN := Vector3(100.0, 0.0, 100.0)


func _ready() -> void:
	_place_decorations()


func _place_decorations() -> void:
	# Dense dark trees around spawn
	_place_dark_tree(SPAWN + Vector3(15, 0, 10))
	_place_dark_tree(SPAWN + Vector3(-20, 0, 15))
	_place_dark_tree(SPAWN + Vector3(30, 0, -20))
	_place_dark_tree(SPAWN + Vector3(-10, 0, -30))
	_place_dark_tree(SPAWN + Vector3(40, 0, 25))
	_place_dark_tree(SPAWN + Vector3(-35, 0, -5))
	_place_dark_tree(SPAWN + Vector3(8, 0, 40))
	_place_dark_tree(SPAWN + Vector3(-40, 0, 20))
	_place_dark_tree(SPAWN + Vector3(25, 0, -35))
	_place_dark_tree(SPAWN + Vector3(-15, 0, 35))
	_place_dark_tree(SPAWN + Vector3(50, 0, -10))
	_place_dark_tree(SPAWN + Vector3(-50, 0, -25))
	_place_dark_tree(SPAWN + Vector3(5, 0, -45))
	_place_dark_tree(SPAWN + Vector3(-28, 0, 45))

	# Large mossy rocks
	_place_rock(SPAWN + Vector3(10, 0.5, -6), 1.0)
	_place_rock(SPAWN + Vector3(-22, 0.6, 8), 1.2)
	_place_rock(SPAWN + Vector3(35, 0.4, 12), 0.8)
	_place_rock(SPAWN + Vector3(-5, 0.7, -18), 1.4)
	_place_rock(SPAWN + Vector3(18, 0.3, 30), 0.6)
	_place_rock(SPAWN + Vector3(-30, 0.55, -15), 1.1)


func _place_dark_tree(base_pos: Vector3) -> void:
	# Tall dark trunk
	var trunk := StaticBody3D.new()
	trunk.position = base_pos + Vector3(0, 2.0, 0)

	var trunk_mesh := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	cyl.top_radius = 0.2
	cyl.bottom_radius = 0.35
	cyl.height = 4.0
	trunk_mesh.mesh = cyl
	var trunk_mat := StandardMaterial3D.new()
	trunk_mat.albedo_color = Color(0.25, 0.15, 0.08)
	trunk_mesh.material_override = trunk_mat

	var trunk_col := CollisionShape3D.new()
	var trunk_shape := CylinderShape3D.new()
	trunk_shape.radius = 0.35
	trunk_shape.height = 4.0
	trunk_col.shape = trunk_shape

	trunk.add_child(trunk_mesh)
	trunk.add_child(trunk_col)
	add_child(trunk)

	# Dark canopy
	var canopy := MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = 1.8
	sphere.height = 3.6
	canopy.mesh = sphere
	canopy.position = base_pos + Vector3(0, 4.8, 0)
	var canopy_mat := StandardMaterial3D.new()
	canopy_mat.albedo_color = Color(0.08, 0.25, 0.06)
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
	mat.albedo_color = Color(0.3, 0.32, 0.28)
	mat.roughness = 0.95
	mesh_inst.material_override = mat

	var col := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = radius
	col.shape = shape

	body.add_child(mesh_inst)
	body.add_child(col)
	add_child(body)
