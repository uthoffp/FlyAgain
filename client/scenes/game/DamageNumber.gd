## DamageNumber.gd
## Floating 3D text that shows damage dealt, animates upward and fades out.
extends Label3D

const FLOAT_SPEED := 2.0
const LIFETIME := 1.5
const SPREAD := 0.5

var _elapsed: float = 0.0
var _velocity: Vector3


func _ready() -> void:
	billboard = BaseMaterial3D.BILLBOARD_ENABLED
	no_depth_test = true
	font_size = 64
	outline_size = 8
	outline_modulate = Color(0, 0, 0, 0.8)
	# Random horizontal spread
	var rng_x := randf_range(-SPREAD, SPREAD)
	var rng_z := randf_range(-SPREAD, SPREAD)
	_velocity = Vector3(rng_x, FLOAT_SPEED, rng_z)


func setup(damage: int, is_critical: bool, is_self_damage: bool) -> void:
	text = str(damage)
	if is_critical:
		text = str(damage) + "!"
		modulate = Color(1.0, 0.9, 0.2)  # Yellow for crits
		font_size = 80
	elif is_self_damage:
		modulate = Color(1.0, 0.3, 0.3)  # Red for damage taken
	else:
		modulate = Color(1.0, 1.0, 1.0)  # White for normal


func _process(delta: float) -> void:
	_elapsed += delta
	if _elapsed >= LIFETIME:
		queue_free()
		return
	# Float upward
	position += _velocity * delta
	_velocity.y *= 0.97  # Slow down
	# Fade out in second half
	var fade_start := LIFETIME * 0.5
	if _elapsed > fade_start:
		var t := (_elapsed - fade_start) / (LIFETIME - fade_start)
		modulate.a = 1.0 - t
