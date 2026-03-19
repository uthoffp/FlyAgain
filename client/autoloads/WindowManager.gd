## WindowManager.gd
## Global singleton managing all GameWindow instances: registry, z-order,
## visibility, persistence, and taskbar state.
class_name WindowManagerClass
extends Node

signal window_registered(window: Control)
signal window_unregistered(window: Control)
signal minimized_list_changed
signal taskbar_position_changed(position: String)

## window_id -> Control (GameWindow or mock)
var _windows: Dictionary = {}
## Ordered list of window_ids by z-order (last = frontmost)
var _z_order: Array[String] = []
## Set of minimized window_ids
var _minimized: Dictionary = {}
## Taskbar position: "top", "bottom", "left", "right"
var _taskbar_position: String = "bottom"
## Debounce timer for auto-save
var _save_timer: Timer


func _ready() -> void:
	_save_timer = Timer.new()
	_save_timer.one_shot = true
	_save_timer.wait_time = 1.0
	_save_timer.timeout.connect(_on_save_timer_timeout)
	add_child(_save_timer)


# ---- Registry ----

func register_window(window: Control) -> void:
	var id: String = _get_window_id(window)
	if id.is_empty():
		return
	_windows[id] = window
	_z_order.append(id)
	window_registered.emit(window)


func unregister_window(window: Control) -> void:
	var id: String = _get_window_id(window)
	if id.is_empty():
		return
	_windows.erase(id)
	_z_order.erase(id)
	_minimized.erase(id)
	window_unregistered.emit(window)


func get_window(window_id: String) -> Control:
	return _windows.get(window_id, null)


# ---- Visibility ----

func open_window(window_id: String) -> void:
	var window: Control = get_window(window_id)
	if window == null:
		return
	_minimized.erase(window_id)
	window.visible = true
	bring_to_front(window_id)
	minimized_list_changed.emit()
	schedule_save()


func close_window(window_id: String) -> void:
	var window: Control = get_window(window_id)
	if window == null:
		return
	window.visible = false
	_minimized.erase(window_id)
	minimized_list_changed.emit()
	schedule_save()


func toggle_window(window_id: String) -> void:
	var window: Control = get_window(window_id)
	if window == null:
		return
	if window.visible:
		close_window(window_id)
	else:
		open_window(window_id)


func minimize_window(window_id: String) -> void:
	var window: Control = get_window(window_id)
	if window == null:
		return
	window.visible = false
	_minimized[window_id] = true
	minimized_list_changed.emit()
	schedule_save()


func restore_window(window_id: String) -> void:
	var window: Control = get_window(window_id)
	if window == null:
		return
	_minimized.erase(window_id)
	window.visible = true
	bring_to_front(window_id)
	minimized_list_changed.emit()
	schedule_save()


# ---- Z-Order ----

func bring_to_front(window_id: String) -> void:
	if not _windows.has(window_id):
		return
	_z_order.erase(window_id)
	_z_order.append(window_id)
	_apply_z_order()


func _apply_z_order() -> void:
	for i in range(_z_order.size()):
		var window: Control = get_window(_z_order[i])
		if window != null:
			window.z_index = i


# ---- ESC Focus Check ----

func is_frontmost_closable(window_id: String) -> bool:
	## Returns true if window_id is the frontmost visible, closable window.
	for i in range(_z_order.size() - 1, -1, -1):
		var id: String = _z_order[i]
		var win: Control = get_window(id)
		if win and win.visible and win.get_meta("closable", false):
			return id == window_id
	return false


# ---- Boundary Clamping ----

func clamp_to_viewport(window: Control) -> void:
	var viewport_size := window.get_viewport_rect().size
	var pos := window.position
	var win_size := window.size
	# Ensure at least 50px of window visible horizontally
	pos.x = clampf(pos.x, -win_size.x + 50.0, viewport_size.x - 50.0)
	# Ensure titlebar stays on screen vertically
	pos.y = clampf(pos.y, 0.0, viewport_size.y - 50.0)
	window.position = pos


# ---- Taskbar ----

func get_minimized_windows() -> Array[String]:
	var result: Array[String] = []
	for id: String in _minimized:
		result.append(id)
	return result


func get_taskbar_position() -> String:
	return _taskbar_position


func set_taskbar_position(pos: String) -> void:
	if pos in ["top", "bottom", "left", "right"]:
		_taskbar_position = pos
		taskbar_position_changed.emit(pos)
		schedule_save()


# ---- Persistence ----

func save_layout() -> void:
	var config := ConfigFile.new()
	for id: String in _windows:
		var window: Control = _windows[id]
		config.set_value(id, "position", window.position)
		config.set_value(id, "size", window.size)
		config.set_value(id, "minimized", _minimized.has(id))
	config.set_value("taskbar", "position", _taskbar_position)
	config.save("user://window_layout.cfg")


func load_layout() -> void:
	var config := ConfigFile.new()
	if config.load("user://window_layout.cfg") != OK:
		return
	for id: String in _windows:
		if config.has_section(id):
			var window: Control = _windows[id]
			window.position = config.get_value(id, "position", window.position)
			window.size = config.get_value(id, "size", window.size)
			if config.get_value(id, "minimized", false):
				minimize_window(id)
	if config.has_section("taskbar"):
		_taskbar_position = config.get_value("taskbar", "position", "bottom")
		taskbar_position_changed.emit(_taskbar_position)


func reset_layout() -> void:
	for id: String in _windows:
		var window: Control = _windows[id]
		if window.has_meta("default_position"):
			window.position = window.get_meta("default_position")
		if window.has_meta("default_size"):
			window.size = window.get_meta("default_size")
		_minimized.erase(id)
	_taskbar_position = "bottom"
	minimized_list_changed.emit()
	taskbar_position_changed.emit(_taskbar_position)
	save_layout()


func schedule_save() -> void:
	if _save_timer:
		_save_timer.start()


func _on_save_timer_timeout() -> void:
	save_layout()


# ---- Internal ----

func _get_window_id(window: Control) -> String:
	return window.get_meta("window_id", "")
