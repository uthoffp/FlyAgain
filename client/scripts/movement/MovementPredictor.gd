## MovementPredictor.gd
## Client-side movement prediction and server reconciliation.
## Pure logic class (RefCounted, no Node dependency) for testability.
##
## The client applies movement locally and sends MovementInput to the server.
## If the server detects a violation (speed hack, boundary), it sends a
## PositionCorrection, and we snap back. No selective input replay is needed
## because corrections only occur on violations, not during normal play.
class_name MovementPredictor
extends RefCounted


# ---- Internal state ----

var _current_position: Vector3 = Vector3.ZERO
var _current_rotation: float = 0.0
var _sequence: int = 0
var _is_flying: bool = false

const MAX_PENDING := 60  # ~3 seconds at 20 Hz


# ---- Public API ----

## Apply a movement input locally (prediction).
## Returns the new predicted position.
func apply_input(
	direction: Vector3,
	is_moving: bool,
	flying: bool,
	dex: int,
	delta: float
) -> Vector3:
	_sequence += 1
	_is_flying = flying

	if is_moving and direction.length_squared() > 0.001:
		var speed := _compute_speed(flying, dex)
		var displacement := direction * speed * delta

		# Non-flying: zero out vertical displacement
		if not flying:
			displacement.y = 0.0

		_current_position += displacement

	_current_position = _clamp_to_world_bounds(_current_position, flying)
	return _current_position


## Handle a server position correction: hard snap-back.
## Returns the corrected position.
func apply_correction(server_pos: Vector3, server_rotation: float) -> Vector3:
	_current_position = server_pos
	_current_rotation = server_rotation
	return _current_position


## Returns the current sequence number (for the MovementInput packet).
func get_sequence() -> int:
	return _sequence


## Returns the current predicted position.
func get_position() -> Vector3:
	return _current_position


## Sets the position (e.g. on initial spawn).
func set_position(pos: Vector3) -> void:
	_current_position = pos


## Returns the current rotation.
func get_rotation() -> float:
	return _current_rotation


## Sets the rotation.
func set_rotation(rot: float) -> void:
	_current_rotation = rot


## Returns whether the predictor is in flying mode.
func is_flying() -> bool:
	return _is_flying


## Sets flying mode.
func set_flying(flying: bool) -> void:
	_is_flying = flying


# ---- Private helpers ----

## Computes movement speed matching the server's MovementHandler.
func _compute_speed(flying: bool, dex: int) -> float:
	var base := WorldConstants.FLY_MOVE_SPEED if flying else WorldConstants.GROUND_MOVE_SPEED
	return base + (dex * 0.05)


## Clamps position to world boundaries matching the server.
func _clamp_to_world_bounds(pos: Vector3, flying: bool) -> Vector3:
	pos.x = clampf(pos.x, WorldConstants.WORLD_BOUNDARY_MIN, WorldConstants.WORLD_BOUNDARY_MAX)
	pos.z = clampf(pos.z, WorldConstants.WORLD_BOUNDARY_MIN, WorldConstants.WORLD_BOUNDARY_MAX)
	if flying:
		pos.y = clampf(pos.y, WorldConstants.MIN_Y_POSITION, WorldConstants.MAX_Y_POSITION)
	else:
		pos.y = clampf(pos.y, WorldConstants.MIN_Y_POSITION, WorldConstants.MAX_GROUND_Y)
	return pos
