## Taskbar.gd
## Dynamic UI bar showing buttons for minimized windows.
## Listens to WindowManager signals to update automatically.
class_name Taskbar
extends PanelContainer

signal taskbar_button_pressed(window_id: String)

var _button_container: BoxContainer
var _current_position: String = "bottom"


func _ready() -> void:
	visible = false
	mouse_filter = Control.MOUSE_FILTER_STOP
	_apply_style()
	_build_container("bottom")

	if WindowManager:
		WindowManager.minimized_list_changed.connect(_on_minimized_changed)
		WindowManager.taskbar_position_changed.connect(_on_position_changed)
		# Ensure taskbar is above all windows
		z_index = 100


func _apply_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(Colors.BG_DARK.r, Colors.BG_DARK.g, Colors.BG_DARK.b, 0.9)
	style.border_color = Colors.BORDER_PANEL
	style.set_border_width_all(1)
	style.set_corner_radius_all(4)
	style.set_content_margin_all(4)
	add_theme_stylebox_override("panel", style)


func _build_container(pos: String) -> void:
	if _button_container:
		_button_container.queue_free()
	_current_position = pos

	if pos in ["top", "bottom"]:
		_button_container = HBoxContainer.new()
	else:
		_button_container = VBoxContainer.new()

	_button_container.add_theme_constant_override("separation", 4)
	add_child(_button_container)
	_update_anchors(pos)


func _update_anchors(pos: String) -> void:
	match pos:
		"bottom":
			set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
			grow_vertical = Control.GROW_DIRECTION_BEGIN
		"top":
			set_anchors_preset(Control.PRESET_TOP_WIDE)
			grow_vertical = Control.GROW_DIRECTION_END
		"left":
			set_anchors_preset(Control.PRESET_LEFT_WIDE)
			grow_horizontal = Control.GROW_DIRECTION_END
			custom_minimum_size.x = 120
		"right":
			set_anchors_preset(Control.PRESET_RIGHT_WIDE)
			grow_horizontal = Control.GROW_DIRECTION_BEGIN
			custom_minimum_size.x = 120


func refresh_buttons(minimized_ids: Array, titles: Dictionary) -> void:
	if not _button_container:
		return
	# Clear existing buttons
	for child in _button_container.get_children():
		child.queue_free()
	# Create new buttons
	for id: String in minimized_ids:
		var btn := Button.new()
		btn.text = titles.get(id, id)
		btn.custom_minimum_size = Vector2(80, 28)
		btn.pressed.connect(_on_button_pressed.bind(id))
		_button_container.add_child(btn)
	visible = minimized_ids.size() > 0


func set_bar_position(pos: String) -> void:
	_build_container(pos)


func get_button_container() -> BoxContainer:
	return _button_container


# ---- Signal Handlers ----

func _on_minimized_changed() -> void:
	if not WindowManager:
		return
	var ids := WindowManager.get_minimized_windows()
	var titles: Dictionary = {}
	for id: String in ids:
		var window: Control = WindowManager.get_game_window(id)
		if window:
			titles[id] = window.get_meta("window_title", id)
	refresh_buttons(ids, titles)


func _on_position_changed(pos: String) -> void:
	set_bar_position(pos)


func _on_button_pressed(window_id: String) -> void:
	taskbar_button_pressed.emit(window_id)
	if WindowManager:
		WindowManager.restore_window(window_id)
