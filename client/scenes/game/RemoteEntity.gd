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

var _selected: bool = false
var _selection_ring: MeshInstance3D = null

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
	_create_selection_ring()
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
			var monster_mesh := _get_monster_mesh(entity_name)
			if not monster_mesh.is_empty():
				_mesh.mesh = monster_mesh["mesh"]
				mat.albedo_color = monster_mesh["color"]
				_mesh.scale = monster_mesh.get("scale", Vector3(1, 1, 1))
			else:
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


func _get_monster_mesh(mname: String) -> Dictionary:
	match mname:
		"Slime":
			var m := SphereMesh.new()
			m.radius = 0.5
			m.height = 1.0
			return {"mesh": m, "color": Color(0.3, 0.9, 0.3), "scale": Vector3(1.0, 0.7, 1.0)}
		"Forest Mushroom":
			var m := CylinderMesh.new()
			m.top_radius = 0.6
			m.bottom_radius = 0.2
			m.height = 1.2
			return {"mesh": m, "color": Color(0.6, 0.35, 0.2), "scale": Vector3(1, 1, 1)}
		"Wild Boar":
			var m := BoxMesh.new()
			m.size = Vector3(1.2, 0.8, 1.6)
			return {"mesh": m, "color": Color(0.5, 0.2, 0.15), "scale": Vector3(1, 1, 1)}
		"Forest Wolf":
			var m := PrismMesh.new()
			m.size = Vector3(0.8, 0.9, 1.4)
			return {"mesh": m, "color": Color(0.55, 0.55, 0.5), "scale": Vector3(1, 1, 1)}
		"Stone Golem":
			var m := BoxMesh.new()
			m.size = Vector3(1.5, 2.0, 1.5)
			return {"mesh": m, "color": Color(0.45, 0.42, 0.4), "scale": Vector3(1.3, 1.3, 1.3)}
	return {}


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


# ---- Selection highlight ----

## Toggle the selection highlight ring around this entity.
func set_selected(selected: bool) -> void:
	_selected = selected
	if _selection_ring:
		_selection_ring.visible = selected


## Update the entity's HP from a server event.
func update_hp(new_hp: int) -> void:
	hp = new_hp
	if hp <= 0:
		set_selected(false)


## Create a golden torus at the entity's feet as a selection indicator.
func _create_selection_ring() -> void:
	_selection_ring = MeshInstance3D.new()
	var torus := TorusMesh.new()
	torus.inner_radius = 0.6
	torus.outer_radius = 0.8
	torus.rings = 16
	torus.ring_segments = 16
	_selection_ring.mesh = torus
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(1.0, 0.85, 0.0, 0.7)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	_selection_ring.material_override = mat
	_selection_ring.position.y = 0.05
	_selection_ring.visible = false
	add_child(_selection_ring)
