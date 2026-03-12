## PlayerCharacter.gd
## The local player's character.
## Uses CharacterBody3D for physics, MovementPredictor for client-side prediction,
## and sends MovementInput via UDP at the server tick rate.
## Supports both WASD movement and Click-to-Move (left-click on terrain).
extends CharacterBody3D


signal target_selected(entity_id: int)
signal target_cleared()
signal auto_attack_toggled(enable: bool, target_id: int)
signal approach_in_range(entity_id: int)

## Adjust in the editor if the model clips into or floats above the ground.
@export var model_y_offset: float = 0.0
## Uniform scale for the character model.
@export var model_scale: float = 1.0

@onready var _camera_pivot: Node3D = $CameraPivot
@onready var _model_pivot: Node3D = $ModelPivot
@onready var _name_label: Label3D = $NameLabel

var _predictor: MovementPredictor = MovementPredictor.new()
var _terrain: Node3D = null
var _is_flying: bool = false
var _facing_angle: float = 0.0
var _send_timer: float = 0.0
const SEND_INTERVAL := 1.0 / WorldConstants.MOVEMENT_SEND_RATE  # 50ms

# Jump state (client-side arc, offset sent to server for remote sync)
var _jump_velocity: float = 0.0
var _jump_offset: float = 0.0
var _is_grounded: bool = true
var _pivot_base_y: float = 0.0
var _label_base_y: float = 0.0
var _camera_base_y: float = 0.0

# Click-to-Move state
var _click_target: Vector3 = Vector3.ZERO
var _has_click_target: bool = false
var _click_stall_timer: float = 0.0
var _last_click_distance: float = 0.0
var _click_marker: MeshInstance3D = null
const CLICK_ARRIVE_THRESHOLD := 0.5
const CLICK_RAYCAST_LENGTH := 1000.0
const CLICK_STALL_TIMEOUT := 1.5

# Auto-run (double-tap W to toggle)
var _auto_run: bool = false
var _last_forward_press_time: float = 0.0
const DOUBLE_TAP_THRESHOLD := 0.3  # seconds

# Right-click targeting state (distinguishes quick clicks from camera drags)
var _right_click_start_pos: Vector2 = Vector2.ZERO
var _right_click_pressed: bool = false
var _right_click_press_time: float = 0.0
const TARGET_CLICK_TIME_THRESHOLD := 0.25  # Max seconds to count as a click (not drag)

# Approach-to-attack state (walk toward selected entity, auto-attack when in range)
var _approach_entity: Node3D = null
var _approach_entity_id: int = 0
var _pending_target_id: int = 0  # Set on first click, before server confirms
const APPROACH_ATTACK_RANGE := 2.0


func _ready() -> void:
	_predictor.set_position(global_position)
	_setup_player_name()
	_setup_model()
	_create_click_marker()
	NetworkManager.position_corrected.connect(_on_position_corrected)
	_find_terrain()
	# Store base Y offsets for jump visual
	if _model_pivot:
		_pivot_base_y = _model_pivot.position.y
	if _name_label:
		_label_base_y = _name_label.position.y
	if _camera_pivot:
		_camera_base_y = _camera_pivot.position.y


func _exit_tree() -> void:
	if NetworkManager.position_corrected.is_connected(_on_position_corrected):
		NetworkManager.position_corrected.disconnect(_on_position_corrected)
	if _click_marker and is_instance_valid(_click_marker):
		_click_marker.queue_free()


func _unhandled_input(event: InputEvent) -> void:
	# Left-click: target entity if hit, otherwise click-to-move
	if event is InputEventMouseButton \
			and event.button_index == MOUSE_BUTTON_LEFT \
			and event.pressed \
			and not _camera_pivot.is_rotating():
		if not _try_target_entity(event.position):
			if not _is_flying:
				_try_click_to_move(event.position)

	# Right-click: target entity (only on quick clicks, not camera drags)
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_RIGHT:
		if event.pressed:
			_right_click_start_pos = event.position
			_right_click_pressed = true
			_right_click_press_time = Time.get_ticks_msec() / 1000.0
		elif _right_click_pressed:
			_right_click_pressed = false
			var held_time := Time.get_ticks_msec() / 1000.0 - _right_click_press_time
			if held_time < TARGET_CLICK_TIME_THRESHOLD:
				_try_target_entity(_right_click_start_pos)

	# Double-tap W: toggle auto-run
	if event.is_action_pressed("move_forward") and not event.is_echo():
		var now := Time.get_ticks_msec() / 1000.0
		if _auto_run:
			_auto_run = false
		elif now - _last_forward_press_time < DOUBLE_TAP_THRESHOLD:
			_auto_run = true
		_last_forward_press_time = now

	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_F:
				auto_attack_toggled.emit(
					not GameState.auto_attack_active,
					GameState.selected_target_id)
				get_viewport().set_input_as_handled()
			KEY_ESCAPE:
				_pending_target_id = 0
				target_cleared.emit()
				get_viewport().set_input_as_handled()


func _physics_process(delta: float) -> void:
	var wasd_direction := _get_wasd_direction()
	var has_wasd := wasd_direction.length_squared() > 0.001

	_handle_flight_input()
	_handle_jump_input()

	# WASD cancels click-to-move and approach
	if has_wasd and _has_click_target:
		_cancel_click_to_move()
	if has_wasd and _approach_entity:
		cancel_approach()

	# Determine final movement direction
	var direction: Vector3
	var is_moving: bool

	if has_wasd:
		direction = wasd_direction
		is_moving = true
	elif _approach_entity:
		if not is_instance_valid(_approach_entity) or _approach_entity.hp <= 0:
			cancel_approach()
			direction = Vector3.ZERO
			is_moving = false
		else:
			var to_entity := _approach_entity.global_position - global_position
			to_entity.y = 0.0
			var dist := to_entity.length()
			if dist <= APPROACH_ATTACK_RANGE:
				var eid := _approach_entity_id
				print("[Combat] Approach in range (dist=%.2f, threshold=%.1f) -> entity %d" % [dist, APPROACH_ATTACK_RANGE, eid])
				cancel_approach()
				approach_in_range.emit(eid)
				direction = Vector3.ZERO
				is_moving = false
			else:
				direction = to_entity.normalized()
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

	# Snap to terrain height when on the ground
	if not _is_flying:
		var terrain_y := _get_terrain_height(new_pos.x, new_pos.z)
		new_pos.y = terrain_y
		_predictor.set_position(new_pos)

	global_position = new_pos

	# Rotate only the model toward movement direction (NOT the root node,
	# because CameraPivot is a child and would rotate with it).
	if is_moving and direction.length() > 0.01:
		var target_angle := atan2(direction.x, direction.z)
		_facing_angle = lerp_angle(_facing_angle, target_angle, 10.0 * delta)
		_model_pivot.rotation.y = _facing_angle

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

	var input_dir := Vector3.ZERO
	if _auto_run or Input.is_action_pressed("move_forward"):
		input_dir += cam_forward
	if Input.is_action_pressed("move_backward"):
		input_dir -= cam_forward
		_auto_run = false
	# A/D handled by ThirdPersonCamera (yaw rotation), not strafing

	# Vertical movement when flying
	if _is_flying:
		if Input.is_action_pressed("fly_up"):
			input_dir.y += 1.0
		if Input.is_action_pressed("fly_down"):
			input_dir.y -= 1.0

	return input_dir.normalized() if input_dir.length() > 0.01 else Vector3.ZERO


# ---- Target selection ----

## Raycast from camera to find a targetable entity at the clicked screen position.
## Returns true if an entity was found and targeted.
func _try_target_entity(screen_pos: Vector2) -> bool:
	var camera: Camera3D = _camera_pivot.get_node("SpringArm3D/Camera3D")
	if not camera:
		return false
	var from: Vector3 = camera.project_ray_origin(screen_pos)
	var to: Vector3 = from + camera.project_ray_normal(screen_pos) * CLICK_RAYCAST_LENGTH

	var space_state := get_world_3d().direct_space_state
	var query := PhysicsRayQueryParameters3D.create(from, to)
	query.collision_mask = 0xFFFFFFFF  # Check all layers for entities
	query.exclude = [get_rid()]
	var result := space_state.intersect_ray(query)

	if result.is_empty():
		return false

	# Check if we hit an entity (directly or via parent)
	var collider: Node = result.get("collider")
	if not collider:
		return false

	var entity: Node = null
	if collider is CharacterBody3D and collider.has_method("set_selected"):
		entity = collider
	elif collider.get_parent() is CharacterBody3D and collider.get_parent().has_method("set_selected"):
		entity = collider.get_parent()

	if entity and entity.hp > 0:
		if entity.entity_id == GameState.selected_target_id or entity.entity_id == _pending_target_id:
			_start_approach(entity)
		else:
			cancel_approach()
			_pending_target_id = entity.entity_id
			target_selected.emit(entity.entity_id)
		return true
	return false


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
	query.exclude = [get_rid()]
	var result := space_state.intersect_ray(query)

	if result.is_empty():
		# Physics raycast missed — fall back to projecting onto terrain heightmap.
		var fallback := _project_click_to_terrain(camera, screen_pos)
		if fallback == Vector3.ZERO:
			return
		_auto_run = false
		cancel_approach()
		_click_target = fallback
		_has_click_target = true
		_click_stall_timer = 0.0
		_last_click_distance = (_click_target - global_position).length()
		_show_click_marker(_click_target)
		return

	_auto_run = false
	cancel_approach()
	_click_target = result["position"]
	_has_click_target = true
	_click_stall_timer = 0.0
	_last_click_distance = (_click_target - global_position).length()
	_show_click_marker(_click_target)


## Project a screen click onto the terrain heightmap (fallback when physics raycast misses).
func _project_click_to_terrain(camera: Camera3D, screen_pos: Vector2) -> Vector3:
	if not _terrain:
		return Vector3.ZERO
	var origin := camera.project_ray_origin(screen_pos)
	var dir := camera.project_ray_normal(screen_pos)
	# Ray must be pointing downward to hit the ground plane.
	if absf(dir.y) < 0.001:
		return Vector3.ZERO
	var ground_y := global_position.y
	var t := (ground_y - origin.y) / dir.y
	if t < 0.0:
		return Vector3.ZERO
	var hit_x := origin.x + dir.x * t
	var hit_z := origin.z + dir.z * t
	var terrain_y := _get_terrain_height(hit_x, hit_z)
	return Vector3(hit_x, terrain_y, hit_z)


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


## Start approaching the given entity for melee attack.
func _start_approach(entity_node: Node3D) -> void:
	_approach_entity = entity_node
	_approach_entity_id = entity_node.entity_id
	_cancel_click_to_move()
	_auto_run = false


## Cancel the current approach-to-attack.
func cancel_approach() -> void:
	_approach_entity = null
	_approach_entity_id = 0


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


## Offsets the model and name label vertically for the jump effect.
func _apply_jump_visual() -> void:
	if _model_pivot:
		_model_pivot.position.y = _pivot_base_y + _jump_offset
	if _name_label:
		_name_label.position.y = _label_base_y + _jump_offset
	if _camera_pivot:
		_camera_pivot.position.y = _camera_base_y + _jump_offset


## Send the current movement state to the server via UDP.
func _send_movement(direction: Vector3, is_moving: bool) -> void:
	NetworkManager.send_movement_input(
		global_position,
		_facing_angle,
		direction.x, direction.y, direction.z,
		is_moving, _is_flying,
		_predictor.get_sequence(),
		_jump_offset)


## Teleport to a new position (e.g. after zone change).
## Updates both the node position and the movement predictor.
func teleport_to(pos: Vector3) -> void:
	_find_terrain()
	global_position = pos
	_predictor.set_position(pos)
	_cancel_click_to_move()
	cancel_approach()
	_auto_run = false
	_is_flying = false
	_predictor.set_flying(false)
	_jump_offset = 0.0
	_jump_velocity = 0.0
	_is_grounded = true
	_apply_jump_visual()


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
	_model_pivot.rotation.y = _facing_angle
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


## Apply model Y-offset and scale from export vars.
func _setup_model() -> void:
	if _model_pivot:
		_model_pivot.position.y = model_y_offset
		_model_pivot.scale = Vector3.ONE * model_scale


## Find the terrain node (sibling via GameWorld parent) for height queries.
func _find_terrain() -> void:
	var parent := get_parent()
	if parent and parent.has_method("get_height_at"):
		_terrain = parent
		return
	if parent:
		for child in parent.get_children():
			if child.has_method("get_height_at"):
				_terrain = child
				return


## Query terrain height at the given world XZ position.
func _get_terrain_height(world_x: float, world_z: float) -> float:
	if _terrain and _terrain.has_method("get_height_at"):
		return _terrain.get_height_at(world_x, world_z)
	return 0.0
