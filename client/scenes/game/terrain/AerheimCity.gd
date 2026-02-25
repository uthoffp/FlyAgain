## AerheimCity.gd
## Placeholder city geometry for Aerheim (zone_id=1).
## Procedurally places buildings, walls, plaza, and NPC markers
## around the town center at (500, 0, 500).
extends Node3D


const CENTER := Vector3(500.0, 0.0, 500.0)
const WALL_HALF := 60.0  # half-size of the walled area (120x120)
const GATE_WIDTH := 8.0
const WALL_HEIGHT := 4.0
const WALL_THICKNESS := 1.0


func _ready() -> void:
	_build_plaza()
	_build_walls()
	_build_buildings()
	_place_npc_markers()


# ---- Plaza ----

func _build_plaza() -> void:
	var plaza := MeshInstance3D.new()
	var box := BoxMesh.new()
	box.size = Vector3(30.0, 0.04, 30.0)
	plaza.mesh = box
	plaza.position = CENTER + Vector3(0, 0.02, 0)
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.7, 0.65, 0.55)
	mat.roughness = 0.85
	plaza.material_override = mat
	add_child(plaza)


# ---- Walls ----

func _build_walls() -> void:
	var min_x := CENTER.x - WALL_HALF
	var max_x := CENTER.x + WALL_HALF
	var min_z := CENTER.z - WALL_HALF
	var max_z := CENTER.z + WALL_HALF
	var gate_half := GATE_WIDTH / 2.0
	var seg_len_h := WALL_HALF - gate_half  # horizontal segment length
	var seg_len_v := WALL_HALF - gate_half  # vertical segment length

	# North wall (z = min_z) — two segments with gate in the middle
	_place_wall_segment(
		Vector3(min_x + seg_len_h / 2.0, WALL_HEIGHT / 2.0, min_z),
		Vector3(seg_len_h, WALL_HEIGHT, WALL_THICKNESS))
	_place_wall_segment(
		Vector3(max_x - seg_len_h / 2.0, WALL_HEIGHT / 2.0, min_z),
		Vector3(seg_len_h, WALL_HEIGHT, WALL_THICKNESS))

	# South wall (z = max_z)
	_place_wall_segment(
		Vector3(min_x + seg_len_h / 2.0, WALL_HEIGHT / 2.0, max_z),
		Vector3(seg_len_h, WALL_HEIGHT, WALL_THICKNESS))
	_place_wall_segment(
		Vector3(max_x - seg_len_h / 2.0, WALL_HEIGHT / 2.0, max_z),
		Vector3(seg_len_h, WALL_HEIGHT, WALL_THICKNESS))

	# West wall (x = min_x)
	_place_wall_segment(
		Vector3(min_x, WALL_HEIGHT / 2.0, min_z + seg_len_v / 2.0),
		Vector3(WALL_THICKNESS, WALL_HEIGHT, seg_len_v))
	_place_wall_segment(
		Vector3(min_x, WALL_HEIGHT / 2.0, max_z - seg_len_v / 2.0),
		Vector3(WALL_THICKNESS, WALL_HEIGHT, seg_len_v))

	# East wall (x = max_x)
	_place_wall_segment(
		Vector3(max_x, WALL_HEIGHT / 2.0, min_z + seg_len_v / 2.0),
		Vector3(WALL_THICKNESS, WALL_HEIGHT, seg_len_v))
	_place_wall_segment(
		Vector3(max_x, WALL_HEIGHT / 2.0, max_z - seg_len_v / 2.0),
		Vector3(WALL_THICKNESS, WALL_HEIGHT, seg_len_v))


func _place_wall_segment(pos: Vector3, size: Vector3) -> void:
	var body := StaticBody3D.new()
	body.position = pos

	var mesh_inst := MeshInstance3D.new()
	var box := BoxMesh.new()
	box.size = size
	mesh_inst.mesh = box
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.55, 0.5, 0.42)
	mat.roughness = 0.9
	mesh_inst.material_override = mat

	var col := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = size
	col.shape = shape

	body.add_child(mesh_inst)
	body.add_child(col)
	add_child(body)


# ---- Buildings ----

const BUILDINGS: Array = [
	# Inn / Tavern — east of plaza
	{"name": "Inn", "offset": Vector3(15, 0, -12),
	 "size": Vector3(8, 5, 6), "color": Color(0.55, 0.35, 0.15)},
	# Shop row — west of plaza
	{"name": "Shop_Weapons", "offset": Vector3(-15, 0, 12),
	 "size": Vector3(5, 4, 4), "color": Color(0.6, 0.5, 0.3)},
	{"name": "Shop_Armor", "offset": Vector3(-15, 0, 18),
	 "size": Vector3(5, 4, 4), "color": Color(0.6, 0.5, 0.3)},
	{"name": "Shop_Potions", "offset": Vector3(-15, 0, 24),
	 "size": Vector3(5, 3.5, 4), "color": Color(0.6, 0.5, 0.3)},
	# Guild Hall — southeast, largest building
	{"name": "GuildHall", "offset": Vector3(20, 0, 20),
	 "size": Vector3(10, 7, 8), "color": Color(0.55, 0.55, 0.5)},
	# Residential — northwest
	{"name": "House_1", "offset": Vector3(-30, 0, -30),
	 "size": Vector3(4, 3, 4), "color": Color(0.5, 0.38, 0.2)},
	{"name": "House_2", "offset": Vector3(-22, 0, -32),
	 "size": Vector3(5, 3.5, 4), "color": Color(0.5, 0.38, 0.2)},
	# Residential — northeast
	{"name": "House_3", "offset": Vector3(30, 0, -30),
	 "size": Vector3(4, 3, 5), "color": Color(0.5, 0.38, 0.2)},
	{"name": "House_4", "offset": Vector3(40, 0, -25),
	 "size": Vector3(5, 3, 4), "color": Color(0.5, 0.38, 0.2)},
	# Residential — southwest
	{"name": "House_5", "offset": Vector3(-40, 0, 35),
	 "size": Vector3(4, 4, 4), "color": Color(0.5, 0.38, 0.2)},
	# Residential — near south gate
	{"name": "House_6", "offset": Vector3(35, 0, 40),
	 "size": Vector3(5, 3, 5), "color": Color(0.5, 0.38, 0.2)},
]


func _build_buildings() -> void:
	for bld in BUILDINGS:
		var offset: Vector3 = bld["offset"]
		var size: Vector3 = bld["size"]
		var color: Color = bld["color"]
		var pos := CENTER + offset + Vector3(0, size.y / 2.0, 0)
		_place_building(pos, size, color)


func _place_building(pos: Vector3, size: Vector3, color: Color) -> void:
	var body := StaticBody3D.new()
	body.position = pos

	var mesh_inst := MeshInstance3D.new()
	var box := BoxMesh.new()
	box.size = size
	mesh_inst.mesh = box
	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mat.roughness = 0.85
	mesh_inst.material_override = mat

	var col := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = size
	col.shape = shape

	# Simple roof — flat box on top, slightly wider
	var roof := MeshInstance3D.new()
	var roof_box := BoxMesh.new()
	roof_box.size = Vector3(size.x + 0.6, 0.3, size.z + 0.6)
	roof.mesh = roof_box
	roof.position = Vector3(0, size.y / 2.0 + 0.15, 0)
	var roof_mat := StandardMaterial3D.new()
	roof_mat.albedo_color = Color(color.r * 0.7, color.g * 0.6, color.b * 0.5)
	roof.material_override = roof_mat

	body.add_child(mesh_inst)
	body.add_child(col)
	body.add_child(roof)
	add_child(body)


# ---- NPC Markers ----

func _place_npc_markers() -> void:
	_place_marker("NPC_Innkeeper", CENTER + Vector3(13, 0, -10))
	_place_marker("NPC_Shopkeeper_Weapons", CENTER + Vector3(-13, 0, 14))
	_place_marker("NPC_Shopkeeper_Armor", CENTER + Vector3(-13, 0, 20))
	_place_marker("NPC_Shopkeeper_Potions", CENTER + Vector3(-13, 0, 26))
	_place_marker("NPC_GuildMaster", CENTER + Vector3(18, 0, 18))
	_place_marker("NPC_Guard_North", CENTER + Vector3(0, 0, -WALL_HALF + 2))
	_place_marker("NPC_Guard_South", CENTER + Vector3(0, 0, WALL_HALF - 2))
	_place_marker("NPC_Guard_West", CENTER + Vector3(-WALL_HALF + 2, 0, 0))
	_place_marker("NPC_Guard_East", CENTER + Vector3(WALL_HALF - 2, 0, 0))


func _place_marker(marker_name: String, pos: Vector3) -> void:
	var marker := Node3D.new()
	marker.name = marker_name
	marker.position = pos
	add_child(marker)
