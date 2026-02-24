## PlayerCharacter.gd
## The local player's character.
## Uses CharacterBody3D for physics, MovementPredictor for client-side prediction,
## and sends MovementInput via UDP at the server tick rate.
extends CharacterBody3D


@onready var _camera_pivot: Node3D = $CameraPivot
@onready var _mesh: MeshInstance3D = $MeshInstance3D
@onready var _name_label: Label3D = $NameLabel

var _predictor: MovementPredictor = MovementPredictor.new()
var _is_flying: bool = false
var _facing_angle: float = 0.0
var _send_timer: float = 0.0
const SEND_INTERVAL := 1.0 / WorldConstants.MOVEMENT_SEND_RATE  # 50ms


func _ready() -> void:
	_predictor.set_position(global_position)
	_setup_player_name()
	_setup_appearance()
	NetworkManager.position_corrected.connect(_on_position_corrected)


func _exit_tree() -> void:
	if NetworkManager.position_corrected.is_connected(_on_position_corrected):
		NetworkManager.position_corrected.disconnect(_on_position_corrected)


func _physics_process(delta: float) -> void:
	var direction := _get_input_direction()
	var is_moving := direction.length_squared() > 0.001

	_handle_flight_input()

	# Client-side prediction
	var new_pos := _predictor.apply_input(
		direction, is_moving, _is_flying, GameState.player_dex, delta)
	global_position = new_pos

	# Rotate only the mesh toward movement direction (NOT the root node,
	# because CameraPivot is a child and would rotate with it).
	if is_moving and direction.length() > 0.01:
		var target_angle := atan2(direction.x, direction.z)
		_facing_angle = lerp_angle(_facing_angle, target_angle, 10.0 * delta)
		_mesh.rotation.y = _facing_angle

	# Send to server at tick rate
	_send_timer += delta
	if _send_timer >= SEND_INTERVAL:
		_send_timer -= SEND_INTERVAL
		_send_movement(direction, is_moving)


## Build the movement direction vector relative to the camera.
func _get_input_direction() -> Vector3:
	var cam_forward: Vector3 = _camera_pivot.get_camera_forward()
	var cam_right: Vector3 = _camera_pivot.get_camera_right()

	var input_dir := Vector3.ZERO
	if Input.is_action_pressed("move_forward"):
		input_dir += cam_forward
	if Input.is_action_pressed("move_backward"):
		input_dir -= cam_forward
	if Input.is_action_pressed("move_left"):
		input_dir -= cam_right
	if Input.is_action_pressed("move_right"):
		input_dir += cam_right

	# Vertical movement when flying
	if _is_flying:
		if Input.is_action_pressed("fly_up"):
			input_dir.y += 1.0
		if Input.is_action_pressed("fly_down"):
			input_dir.y -= 1.0

	return input_dir.normalized() if input_dir.length() > 0.01 else Vector3.ZERO


## Handle flight toggle input.
func _handle_flight_input() -> void:
	if Input.is_action_just_pressed("toggle_flight"):
		_is_flying = not _is_flying
		_predictor.set_flying(_is_flying)


## Send the current movement state to the server via UDP.
func _send_movement(direction: Vector3, is_moving: bool) -> void:
	NetworkManager.send_movement_input(
		global_position,
		_facing_angle,
		direction.x, direction.y, direction.z,
		is_moving, _is_flying,
		_predictor.get_sequence())


## Handle server position correction.
func _on_position_corrected(data: Dictionary) -> void:
	var pos_dict: Dictionary = data.get("position", {})
	var server_pos := Vector3(
		pos_dict.get("x", 0.0),
		pos_dict.get("y", 0.0),
		pos_dict.get("z", 0.0))
	var server_rot: float = data.get("rotation", 0.0)
	var corrected := _predictor.apply_correction(server_pos, server_rot)
	global_position = corrected
	_facing_angle = server_rot
	_mesh.rotation.y = _facing_angle
	push_warning("Position corrected: %s (reason: %s)" % [server_pos, data.get("reason", "")])


## Set up the player name label.
func _setup_player_name() -> void:
	var char_name := "Player"
	for c in GameState.characters:
		if c.get("id") == GameState.selected_character_id:
			char_name = c.get("name", "Player")
			break
	if _name_label:
		_name_label.text = char_name


## Set up the player appearance (placeholder gold capsule).
func _setup_appearance() -> void:
	if _mesh:
		var mat := StandardMaterial3D.new()
		mat.albedo_color = Color(0.85, 0.65, 0.13)  # Gold
		_mesh.material_override = mat
