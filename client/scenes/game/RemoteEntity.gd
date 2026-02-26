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
var _mesh_base_y: float = 0.0
var _label_base_y: float = 0.0

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
	position = pos
	rotation.y = data.get("rotation", 0.0)

	# Seed the interpolator with the initial position
	_interpolator.push_snapshot(pos, rotation.y, false, is_flying)


func _ready() -> void:
	_update_name_label()
	_apply_appearance()
	if _mesh:
		_mesh_base_y = _mesh.position.y
	if _name_label:
		_label_base_y = _name_label.position.y


## Push a new position update from the server.
func push_position_update(pos: Vector3, rot: float, moving: bool, flying: bool, jump_offset: float = 0.0) -> void:
	is_flying = flying
	_interpolator.push_snapshot(pos, rot, moving, flying, jump_offset)


func _physics_process(_delta: float) -> void:
	var sample := _interpolator.sample()
	var new_pos: Vector3 = sample["position"]
	if new_pos != Vector3.ZERO:
		global_position = new_pos
		rotation.y = sample["rotation"]

	# Apply jump visual offset to mesh and name label
	var jump_off: float = sample.get("jump_offset", 0.0)
	if _mesh:
		_mesh.position.y = _mesh_base_y + jump_off
	if _name_label:
		_name_label.position.y = _label_base_y + jump_off


## Update the name label text based on entity type.
func _update_name_label() -> void:
	if not _name_label:
		return
	if entity_type == WorldConstants.ENTITY_TYPE_PLAYER:
		var class_abbr := _get_class_abbreviation(character_class)
		_name_label.text = "%s [%s Lv.%d]" % [entity_name, class_abbr, level]
	else:
		_name_label.text = "%s [Lv.%d]" % [entity_name, level]

	# Color the label by entity type
	match entity_type:
		WorldConstants.ENTITY_TYPE_MONSTER:
			_name_label.modulate = Color(1.0, 0.4, 0.4)
		WorldConstants.ENTITY_TYPE_NPC:
			_name_label.modulate = Color(0.4, 1.0, 0.4)
		_:
			_name_label.modulate = Color.WHITE


## Apply visual appearance based on entity type and character class.
func _apply_appearance() -> void:
	if not _mesh:
		return
	var mat := StandardMaterial3D.new()
	match entity_type:
		WorldConstants.ENTITY_TYPE_PLAYER:
			mat.albedo_color = _get_class_color(character_class)
		WorldConstants.ENTITY_TYPE_MONSTER:
			mat.albedo_color = Color(0.8, 0.2, 0.2)
			_mesh.scale = Vector3(1.2, 1.2, 1.2)
		WorldConstants.ENTITY_TYPE_NPC:
			mat.albedo_color = Color(0.2, 0.8, 0.2)
		WorldConstants.ENTITY_TYPE_LOOT:
			mat.albedo_color = Color(0.8, 0.7, 0.2)
			_mesh.scale = Vector3(0.5, 0.5, 0.5)
		_:
			mat.albedo_color = Color(0.5, 0.5, 0.5)
	_mesh.material_override = mat


func _get_class_color(cls: int) -> Color:
	match cls:
		WorldConstants.CLASS_WARRIOR:
			return Color(0.3, 0.5, 0.9)   # Steel blue
		WorldConstants.CLASS_MAGE:
			return Color(0.6, 0.2, 0.8)   # Purple
		WorldConstants.CLASS_ASSASSIN:
			return Color(0.15, 0.15, 0.15) # Dark
		WorldConstants.CLASS_CLERIC:
			return Color(0.9, 0.85, 0.5)  # Golden
		_:
			return Color(0.2, 0.6, 1.0)   # Default blue


func _get_class_abbreviation(cls: int) -> String:
	match cls:
		WorldConstants.CLASS_WARRIOR:  return "WAR"
		WorldConstants.CLASS_MAGE:     return "MAG"
		WorldConstants.CLASS_ASSASSIN: return "ASN"
		WorldConstants.CLASS_CLERIC:   return "CLR"
		_: return "???"
