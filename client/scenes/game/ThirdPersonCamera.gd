## ThirdPersonCamera.gd
## Orbiting third-person camera using SpringArm3D.
## Attached to a child Node3D of the player character (CameraPivot).
##
## Controls:
##   - Hold right mouse button + move mouse: rotate camera
##   - Scroll wheel: zoom in/out
##   - Mouse cursor is visible by default (MMO-style)
extends Node3D


@export var mouse_sensitivity := 0.003
@export var min_pitch := deg_to_rad(-80.0)
@export var max_pitch := deg_to_rad(60.0)
@export var min_distance := 2.0
@export var max_distance := 20.0
@export var zoom_speed := 1.0
@export var default_distance := 8.0

var _yaw: float = 0.0
var _pitch: float = deg_to_rad(-20.0)
var _is_right_mouse_held: bool = false

@onready var _spring_arm: SpringArm3D = $SpringArm3D
@onready var _camera: Camera3D = $SpringArm3D/Camera3D


func _ready() -> void:
	_spring_arm.spring_length = default_distance
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE


func _exit_tree() -> void:
	Input.mouse_mode = Input.MOUSE_MODE_VISIBLE


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_WINDOW_FOCUS_OUT:
		if _is_right_mouse_held:
			_is_right_mouse_held = false
			Input.mouse_mode = Input.MOUSE_MODE_VISIBLE


func _unhandled_input(event: InputEvent) -> void:
	# Right-click hold to rotate camera
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_RIGHT:
		if event.pressed:
			_is_right_mouse_held = true
			Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
		else:
			_is_right_mouse_held = false
			Input.mouse_mode = Input.MOUSE_MODE_VISIBLE

	# Camera rotation via mouse motion (only while right-click held)
	if event is InputEventMouseMotion and _is_right_mouse_held:
		_yaw -= event.relative.x * mouse_sensitivity
		_pitch -= event.relative.y * mouse_sensitivity
		_pitch = clampf(_pitch, min_pitch, max_pitch)

	# Zoom via scroll wheel
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_WHEEL_UP and event.pressed:
			_spring_arm.spring_length = maxf(_spring_arm.spring_length - zoom_speed, min_distance)
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN and event.pressed:
			_spring_arm.spring_length = minf(_spring_arm.spring_length + zoom_speed, max_distance)


func _process(_delta: float) -> void:
	rotation = Vector3(_pitch, _yaw, 0.0)


## Returns true when the player is holding right-click to rotate the camera.
func is_rotating() -> bool:
	return _is_right_mouse_held


## Returns the camera's horizontal forward direction (Y zeroed, for movement).
func get_camera_forward() -> Vector3:
	var forward := -global_transform.basis.z
	forward.y = 0.0
	return forward.normalized() if forward.length() > 0.001 else Vector3.FORWARD


## Returns the camera's horizontal right direction (Y zeroed, for movement).
func get_camera_right() -> Vector3:
	var right := global_transform.basis.x
	right.y = 0.0
	return right.normalized() if right.length() > 0.001 else Vector3.RIGHT
