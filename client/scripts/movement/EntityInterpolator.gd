## EntityInterpolator.gd
## Smooth interpolation for remote entities using a snapshot buffer.
## Pure logic class (RefCounted, no Node dependency) for testability.
##
## Receives server position updates and interpolates between them
## with a configurable render delay (default 100ms = 2 server ticks).
class_name EntityInterpolator
extends RefCounted


# ---- Snapshot data ----

var _buffer: Array = []  # Array of Dictionaries: { position, rotation, timestamp, is_moving, is_flying }
var _render_delay_ms: float = WorldConstants.INTERPOLATION_BUFFER_MS

const MAX_BUFFER_SIZE := 10


# ---- Public API ----

## Push a new server position update into the buffer.
func push_snapshot(
	position: Vector3,
	rotation: float,
	is_moving: bool,
	is_flying: bool
) -> void:
	var snap := {
		"position": position,
		"rotation": rotation,
		"timestamp": Time.get_ticks_msec(),
		"is_moving": is_moving,
		"is_flying": is_flying,
	}
	_buffer.append(snap)
	if _buffer.size() > MAX_BUFFER_SIZE:
		_buffer.pop_front()


## Push a snapshot with a specific timestamp (for testing).
func push_snapshot_at(
	position: Vector3,
	rotation: float,
	is_moving: bool,
	is_flying: bool,
	timestamp_ms: float
) -> void:
	var snap := {
		"position": position,
		"rotation": rotation,
		"timestamp": timestamp_ms,
		"is_moving": is_moving,
		"is_flying": is_flying,
	}
	_buffer.append(snap)
	if _buffer.size() > MAX_BUFFER_SIZE:
		_buffer.pop_front()


## Sample the interpolated position at the current render time.
## Returns: { position: Vector3, rotation: float }
func sample() -> Dictionary:
	if _buffer.is_empty():
		return {"position": Vector3.ZERO, "rotation": 0.0}

	if _buffer.size() == 1:
		return {"position": _buffer[0]["position"], "rotation": _buffer[0]["rotation"]}

	var render_time: float = Time.get_ticks_msec() - _render_delay_ms

	# Find the two snapshots surrounding render_time
	for i in range(_buffer.size() - 1):
		var a: Dictionary = _buffer[i]
		var b: Dictionary = _buffer[i + 1]
		if render_time >= a["timestamp"] and render_time <= b["timestamp"]:
			var duration: float = b["timestamp"] - a["timestamp"]
			var t: float = (render_time - a["timestamp"]) / maxf(duration, 1.0)
			t = clampf(t, 0.0, 1.0)
			return {
				"position": (a["position"] as Vector3).lerp(b["position"], t),
				"rotation": lerp_angle(a["rotation"], b["rotation"], t),
			}

	# If render_time is before all snapshots, use the earliest
	if render_time < _buffer[0]["timestamp"]:
		return {"position": _buffer[0]["position"], "rotation": _buffer[0]["rotation"]}

	# Past the latest snapshot — use last known position (no extrapolation)
	var last: Dictionary = _buffer.back()
	return {"position": last["position"], "rotation": last["rotation"]}


## Sample at a specific render time (for testing).
func sample_at(render_time_ms: float) -> Dictionary:
	if _buffer.is_empty():
		return {"position": Vector3.ZERO, "rotation": 0.0}

	if _buffer.size() == 1:
		return {"position": _buffer[0]["position"], "rotation": _buffer[0]["rotation"]}

	for i in range(_buffer.size() - 1):
		var a: Dictionary = _buffer[i]
		var b: Dictionary = _buffer[i + 1]
		if render_time_ms >= a["timestamp"] and render_time_ms <= b["timestamp"]:
			var duration: float = b["timestamp"] - a["timestamp"]
			var t: float = (render_time_ms - a["timestamp"]) / maxf(duration, 1.0)
			t = clampf(t, 0.0, 1.0)
			return {
				"position": (a["position"] as Vector3).lerp(b["position"], t),
				"rotation": lerp_angle(a["rotation"], b["rotation"], t),
			}

	if render_time_ms < _buffer[0]["timestamp"]:
		return {"position": _buffer[0]["position"], "rotation": _buffer[0]["rotation"]}

	var last: Dictionary = _buffer.back()
	return {"position": last["position"], "rotation": last["rotation"]}


## Returns the number of snapshots in the buffer.
func buffer_size() -> int:
	return _buffer.size()


## Clears the buffer.
func clear() -> void:
	_buffer.clear()
