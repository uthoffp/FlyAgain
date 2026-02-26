## ZonePortal.gd
## An interactive portal that triggers a zone change when the player enters it.
## Place as a child of terrain scenes. Configure target_zone_id and portal_label.
extends Area3D


signal portal_entered(target_zone_id: int)

## The zone this portal leads to.
@export var target_zone_id: int = 0

## Display label for the portal (e.g., "Green Plains", "Aerheim").
@export var portal_label: String = ""

## Portal pillar color.
@export var portal_color: Color = Color(0.3, 0.5, 1.0, 0.8)

var _label: Label3D = null
var _glow_mesh: MeshInstance3D = null
var _time: float = 0.0


func _ready() -> void:
	body_entered.connect(_on_body_entered)
	_build_visual()


func _physics_process(delta: float) -> void:
	_time += delta
	if _glow_mesh:
		# Gentle floating glow effect
		_glow_mesh.position.y = 2.0 + sin(_time * 2.0) * 0.15


func _on_body_entered(body: Node3D) -> void:
	# Only react to the local player — identified by the _predictor member
	# which only exists on PlayerCharacter, not on RemoteEntity.
	if body is CharacterBody3D and body.get("_predictor") != null:
		portal_entered.emit(target_zone_id)


func _build_visual() -> void:
	# Collision shape for detection area
	var col := CollisionShape3D.new()
	var shape := CylinderShape3D.new()
	shape.radius = 3.0
	shape.height = 6.0
	col.shape = shape
	col.position = Vector3(0, 3.0, 0)
	add_child(col)

	# Base pillar
	var pillar := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	cyl.top_radius = 0.4
	cyl.bottom_radius = 0.6
	cyl.height = 4.0
	pillar.mesh = cyl
	pillar.position = Vector3(0, 2.0, 0)
	var pillar_mat := StandardMaterial3D.new()
	pillar_mat.albedo_color = Color(0.35, 0.35, 0.4)
	pillar_mat.roughness = 0.7
	pillar.material_override = pillar_mat
	add_child(pillar)

	# Glowing orb on top
	_glow_mesh = MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = 0.6
	sphere.height = 1.2
	_glow_mesh.mesh = sphere
	_glow_mesh.position = Vector3(0, 4.5, 0)
	var glow_mat := StandardMaterial3D.new()
	glow_mat.albedo_color = portal_color
	glow_mat.emission_enabled = true
	glow_mat.emission = portal_color
	glow_mat.emission_energy_multiplier = 2.0
	glow_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	_glow_mesh.material_override = glow_mat
	add_child(_glow_mesh)

	# Portal label
	_label = Label3D.new()
	_label.text = portal_label if not portal_label.is_empty() else WorldConstants.get_zone_name(target_zone_id)
	_label.position = Vector3(0, 5.5, 0)
	_label.font_size = 24
	_label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	_label.no_depth_test = true
	_label.modulate = Color(1.0, 0.9, 0.5)
	add_child(_label)


