## RemoteEntity.gd
## Represents a non-local entity (other player, monster, NPC, loot).
## Uses EntityInterpolator for smooth movement between server updates.
extends CharacterBody3D


# ---- Entity data ----

var entity_id: int = 0
var entity_type: int = 0
var entity_name: String = ""
var level: int = 0
var hp: int = 0
var max_hp: int = 0
var character_class: int = 0
var is_flying: bool = false

var _interpolator: EntityInterpolator = EntityInterpolator.new()

@onready var _mesh: MeshInstance3D = $MeshInstance3D
@onready var _name_label: Label3D = $NameLabel


## Initialize entity from an EntitySpawnMessage dictionary.
func setup(data: Dictionary) -> void:
	entity_id = data.get("entity_id", 0)
	entity_type = data.get("entity_type", 0)
	entity_name = data.get("name", "")
	level = data.get("level", 0)
	hp = data.get("hp", 0)
	max_hp = data.get("max_hp", 0)
	character_class = data.get("character_class", 0)
	is_flying = data.get("is_flying", false)

	var pos_dict: Dictionary = data.get("position", {})
	var pos := Vector3(
		pos_dict.get("x", 0.0),
		pos_dict.get("y", 0.0),
		pos_dict.get("z", 0.0))
	global_position = pos
	rotation.y = data.get("rotation", 0.0)

	# Seed the interpolator with the initial position
	_interpolator.push_snapshot(pos, rotation.y, false, is_flying)


func _ready() -> void:
	_update_name_label()
	_apply_appearance()


## Push a new position update from the server.
func push_position_update(pos: Vector3, rot: float, moving: bool, flying: bool) -> void:
	is_flying = flying
	_interpolator.push_snapshot(pos, rot, moving, flying)


func _physics_process(_delta: float) -> void:
	var sample := _interpolator.sample()
	var new_pos: Vector3 = sample["position"]
	if new_pos != Vector3.ZERO:
		global_position = new_pos
		rotation.y = sample["rotation"]


## Update the name label text.
func _update_name_label() -> void:
	if _name_label:
		_name_label.text = "%s [Lv.%d]" % [entity_name, level]


## Apply visual appearance based on entity type (placeholder colors).
func _apply_appearance() -> void:
	if not _mesh:
		return
	var mat := StandardMaterial3D.new()
	match entity_type:
		WorldConstants.ENTITY_TYPE_PLAYER:
			mat.albedo_color = Color(0.2, 0.6, 1.0)   # Blue
		WorldConstants.ENTITY_TYPE_MONSTER:
			mat.albedo_color = Color(0.8, 0.2, 0.2)   # Red
		WorldConstants.ENTITY_TYPE_NPC:
			mat.albedo_color = Color(0.2, 0.8, 0.2)   # Green
		_:
			mat.albedo_color = Color(0.5, 0.5, 0.5)   # Gray
	_mesh.material_override = mat
