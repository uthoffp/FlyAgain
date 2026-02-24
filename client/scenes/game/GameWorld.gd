## GameWorld.gd
## Root scene for the 3D game world.
## Orchestrates: terrain, local player, remote entities, zone data.
extends Node3D


const PlayerCharacterScene := preload("res://scenes/game/PlayerCharacter.tscn")
const BaseTerrainScene := preload("res://scenes/game/terrain/BaseTerrain.tscn")

var _entity_factory: EntityFactory = EntityFactory.new()
var _player: CharacterBody3D = null
var _terrain: Node3D = null

@onready var _entities_root: Node3D = $Entities


func _ready() -> void:
	_entity_factory.initialize(_entities_root)
	_connect_signals()
	_setup_terrain()
	_setup_static_objects()
	_spawn_local_player()
	_send_enter_world()


func _exit_tree() -> void:
	_disconnect_signals()


func _connect_signals() -> void:
	NetworkManager.zone_data_received.connect(_on_zone_data)
	NetworkManager.entity_spawned.connect(_on_entity_spawned)
	NetworkManager.entity_despawned.connect(_on_entity_despawned)
	NetworkManager.entity_position_updated.connect(_on_entity_position_updated)
	NetworkManager.world_disconnected.connect(_on_world_disconnected)


func _disconnect_signals() -> void:
	if NetworkManager.zone_data_received.is_connected(_on_zone_data):
		NetworkManager.zone_data_received.disconnect(_on_zone_data)
	if NetworkManager.entity_spawned.is_connected(_on_entity_spawned):
		NetworkManager.entity_spawned.disconnect(_on_entity_spawned)
	if NetworkManager.entity_despawned.is_connected(_on_entity_despawned):
		NetworkManager.entity_despawned.disconnect(_on_entity_despawned)
	if NetworkManager.entity_position_updated.is_connected(_on_entity_position_updated):
		NetworkManager.entity_position_updated.disconnect(_on_entity_position_updated)
	if NetworkManager.world_disconnected.is_connected(_on_world_disconnected):
		NetworkManager.world_disconnected.disconnect(_on_world_disconnected)


func _setup_terrain() -> void:
	_terrain = BaseTerrainScene.instantiate()
	add_child(_terrain)


func _setup_static_objects() -> void:
	var spawn := WorldConstants.DEFAULT_SPAWN

	# Wooden crates near spawn
	_place_box(spawn + Vector3(6, 0.5, 3), Vector3(1, 1, 1), Color(0.55, 0.35, 0.15))
	_place_box(spawn + Vector3(7, 0.5, 3.5), Vector3(0.8, 0.8, 0.8), Color(0.5, 0.3, 0.12))
	_place_box(spawn + Vector3(6.4, 1.4, 3.2), Vector3(0.7, 0.7, 0.7), Color(0.6, 0.38, 0.18))

	# Stone pillars
	_place_cylinder(spawn + Vector3(-8, 1.5, 5), 0.5, 3.0, Color(0.6, 0.6, 0.58))
	_place_cylinder(spawn + Vector3(-8, 1.5, -5), 0.5, 3.0, Color(0.6, 0.6, 0.58))
	_place_cylinder(spawn + Vector3(8, 1.5, -5), 0.5, 3.0, Color(0.55, 0.55, 0.53))

	# Rocks scattered around
	_place_sphere(spawn + Vector3(12, 0.4, -8), 0.8, Color(0.45, 0.43, 0.4))
	_place_sphere(spawn + Vector3(-15, 0.3, 10), 0.6, Color(0.5, 0.48, 0.44))
	_place_sphere(spawn + Vector3(3, 0.25, -14), 0.5, Color(0.4, 0.38, 0.35))
	_place_sphere(spawn + Vector3(-5, 0.5, -12), 1.0, Color(0.48, 0.46, 0.42))

	# Tree trunks (cylinder) with canopy (sphere)
	_place_tree(spawn + Vector3(-12, 0, -3))
	_place_tree(spawn + Vector3(15, 0, 8))
	_place_tree(spawn + Vector3(-3, 0, 18))
	_place_tree(spawn + Vector3(20, 0, -15))


func _place_box(pos: Vector3, size: Vector3, color: Color) -> void:
	var body := StaticBody3D.new()
	body.position = pos

	var mesh_inst := MeshInstance3D.new()
	var box := BoxMesh.new()
	box.size = size
	mesh_inst.mesh = box

	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mesh_inst.material_override = mat

	var col := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = size
	col.shape = shape

	body.add_child(mesh_inst)
	body.add_child(col)
	add_child(body)


func _place_cylinder(pos: Vector3, radius: float, height: float, color: Color) -> void:
	var body := StaticBody3D.new()
	body.position = pos

	var mesh_inst := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	cyl.top_radius = radius
	cyl.bottom_radius = radius
	cyl.height = height
	mesh_inst.mesh = cyl

	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mesh_inst.material_override = mat

	var col := CollisionShape3D.new()
	var shape := CylinderShape3D.new()
	shape.radius = radius
	shape.height = height
	col.shape = shape

	body.add_child(mesh_inst)
	body.add_child(col)
	add_child(body)


func _place_sphere(pos: Vector3, radius: float, color: Color) -> void:
	var body := StaticBody3D.new()
	body.position = pos

	var mesh_inst := MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = radius
	sphere.height = radius * 2.0
	mesh_inst.mesh = sphere

	var mat := StandardMaterial3D.new()
	mat.albedo_color = color
	mesh_inst.material_override = mat

	var col := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = radius
	col.shape = shape

	body.add_child(mesh_inst)
	body.add_child(col)
	add_child(body)


func _place_tree(base_pos: Vector3) -> void:
	# Trunk
	_place_cylinder(base_pos + Vector3(0, 1.5, 0), 0.25, 3.0, Color(0.4, 0.26, 0.13))
	# Canopy
	_place_sphere(base_pos + Vector3(0, 3.8, 0), 1.5, Color(0.15, 0.45, 0.12))


func _send_enter_world() -> void:
	NetworkManager.send_enter_world(
		GameState.jwt,
		GameState.selected_character_id,
		GameState.session_id)


# ---- Signal handlers ----

func _on_zone_data(data: Dictionary) -> void:
	GameState.current_zone_id = data.get("zone_id", 0)
	GameState.current_channel_id = data.get("channel_id", 0)
	GameState.current_zone_name = data.get("zone_name", "")

	# Clear existing remote entities
	_entity_factory.clear_all()

	# Spawn all entities from zone data (skip our own entity)
	var entities: Array = data.get("entities", [])
	for entity_data in entities:
		var eid: int = entity_data.get("entity_id", 0)
		if eid != GameState.my_entity_id:
			_entity_factory.spawn_entity(entity_data)


func _on_entity_spawned(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if entity_id == GameState.my_entity_id:
		return  # Don't spawn ourselves as a remote entity
	_entity_factory.spawn_entity(data)


func _on_entity_despawned(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if entity_id == GameState.my_entity_id:
		return
	_entity_factory.despawn_entity(entity_id)


func _on_entity_position_updated(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if entity_id == GameState.my_entity_id:
		return  # Our own position is handled by prediction
	_entity_factory.update_entity_position(data)


func _on_world_disconnected() -> void:
	_entity_factory.clear_all()
	GameState.reset()
	UIManager.leave_game_world("login")


# ---- Player spawning ----

func _spawn_local_player() -> void:
	_player = PlayerCharacterScene.instantiate()
	# Use the spawn position from GameState if available, otherwise default
	var spawn_pos := GameState.player_position
	if spawn_pos == Vector3.ZERO:
		spawn_pos = WorldConstants.DEFAULT_SPAWN
	_player.global_position = spawn_pos
	add_child(_player)
