## PlayerCharacter.gd
## The local player's character.
## Uses CharacterBody3D for physics, MovementPredictor for client-side prediction,
## and sends MovementInput via UDP at the server tick rate.
## Supports both WASD movement and Click-to-Move (left-click on terrain).
extends CharacterBody3D


@onready var _camera_pivot: Node3D = $CameraPivot
@onready var _mesh: MeshInstance3D = $MeshInstance3D
@onready var _name_label: Label3D = $NameLabel

var _predictor: MovementPredictor = MovementPredictor.new()
var _is_flying: bool = false
var _facing_angle: float = 0.0
var _send_timer: float = 0.0
const SEND_INTERVAL := 1.0 / WorldConstants.MOVEMENT_SEND_RATE  # 50ms

# Jump state (client-side arc, offset sent to server for remote sync)
var _jump_velocity: float = 0.0
var _jump_offset: float = 0.0
var _is_grounded: bool = true
var _mesh_base_y: float = 0.0
var _label_base_y: float = 0.0

# Click-to-Move state
var _click_target: Vector3 = Vector3.ZERO
var _has_click_target: bool = false
var _click_stall_timer: float = 0.0
var _last_click_distance: float = 0.0
var _click_marker: MeshInstance3D = null
const CLICK_ARRIVE_THRESHOLD := 0.5
const CLICK_RAYCAST_LENGTH := 1000.0
const CLICK_STALL_TIMEOUT := 1.5


func _ready() -> void:
	_predictor.set_position(global_position)
	_setup_player_name()
	_setup_appearance()
	_create_click_marker()
	NetworkManager.position_corrected.connect(_on_position_corrected)
	# Store base Y offsets for jump visual
	if _mesh:
		_mesh_base_y = _mesh.position.y
	if _name_label:
		_label_base_y = _name_label.position.y


func _exit_tree() -> void:
	if NetworkManager.position_corrected.is_connected(_on_position_corrected):
		NetworkManager.position_corrected.disconnect(_on_position_corrected)
	if _click_marker and is_instance_valid(_click_marker):
		_click_marker.queue_free()


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseButton \
			and event.button_index == MOUSE_BUTTON_LEFT \
			and event.pressed \
			and not _is_flying \
			and not _camera_pivot.is_rotating():
		_try_click_to_move(event.position)


func _physics_process(delta: float) -> void:
	var wasd_direction := _get_wasd_direction()
	var has_wasd := wasd_direction.length_squared() > 0.001

	_handle_flight_input()
	_handle_jump_input()

	# WASD cancels click-to-move
	if has_wasd and _has_click_target:
		_cancel_click_to_move()

	# Determine final movement direction
	var direction: Vector3
	var is_moving: bool

	if has_wasd:
		direction = wasd_direction
		is_moving = true
	elif _has_click_target and not _is_flying:
		direction = _get_click_direction()
		is_moving = direction.length_squared() > 0.001
		if not is_moving:
			_cancel_click_to_move()
		else:
			_update_click_stall(delta)
	else:
		direction = Vector3.ZERO
		is_moving = false

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

	# Update jump visual offset (applies to mesh + label, not the root node)
	_update_jump(delta)

	# Send to server at tick rate
	_send_timer += delta
	if _send_timer >= SEND_INTERVAL:
		_send_timer -= SEND_INTERVAL
		_send_movement(direction, is_moving)


## Build the WASD movement direction vector relative to the camera.
func _get_wasd_direction() -> Vector3:
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


# ---- Click-to-Move ----

## Raycast from camera to terrain at the clicked screen position.
func _try_click_to_move(screen_pos: Vector2) -> void:
	var camera: Camera3D = _camera_pivot.get_node("SpringArm3D/Camera3D")
	if not camera:
		return
	var from: Vector3 = camera.project_ray_origin(screen_pos)
	var to: Vector3 = from + camera.project_ray_normal(screen_pos) * CLICK_RAYCAST_LENGTH

	var space_state := get_world_3d().direct_space_state
	var query := PhysicsRayQueryParameters3D.create(from, to)
	query.collision_mask = 1
	var result := space_state.intersect_ray(query)

	if result.is_empty():
		return

	_click_target = result["position"]
	_click_target.y = 0.0
	_has_click_target = true
	_click_stall_timer = 0.0
	_last_click_distance = (_click_target - global_position).length()
	_show_click_marker(_click_target)


## Compute direction toward the click-to-move target (XZ plane only).
func _get_click_direction() -> Vector3:
	var to_target := _click_target - global_position
	to_target.y = 0.0
	if to_target.length() < CLICK_ARRIVE_THRESHOLD:
		return Vector3.ZERO
	return to_target.normalized()


## Cancel the active click-to-move target.
func _cancel_click_to_move() -> void:
	_has_click_target = false
	_click_stall_timer = 0.0
	_hide_click_marker()


## Detect if click-to-move is stuck (at boundary or obstacle).
func _update_click_stall(delta: float) -> void:
	var current_dist := (_click_target - global_position).length()
	if absf(current_dist - _last_click_distance) < 0.05:
		_click_stall_timer += delta
		if _click_stall_timer >= CLICK_STALL_TIMEOUT:
			_cancel_click_to_move()
	else:
		_click_stall_timer = 0.0
	_last_click_distance = current_dist


## Handle flight toggle input.
func _handle_flight_input() -> void:
	if Input.is_action_just_pressed("toggle_flight"):
		_is_flying = not _is_flying
		_predictor.set_flying(_is_flying)
		if _is_flying and _has_click_target:
			_cancel_click_to_move()
		if _is_flying:
			# Reset jump state when entering flight
			_jump_offset = 0.0
			_jump_velocity = 0.0
			_is_grounded = true
			_apply_jump_visual()


## Handle jump input (only when grounded and not flying).
func _handle_jump_input() -> void:
	if _is_flying or not _is_grounded:
		return
	if Input.is_action_just_pressed("jump"):
		_jump_velocity = WorldConstants.JUMP_VELOCITY
		_is_grounded = false


## Update jump arc (gravity) and apply the visual offset to mesh + label.
func _update_jump(delta: float) -> void:
	if _is_flying:
		return
	if not _is_grounded:
		_jump_velocity -= WorldConstants.GRAVITY * delta
		_jump_offset += _jump_velocity * delta
		if _jump_offset <= 0.0:
			_jump_offset = 0.0
			_jump_velocity = 0.0
			_is_grounded = true
	_apply_jump_visual()


## Offsets the mesh and name label vertically for the jump effect.
func _apply_jump_visual() -> void:
	if _mesh:
		_mesh.position.y = _mesh_base_y + _jump_offset
	if _name_label:
		_name_label.position.y = _label_base_y + _jump_offset


## Send the current movement state to the server via UDP.
func _send_movement(direction: Vector3, is_moving: bool) -> void:
	NetworkManager.send_movement_input(
		global_position,
		_facing_angle,
		direction.x, direction.y, direction.z,
		is_moving, _is_flying,
		_predictor.get_sequence(),
		_jump_offset)


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
	_cancel_click_to_move()
	push_warning("Position corrected: %s (reason: %s)" % [server_pos, data.get("reason", "")])


# ---- Click marker ----

## Create the click-to-move target indicator (green torus on the ground).
func _create_click_marker() -> void:
	_click_marker = MeshInstance3D.new()
	var torus := TorusMesh.new()
	torus.inner_radius = 0.3
	torus.outer_radius = 0.5
	torus.rings = 16
	torus.ring_segments = 16
	_click_marker.mesh = torus

	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.2, 0.8, 0.3, 0.7)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	mat.no_depth_test = true
	_click_marker.material_override = mat
	_click_marker.visible = false

	get_tree().current_scene.call_deferred("add_child", _click_marker)


func _show_click_marker(pos: Vector3) -> void:
	if _click_marker:
		_click_marker.global_position = pos + Vector3(0, 0.05, 0)
		_click_marker.visible = true


func _hide_click_marker() -> void:
	if _click_marker:
		_click_marker.visible = false


# ---- Setup ----

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
