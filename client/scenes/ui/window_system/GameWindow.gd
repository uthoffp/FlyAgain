## GameWindow.gd
## Reusable wrapper Control that provides titlebar, drag, resize, minimize,
## close, and focus management for any child panel.
class_name GameWindow
extends PanelContainer

signal window_closed(window_id: String)
signal window_minimized(window_id: String)
signal window_focused(window_id: String)
signal window_restored(window_id: String)

## Configuration — set via setup() or exported properties
var window_id: String = ""
var window_title: String = ""
var draggable: bool = true
var resizable: bool = true
var minimizable: bool = true
var closable: bool = true
var min_size: Vector2 = Vector2(200, 150)
var max_size: Vector2 = Vector2(800, 600)

## Internal nodes
var _title_bar_panel: PanelContainer
var _title_bar: HBoxContainer
var _title_label: Label
var _minimize_button: Button
var _close_button: Button
var _content_container: PanelContainer
var _vbox: VBoxContainer
var _drag_handle: Control

## Drag state
var _dragging: bool = false
var _drag_offset: Vector2 = Vector2.ZERO

## Resize state
const HANDLE_SIZE := 6.0
var _resize_handles: Array[Control] = []
var _resizing: bool = false
var _resize_edge: String = ""
var _resize_start_pos: Vector2 = Vector2.ZERO
var _resize_start_size: Vector2 = Vector2.ZERO
var _resize_start_window_pos: Vector2 = Vector2.ZERO


func setup(id: String, title: String, options: Dictionary = {}) -> void:
	window_id = id
	window_title = title
	draggable = options.get("draggable", true)
	resizable = options.get("resizable", true)
	minimizable = options.get("minimizable", true)
	closable = options.get("closable", true)
	min_size = options.get("min_size", Vector2(200, 150))
	max_size = options.get("max_size", Vector2(800, 600))

	var default_pos: Vector2 = options.get("default_position", Vector2.ZERO)
	var default_sz: Vector2 = options.get("default_size", Vector2(400, 300))

	set_meta("window_id", window_id)
	set_meta("window_title", window_title)
	set_meta("closable", closable)
	set_meta("default_position", default_pos)
	set_meta("default_size", default_sz)

	position = default_pos
	size = default_sz
	custom_minimum_size = min_size

	# Rebuild UI if already built (setup called after _ready)
	if _vbox != null:
		_rebuild_ui()


func _ready() -> void:
	var has_features := draggable or resizable or minimizable or closable
	mouse_filter = Control.MOUSE_FILTER_STOP if has_features else Control.MOUSE_FILTER_IGNORE
	_build_ui()
	_apply_style()

	if window_id.is_empty():
		return
	if WindowManager:
		WindowManager.register_window(self)


func _exit_tree() -> void:
	if WindowManager and not window_id.is_empty():
		WindowManager.unregister_window(self)


func _rebuild_ui() -> void:
	# Remove all existing children immediately
	for child in get_children():
		remove_child(child)
		child.free()
	# Reset node references
	_vbox = null
	_title_bar_panel = null
	_title_bar = null
	_title_label = null
	_minimize_button = null
	_close_button = null
	_content_container = null
	_drag_handle = null
	_resize_handles = []
	_build_ui()
	_apply_style()


func _build_ui() -> void:
	_vbox = VBoxContainer.new()
	_vbox.set_anchors_preset(Control.PRESET_FULL_RECT)
	_vbox.add_theme_constant_override("separation", 0)
	add_child(_vbox)

	# -- Title Bar --
	var show_titlebar := draggable or resizable or minimizable or closable
	_title_bar_panel = PanelContainer.new()
	_title_bar_panel.visible = show_titlebar
	_vbox.add_child(_title_bar_panel)

	_title_bar = HBoxContainer.new()
	_title_bar.add_theme_constant_override("separation", 4)
	_title_bar_panel.add_child(_title_bar)

	# Drag handle (fills remaining space)
	_drag_handle = Control.new()
	_drag_handle.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_drag_handle.custom_minimum_size.y = 28
	_drag_handle.mouse_filter = Control.MOUSE_FILTER_STOP
	_drag_handle.gui_input.connect(_on_drag_handle_input)
	_title_bar.add_child(_drag_handle)

	# Title label (inside drag handle)
	_title_label = Label.new()
	_title_label.text = window_title
	_title_label.set_anchors_preset(Control.PRESET_FULL_RECT)
	_title_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_title_label.add_theme_color_override("font_color", Colors.TEXT_TITLE)
	_title_label.add_theme_font_size_override("font_size", 13)
	_drag_handle.add_child(_title_label)

	# Minimize button
	_minimize_button = Button.new()
	_minimize_button.text = "—"
	_minimize_button.custom_minimum_size = Vector2(24, 24)
	_minimize_button.visible = minimizable
	_minimize_button.pressed.connect(_on_minimize_pressed)
	_title_bar.add_child(_minimize_button)

	# Close button
	_close_button = Button.new()
	_close_button.text = "✕"
	_close_button.custom_minimum_size = Vector2(24, 24)
	_close_button.visible = closable
	_close_button.pressed.connect(_on_close_pressed)
	_title_bar.add_child(_close_button)

	# -- Content Container --
	_content_container = PanelContainer.new()
	_content_container.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_content_container.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_vbox.add_child(_content_container)

	# Build resize handles AFTER the vbox so they sit on top in the scene tree.
	# Godot processes input from last child to first, so handles receive
	# mouse events before the vbox content underneath.
	_build_resize_handles()


func _apply_style() -> void:
	var has_features := draggable or resizable or minimizable or closable
	if has_features:
		# Styled window with background and border
		var style := StyleBoxFlat.new()
		style.bg_color = Colors.BG_PANEL
		style.border_color = Colors.BORDER_PANEL
		style.set_border_width_all(2)
		style.set_corner_radius_all(6)
		add_theme_stylebox_override("panel", style)
	else:
		# Transparent wrapper — zero visual overhead
		var empty := StyleBoxEmpty.new()
		add_theme_stylebox_override("panel", empty)

	# Titlebar background
	if _title_bar_panel and _title_bar_panel.visible:
		var tb_style := StyleBoxFlat.new()
		tb_style.bg_color = Color(Colors.BG_DARK.r, Colors.BG_DARK.g, Colors.BG_DARK.b, 0.8)
		tb_style.set_content_margin_all(4)
		tb_style.set_corner_radius_all(4)
		_title_bar_panel.add_theme_stylebox_override("panel", tb_style)

	# Content container — transparent so content panel's own style shows through
	if _content_container:
		var empty_content := StyleBoxEmpty.new()
		_content_container.add_theme_stylebox_override("panel", empty_content)


# ---- Resize Handles ----

func _build_resize_handles() -> void:
	if not resizable:
		return
	var edges := ["top", "bottom", "left", "right", "top_left", "top_right", "bottom_left", "bottom_right"]
	for edge: String in edges:
		var handle := Control.new()
		handle.mouse_filter = Control.MOUSE_FILTER_STOP
		handle.set_meta("edge", edge)
		handle.mouse_default_cursor_shape = _cursor_for_edge(edge)
		handle.gui_input.connect(_on_resize_input.bind(edge))
		add_child(handle)
		_resize_handles.append(handle)


func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		_position_resize_handles()


func _position_resize_handles() -> void:
	if _resize_handles.is_empty():
		return
	var s := size
	var h := HANDLE_SIZE
	for handle: Control in _resize_handles:
		var edge: String = handle.get_meta("edge")
		match edge:
			"top":
				handle.position = Vector2(h, 0)
				handle.size = Vector2(s.x - 2 * h, h)
			"bottom":
				handle.position = Vector2(h, s.y - h)
				handle.size = Vector2(s.x - 2 * h, h)
			"left":
				handle.position = Vector2(0, h)
				handle.size = Vector2(h, s.y - 2 * h)
			"right":
				handle.position = Vector2(s.x - h, h)
				handle.size = Vector2(h, s.y - 2 * h)
			"top_left":
				handle.position = Vector2(0, 0)
				handle.size = Vector2(h, h)
			"top_right":
				handle.position = Vector2(s.x - h, 0)
				handle.size = Vector2(h, h)
			"bottom_left":
				handle.position = Vector2(0, s.y - h)
				handle.size = Vector2(h, h)
			"bottom_right":
				handle.position = Vector2(s.x - h, s.y - h)
				handle.size = Vector2(h, h)


func _on_resize_input(event: InputEvent, edge: String) -> void:
	if not resizable:
		return
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.pressed:
			_resizing = true
			_resize_edge = edge
			_resize_start_pos = get_global_mouse_position()
			_resize_start_size = size
			_resize_start_window_pos = position
			_emit_focus()
		else:
			_resizing = false
			if WindowManager:
				WindowManager.clamp_to_viewport(self)
				WindowManager.schedule_save()
	elif event is InputEventMouseMotion and _resizing:
		var delta := get_global_mouse_position() - _resize_start_pos
		var new_size := _resize_start_size
		var new_pos := _resize_start_window_pos
		match _resize_edge:
			"right":
				new_size.x = _resize_start_size.x + delta.x
			"bottom":
				new_size.y = _resize_start_size.y + delta.y
			"left":
				new_size.x = _resize_start_size.x - delta.x
				new_pos.x = _resize_start_window_pos.x + delta.x
			"top":
				new_size.y = _resize_start_size.y - delta.y
				new_pos.y = _resize_start_window_pos.y + delta.y
			"bottom_right":
				new_size += delta
			"bottom_left":
				new_size.x = _resize_start_size.x - delta.x
				new_size.y = _resize_start_size.y + delta.y
				new_pos.x = _resize_start_window_pos.x + delta.x
			"top_right":
				new_size.x = _resize_start_size.x + delta.x
				new_size.y = _resize_start_size.y - delta.y
				new_pos.y = _resize_start_window_pos.y + delta.y
			"top_left":
				new_size.x = _resize_start_size.x - delta.x
				new_size.y = _resize_start_size.y - delta.y
				new_pos += delta
		# Clamp size
		new_size.x = clampf(new_size.x, min_size.x, max_size.x)
		new_size.y = clampf(new_size.y, min_size.y, max_size.y)
		# Adjust position for left/top edges so window doesn't jump
		if _resize_edge.contains("left"):
			new_pos.x = _resize_start_window_pos.x + (_resize_start_size.x - new_size.x)
		if _resize_edge.contains("top"):
			new_pos.y = _resize_start_window_pos.y + (_resize_start_size.y - new_size.y)
		size = new_size
		position = new_pos


func _cursor_for_edge(edge: String) -> Control.CursorShape:
	match edge:
		"left", "right":
			return Control.CURSOR_HSIZE
		"top", "bottom":
			return Control.CURSOR_VSIZE
		"top_left", "bottom_right":
			return Control.CURSOR_FDIAGSIZE
		"top_right", "bottom_left":
			return Control.CURSOR_BDIAGSIZE
	return Control.CURSOR_ARROW


# ---- Drag Handling ----

func _on_drag_handle_input(event: InputEvent) -> void:
	if not draggable:
		return
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_LEFT:
			if event.pressed:
				_dragging = true
				_drag_offset = get_global_mouse_position() - global_position
				_emit_focus()
			else:
				_dragging = false
				if WindowManager:
					WindowManager.clamp_to_viewport(self)
	elif event is InputEventMouseMotion and _dragging:
		global_position = get_global_mouse_position() - _drag_offset


# ---- Focus ----

func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed:
		_emit_focus()


func _emit_focus() -> void:
	window_focused.emit(window_id)
	if WindowManager:
		WindowManager.bring_to_front(window_id)


# ---- ESC to Close ----

func _unhandled_input(event: InputEvent) -> void:
	if not visible or not closable:
		return
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		if WindowManager:
			if WindowManager.is_frontmost_closable(window_id):
				WindowManager.close_window(window_id)
				window_closed.emit(window_id)
				get_viewport().set_input_as_handled()
		else:
			visible = false
			window_closed.emit(window_id)
			get_viewport().set_input_as_handled()


# ---- Button Callbacks ----

func _on_minimize_pressed() -> void:
	window_minimized.emit(window_id)
	if WindowManager:
		WindowManager.minimize_window(window_id)
	else:
		visible = false


func _on_close_pressed() -> void:
	if WindowManager:
		WindowManager.close_window(window_id)
	else:
		visible = false
	window_closed.emit(window_id)


# ---- Content API ----

func set_content(node: Control) -> void:
	if _content_container:
		_content_container.add_child(node)


func get_content() -> Control:
	if _content_container and _content_container.get_child_count() > 0:
		return _content_container.get_child(0)
	return null


# ---- Accessors for testing ----

func get_title_bar() -> PanelContainer:
	return _title_bar_panel

func get_title_label() -> Label:
	return _title_label

func get_content_container() -> PanelContainer:
	return _content_container

func get_minimize_button() -> Button:
	return _minimize_button

func get_close_button() -> Button:
	return _close_button

func get_resize_handles() -> Array[Control]:
	return _resize_handles
