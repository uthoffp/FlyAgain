# Reusable Window System Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a reusable, configurable window management system for all HUD panels in the Godot 4 client — supporting drag, resize, minimize-to-taskbar, close, z-order, and local persistence.

**Architecture:** A `GameWindow` wrapper Control wraps each panel. A `WindowManager` autoload manages registry, z-order, persistence, and taskbar state. A `Taskbar` UI renders minimized window buttons at a configurable screen edge.

**Tech Stack:** Godot 4, GDScript, GdUnit4 for tests, ConfigFile for persistence.

**Spec:** `docs/superpowers/specs/2026-03-19-reusable-window-system-design.md`

---

## File Structure

**New files:**
| File | Responsibility |
|------|---------------|
| `client/autoloads/WindowManager.gd` | Autoload: registry, z-order, persistence, taskbar state |
| `client/scenes/ui/window_system/GameWindow.gd` | Wrapper Control: titlebar, drag, resize, minimize, close, focus |
| `client/scenes/ui/window_system/Taskbar.gd` | Taskbar UI: renders minimized window buttons |
| `client/tests/autoloads/WindowManagerTest.gd` | Tests for WindowManager |
| `client/tests/window_system/GameWindowTest.gd` | Tests for GameWindow |
| `client/tests/window_system/TaskbarTest.gd` | Tests for Taskbar |

**Modified files:**
| File | What changes |
|------|-------------|
| `client/project.godot` (line 23) | Add WindowManager autoload |
| `client/scenes/game/GameWorld.gd` (lines 210–341) | Refactor `_setup_hud()` to wrap panels in GameWindow |
| `client/scenes/game/GameWorld.gd` (lines 100–107) | Refactor `_unhandled_key_input()` to use WindowManager |
| `client/scenes/ui/game_hud/InventoryScreen.gd` (lines 72–76) | Remove ESC handler (GameWindow owns close) |
| `client/scenes/ui/game_hud/NpcShopScreen.gd` (lines 74–79) | Remove ESC handler |
| `client/scenes/ui/game_hud/NpcDialog.gd` (lines 39–44) | Remove ESC handler |
| `client/translations/translations.en.translation` | Add WINDOW_* keys |
| `client/translations/translations.de.translation` | Add WINDOW_* keys |

---

## Task 1: WindowManager Autoload — Registry & Z-Order

**Files:**
- Create: `client/autoloads/WindowManager.gd`
- Create: `client/tests/autoloads/WindowManagerTest.gd`
- Modify: `client/project.godot` (line 23)

This task builds the core WindowManager with registry and z-order management. Persistence and taskbar come in later tasks.

- [ ] **Step 1: Write failing tests for registry**

Create `client/tests/autoloads/WindowManagerTest.gd`:

```gdscript
## WindowManagerTest.gd
## Tests for WindowManager autoload: registry, z-order, visibility.
class_name WindowManagerTest
extends GdUnitTestSuite


var _manager: Node


func before_test() -> void:
	_manager = auto_free(Node.new())
	_manager.set_script(load("res://autoloads/WindowManager.gd"))


# ---- Registry ----

func test_register_window_adds_to_registry() -> void:
	var window := _create_mock_window("test_win")
	_manager.register_window(window)
	assert_object(_manager.get_window("test_win")).is_equal(window)


func test_unregister_window_removes_from_registry() -> void:
	var window := _create_mock_window("test_win")
	_manager.register_window(window)
	_manager.unregister_window(window)
	assert_object(_manager.get_window("test_win")).is_null()


func test_get_window_returns_null_for_unknown_id() -> void:
	assert_object(_manager.get_window("nonexistent")).is_null()


func test_register_emits_signal() -> void:
	var window := _create_mock_window("sig_test")
	await assert_signal(_manager).is_emitted("window_registered", [window])
	_manager.register_window(window)


func test_unregister_emits_signal() -> void:
	var window := _create_mock_window("sig_test")
	_manager.register_window(window)
	await assert_signal(_manager).is_emitted("window_unregistered", [window])
	_manager.unregister_window(window)


# ---- Z-Order ----

func test_bring_to_front_sets_highest_z_index() -> void:
	var win_a := _create_mock_window("a")
	var win_b := _create_mock_window("b")
	_add_to_scene(win_a)
	_add_to_scene(win_b)
	_manager.register_window(win_a)
	_manager.register_window(win_b)
	_manager.bring_to_front("a")
	assert_int(win_a.z_index).is_greater(win_b.z_index)


func test_bring_to_front_updates_on_second_call() -> void:
	var win_a := _create_mock_window("a")
	var win_b := _create_mock_window("b")
	_add_to_scene(win_a)
	_add_to_scene(win_b)
	_manager.register_window(win_a)
	_manager.register_window(win_b)
	_manager.bring_to_front("a")
	_manager.bring_to_front("b")
	assert_int(win_b.z_index).is_greater(win_a.z_index)


# ---- Visibility ----

func test_open_window_makes_visible() -> void:
	var window := _create_mock_window("vis_test")
	_add_to_scene(window)
	_manager.register_window(window)
	window.visible = false
	_manager.open_window("vis_test")
	assert_bool(window.visible).is_true()


func test_close_window_makes_invisible() -> void:
	var window := _create_mock_window("vis_test")
	_add_to_scene(window)
	_manager.register_window(window)
	window.visible = true
	_manager.close_window("vis_test")
	assert_bool(window.visible).is_false()


func test_toggle_window_flips_visibility() -> void:
	var window := _create_mock_window("toggle_test")
	_add_to_scene(window)
	_manager.register_window(window)
	window.visible = false
	_manager.toggle_window("toggle_test")
	assert_bool(window.visible).is_true()
	_manager.toggle_window("toggle_test")
	assert_bool(window.visible).is_false()


# ---- Boundary Clamping ----

func test_clamp_to_viewport_keeps_window_on_screen() -> void:
	var window := _create_mock_window("clamp_test")
	_add_to_scene(window)
	_manager.register_window(window)
	window.position = Vector2(-9999, -9999)
	window.size = Vector2(200, 100)
	_manager.clamp_to_viewport(window)
	# At least 50px of window should remain visible
	assert_float(window.position.x + window.size.x).is_greater_equal(50.0)
	assert_float(window.position.y).is_greater_equal(0.0)


# ---- Helpers ----

func _create_mock_window(id: String) -> Control:
	var win := auto_free(Control.new())
	win.set_meta("window_id", id)
	win.set_meta("window_title", id)
	return win


func _add_to_scene(node: Control) -> void:
	add_child(node)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/autoloads/WindowManagerTest.gd`
Expected: FAIL — WindowManager.gd does not exist yet.

- [ ] **Step 3: Implement WindowManager**

Create `client/autoloads/WindowManager.gd`:

```gdscript
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
```

- [ ] **Step 4: Register autoload in project.godot**

Add after line 23 in `client/project.godot`:
```
WindowManager="*res://autoloads/WindowManager.gd"
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/autoloads/WindowManagerTest.gd`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add client/autoloads/WindowManager.gd client/tests/autoloads/WindowManagerTest.gd client/project.godot
git commit -m "feat: add WindowManager autoload with registry, z-order, visibility, persistence"
```

---

## Task 2: GameWindow Wrapper — Structure, Drag & Focus

**Files:**
- Create: `client/scenes/ui/window_system/GameWindow.gd`
- Create: `client/tests/window_system/GameWindowTest.gd`

This task builds the GameWindow wrapper with titlebar, drag, and focus behavior. Resize and minimize come in the next tasks.

- [ ] **Step 1: Write failing tests for GameWindow structure and drag**

Create `client/tests/window_system/GameWindowTest.gd`:

```gdscript
## GameWindowTest.gd
## Tests for GameWindow wrapper: structure, drag, focus, close.
class_name GameWindowTest
extends GdUnitTestSuite


var _window: Control


func before_test() -> void:
	_window = auto_free(Control.new())
	_window.set_script(load("res://scenes/ui/window_system/GameWindow.gd"))
	add_child(_window)


# ---- Structure ----

func test_has_title_bar() -> void:
	_window.call("setup", "test", "Test Window", {})
	await get_tree().process_frame
	var titlebar := _window.call("get_title_bar")
	assert_object(titlebar).is_not_null()


func test_has_content_container() -> void:
	_window.call("setup", "test", "Test Window", {})
	await get_tree().process_frame
	var container := _window.call("get_content_container")
	assert_object(container).is_not_null()


func test_set_content_adds_child_to_container() -> void:
	_window.call("setup", "test", "Test Window", {})
	await get_tree().process_frame
	var content := auto_free(Control.new())
	_window.call("set_content", content)
	var container := _window.call("get_content_container")
	assert_int(container.get_child_count()).is_equal(1)


func test_title_label_shows_window_title() -> void:
	_window.call("setup", "test", "My Title", {})
	await get_tree().process_frame
	var label: Label = _window.call("get_title_label")
	assert_str(label.text).is_equal("My Title")


# ---- Titlebar hidden when all features disabled ----

func test_titlebar_hidden_when_all_disabled() -> void:
	_window.call("setup", "test", "Test", {
		"draggable": false,
		"resizable": false,
		"minimizable": false,
		"closable": false,
	})
	await get_tree().process_frame
	var titlebar := _window.call("get_title_bar")
	assert_bool(titlebar.visible).is_false()


func test_titlebar_visible_when_any_feature_enabled() -> void:
	_window.call("setup", "test", "Test", {
		"draggable": true,
		"resizable": false,
		"minimizable": false,
		"closable": false,
	})
	await get_tree().process_frame
	var titlebar := _window.call("get_title_bar")
	assert_bool(titlebar.visible).is_true()


# ---- Close ----

func test_close_button_hidden_when_not_closable() -> void:
	_window.call("setup", "test", "Test", {"closable": false})
	await get_tree().process_frame
	var close_btn: Button = _window.call("get_close_button")
	assert_bool(close_btn.visible).is_false()


func test_close_button_visible_when_closable() -> void:
	_window.call("setup", "test", "Test", {"closable": true})
	await get_tree().process_frame
	var close_btn: Button = _window.call("get_close_button")
	assert_bool(close_btn.visible).is_true()


# ---- Meta ----

func test_window_id_stored_as_meta() -> void:
	_window.call("setup", "my_window", "My Window", {})
	assert_str(_window.get_meta("window_id")).is_equal("my_window")


func test_default_position_stored_as_meta() -> void:
	_window.call("setup", "test", "Test", {"default_position": Vector2(50, 75)})
	assert_object(_window.get_meta("default_position")).is_equal(Vector2(50, 75))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/window_system/GameWindowTest.gd`
Expected: FAIL — GameWindow.gd does not exist.

- [ ] **Step 3: Implement GameWindow core**

Create `client/scenes/ui/window_system/GameWindow.gd`:

```gdscript
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


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	_build_ui()
	_apply_style()

	if window_id.is_empty():
		return
	if WindowManager:
		WindowManager.register_window(self)


func _exit_tree() -> void:
	if WindowManager and not window_id.is_empty():
		WindowManager.unregister_window(self)


func _build_ui() -> void:
	_vbox = VBoxContainer.new()
	_vbox.set_anchors_preset(Control.PRESET_FULL_RECT)
	_vbox.add_theme_constant_override("separation", 0)
	add_child(_vbox)

	# -- Title Bar --
	_title_bar = HBoxContainer.new()
	_title_bar.add_theme_constant_override("separation", 4)
	_vbox.add_child(_title_bar)

	var show_titlebar := draggable or resizable or minimizable or closable
	_title_bar.visible = show_titlebar

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


func _apply_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Colors.BG_PANEL
	style.border_color = Colors.BORDER_PANEL
	style.set_border_width_all(2)
	style.set_corner_radius_all(6)
	add_theme_stylebox_override("panel", style)

	# Titlebar background
	if _title_bar.visible:
		var tb_style := StyleBoxFlat.new()
		tb_style.bg_color = Color(Colors.BG_DARK.r, Colors.BG_DARK.g, Colors.BG_DARK.b, 0.8)
		tb_style.set_content_margin_all(4)
		tb_style.set_corner_radius_all(4)
		_title_bar.add_theme_stylebox_override("panel", tb_style)


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
# Note: ESC is handled via _unhandled_input so only one window closes.
# WindowManager.close_frontmost_closable() finds the top z-order closable
# window and closes only that one.

func _unhandled_input(event: InputEvent) -> void:
	if not visible or not closable:
		return
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		if WindowManager:
			# Only the frontmost closable window should respond
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

func get_title_bar() -> HBoxContainer:
	return _title_bar

func get_title_label() -> Label:
	return _title_label

func get_content_container() -> PanelContainer:
	return _content_container

func get_minimize_button() -> Button:
	return _minimize_button

func get_close_button() -> Button:
	return _close_button
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/window_system/GameWindowTest.gd`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add client/scenes/ui/window_system/GameWindow.gd client/tests/window_system/GameWindowTest.gd
git commit -m "feat: add GameWindow wrapper with titlebar, drag, focus, and close"
```

---

## Task 3: GameWindow — Resize Handles

**Files:**
- Modify: `client/scenes/ui/window_system/GameWindow.gd`
- Modify: `client/tests/window_system/GameWindowTest.gd`

Adds 8 resize handles (4 edges + 4 corners) to GameWindow.

- [ ] **Step 1: Write failing tests for resize**

Append to `client/tests/window_system/GameWindowTest.gd`:

```gdscript
# ---- Resize ----

func test_resize_handles_created_when_resizable() -> void:
	_window.call("setup", "test", "Test", {"resizable": true})
	await get_tree().process_frame
	var handles: Array = _window.call("get_resize_handles")
	assert_int(handles.size()).is_equal(8)


func test_resize_handles_hidden_when_not_resizable() -> void:
	_window.call("setup", "test", "Test", {"resizable": false})
	await get_tree().process_frame
	var handles: Array = _window.call("get_resize_handles")
	for handle: Control in handles:
		assert_bool(handle.visible).is_false()


func test_window_respects_min_size() -> void:
	_window.call("setup", "test", "Test", {
		"min_size": Vector2(100, 80),
		"default_size": Vector2(100, 80),
	})
	await get_tree().process_frame
	assert_float(_window.size.x).is_greater_equal(100.0)
	assert_float(_window.size.y).is_greater_equal(80.0)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/window_system/GameWindowTest.gd`
Expected: New tests FAIL — `get_resize_handles` not found.

- [ ] **Step 3: Implement resize handles in GameWindow**

Add to `client/scenes/ui/window_system/GameWindow.gd`:

```gdscript
## Add to class variables:
const HANDLE_SIZE := 6.0
var _resize_handles: Array[Control] = []
var _resizing: bool = false
var _resize_edge: String = ""
var _resize_start_pos: Vector2 = Vector2.ZERO
var _resize_start_size: Vector2 = Vector2.ZERO
var _resize_start_window_pos: Vector2 = Vector2.ZERO

## Add to _build_ui(), after content container:
func _build_resize_handles() -> void:
	var edges := ["top", "bottom", "left", "right", "top_left", "top_right", "bottom_left", "bottom_right"]
	for edge: String in edges:
		var handle := Control.new()
		handle.mouse_filter = Control.MOUSE_FILTER_STOP
		handle.visible = resizable
		handle.set_meta("edge", edge)
		handle.gui_input.connect(_on_resize_input.bind(edge))
		handle.mouse_entered.connect(_on_resize_hover.bind(edge))
		handle.mouse_exited.connect(_on_resize_hover_exit)
		add_child(handle)
		_resize_handles.append(handle)

## Call _build_resize_handles() at the end of _build_ui()

## Position handles on size change:
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

func _on_resize_hover(edge: String) -> void:
	if not resizable:
		return
	match edge:
		"left", "right":
			mouse_default_cursor_shape = Control.CURSOR_HSIZE
		"top", "bottom":
			mouse_default_cursor_shape = Control.CURSOR_VSIZE
		"top_left", "bottom_right":
			mouse_default_cursor_shape = Control.CURSOR_FDIAGSIZE
		"top_right", "bottom_left":
			mouse_default_cursor_shape = Control.CURSOR_BDIAGSIZE

func _on_resize_hover_exit() -> void:
	mouse_default_cursor_shape = Control.CURSOR_ARROW

func get_resize_handles() -> Array[Control]:
	return _resize_handles
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/window_system/GameWindowTest.gd`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add client/scenes/ui/window_system/GameWindow.gd client/tests/window_system/GameWindowTest.gd
git commit -m "feat: add resize handles to GameWindow (edges and corners)"
```

---

## Task 4: Taskbar

**Files:**
- Create: `client/scenes/ui/window_system/Taskbar.gd`
- Create: `client/tests/window_system/TaskbarTest.gd`

- [ ] **Step 1: Write failing tests for Taskbar**

Create `client/tests/window_system/TaskbarTest.gd`:

```gdscript
## TaskbarTest.gd
## Tests for Taskbar: button creation, positioning, auto-hide.
class_name TaskbarTest
extends GdUnitTestSuite


var _taskbar: PanelContainer


func before_test() -> void:
	_taskbar = auto_free(PanelContainer.new())
	_taskbar.set_script(load("res://scenes/ui/window_system/Taskbar.gd"))
	add_child(_taskbar)


func test_initially_hidden_when_no_minimized_windows() -> void:
	await get_tree().process_frame
	assert_bool(_taskbar.visible).is_false()


func test_creates_button_for_minimized_window() -> void:
	# Simulate WindowManager state
	_taskbar.call("refresh_buttons", ["inventory"], {"inventory": "Inventory"})
	await get_tree().process_frame
	var container: BoxContainer = _taskbar.call("get_button_container")
	assert_int(container.get_child_count()).is_equal(1)


func test_shows_when_windows_minimized() -> void:
	_taskbar.call("refresh_buttons", ["inventory"], {"inventory": "Inventory"})
	await get_tree().process_frame
	assert_bool(_taskbar.visible).is_true()


func test_hides_when_all_restored() -> void:
	_taskbar.call("refresh_buttons", ["inventory"], {"inventory": "Inventory"})
	await get_tree().process_frame
	_taskbar.call("refresh_buttons", [], {})
	await get_tree().process_frame
	assert_bool(_taskbar.visible).is_false()


func test_button_text_matches_window_title() -> void:
	_taskbar.call("refresh_buttons", ["shop"], {"shop": "Shop"})
	await get_tree().process_frame
	var container: BoxContainer = _taskbar.call("get_button_container")
	var btn: Button = container.get_child(0)
	assert_str(btn.text).is_equal("Shop")


func test_updates_orientation_for_bottom() -> void:
	_taskbar.call("set_bar_position", "bottom")
	await get_tree().process_frame
	var container: BoxContainer = _taskbar.call("get_button_container")
	assert_object(container).is_instanceof(HBoxContainer)


func test_updates_orientation_for_left() -> void:
	_taskbar.call("set_bar_position", "left")
	await get_tree().process_frame
	var container: BoxContainer = _taskbar.call("get_button_container")
	assert_object(container).is_instanceof(VBoxContainer)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/window_system/TaskbarTest.gd`
Expected: FAIL — Taskbar.gd does not exist.

- [ ] **Step 3: Implement Taskbar**

Create `client/scenes/ui/window_system/Taskbar.gd`:

```gdscript
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
		var window: Control = WindowManager.get_window(id)
		if window:
			titles[id] = window.get_meta("window_title", id)
	refresh_buttons(ids, titles)


func _on_position_changed(pos: String) -> void:
	set_bar_position(pos)


func _on_button_pressed(window_id: String) -> void:
	taskbar_button_pressed.emit(window_id)
	if WindowManager:
		WindowManager.restore_window(window_id)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/window_system/TaskbarTest.gd`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add client/scenes/ui/window_system/Taskbar.gd client/tests/window_system/TaskbarTest.gd
git commit -m "feat: add Taskbar with dynamic buttons, auto-hide, and configurable position"
```

---

## Task 5: Localization — Add Translation Keys

**Files:**
- Modify: `client/translations/translations.en.translation`
- Modify: `client/translations/translations.de.translation`

Note: These are compiled Godot translation files. The project may use `.csv` or `.po` source files that compile to `.translation`. Check for source files first. If only `.translation` files exist, the keys must be added via Godot's translation system or by editing the source format.

- [ ] **Step 1: Check for translation source files**

Run: `find client/ -name "*.csv" -o -name "*.po" -o -name "*.pot" | head -20`

Depending on what exists:
- If `.csv` exists: add rows for new keys
- If `.po` files exist: add msgid/msgstr entries
- If only `.translation` files: keys need to be added programmatically or via Godot editor

- [ ] **Step 2: Add translation keys**

Add the following keys (format depends on Step 1 findings):

| Key | EN | DE |
|-----|----|----|
| `WINDOW_INVENTORY` | Inventory | Inventar |
| `WINDOW_SHOP` | Shop | Laden |
| `WINDOW_NPC_DIALOG` | Dialog | Dialog |
| `WINDOW_PLAYER` | Player | Spieler |
| `WINDOW_TARGET` | Target | Ziel |
| `WINDOW_SKILLS` | Skills | Fähigkeiten |
| `WINDOW_MINIMIZE` | Minimize | Minimieren |
| `WINDOW_CLOSE` | Close | Schließen |
| `WINDOW_RESTORE` | Restore | Wiederherstellen |

- [ ] **Step 3: Commit**

```bash
git add client/translations/
git commit -m "feat: add window system translation keys (EN + DE)"
```

---

## Task 6: Integration — Refactor GameWorld._setup_hud()

**Files:**
- Modify: `client/scenes/game/GameWorld.gd` (lines 210–341, 100–107)

This is the largest integration task. It replaces all CenterContainer/MarginContainer wrappers with GameWindow wrappers.

- [ ] **Step 1: Read current _setup_hud() to get exact line numbers**

Read `client/scenes/game/GameWorld.gd` lines 200–350 to identify each panel's creation block.

- [ ] **Step 2: Add GameWindow script preload**

At the top of `GameWorld.gd` where other scripts are preloaded, add:

```gdscript
const GameWindowScript := preload("res://scenes/ui/window_system/GameWindow.gd")
const TaskbarScript := preload("res://scenes/ui/window_system/Taskbar.gd")
```

- [ ] **Step 3: Refactor PlayerFrame creation**

Replace the PlayerFrame MarginContainer wrapper with a GameWindow (all features disabled):

```gdscript
var player_window := PanelContainer.new()
player_window.set_script(GameWindowScript)
player_window.call("setup", "player_frame", tr("WINDOW_PLAYER"), {
	"draggable": false, "resizable": false,
	"minimizable": false, "closable": false,
	"default_position": Vector2(10, 10),
	"default_size": Vector2(220, 120),
})
_hud_root.add_child(player_window)
var player_frame := PanelContainer.new()
player_frame.set_script(PlayerFrameScript)
player_window.call("set_content", player_frame)
```

- [ ] **Step 4: Refactor TargetFrame creation**

Replace with GameWindow (features disabled, positioned top-center):

```gdscript
var target_window := PanelContainer.new()
target_window.set_script(GameWindowScript)
target_window.call("setup", "target_frame", tr("WINDOW_TARGET"), {
	"draggable": false, "resizable": false,
	"minimizable": false, "closable": false,
	"default_position": Vector2(540, 10),
	"default_size": Vector2(200, 80),
})
_hud_root.add_child(target_window)
_target_frame = PanelContainer.new()
_target_frame.set_script(TargetFrameScript)
target_window.call("set_content", _target_frame)
```

- [ ] **Step 5: Refactor SkillBar creation**

Replace with GameWindow (features disabled, positioned bottom-center).

- [ ] **Step 6: Refactor NotificationStack creation**

Replace with GameWindow (features disabled, positioned right side).

- [ ] **Step 7: Refactor InventoryScreen creation**

Replace with GameWindow (all features ENABLED):

```gdscript
var inv_window := PanelContainer.new()
inv_window.set_script(GameWindowScript)
inv_window.call("setup", "inventory", tr("WINDOW_INVENTORY"), {
	"draggable": true, "resizable": true,
	"minimizable": true, "closable": true,
	"default_position": Vector2(100, 100),
	"default_size": Vector2(450, 500),
	"min_size": Vector2(350, 400),
	"max_size": Vector2(600, 700),
})
inv_window.visible = false
_hud_root.add_child(inv_window)
_inventory_screen = PanelContainer.new()
_inventory_screen.set_script(InventoryScreenScript)
inv_window.call("set_content", _inventory_screen)
```

- [ ] **Step 8: Refactor NpcDialog creation**

Replace with GameWindow (draggable, closable, not resizable/minimizable):

```gdscript
var dialog_window := PanelContainer.new()
dialog_window.set_script(GameWindowScript)
dialog_window.call("setup", "npc_dialog", tr("WINDOW_NPC_DIALOG"), {
	"draggable": true, "resizable": false,
	"minimizable": false, "closable": true,
	"default_position": Vector2(400, 300),
	"default_size": Vector2(300, 200),
})
dialog_window.visible = false
_hud_root.add_child(dialog_window)
_npc_dialog = PanelContainer.new()
_npc_dialog.set_script(NpcDialogScript)
dialog_window.call("set_content", _npc_dialog)
```

- [ ] **Step 9: Refactor NpcShopScreen creation**

Replace with GameWindow (all features enabled):

```gdscript
var shop_window := PanelContainer.new()
shop_window.set_script(GameWindowScript)
shop_window.call("setup", "npc_shop", tr("WINDOW_SHOP"), {
	"draggable": true, "resizable": true,
	"minimizable": true, "closable": true,
	"default_position": Vector2(300, 80),
	"default_size": Vector2(500, 550),
	"min_size": Vector2(400, 450),
	"max_size": Vector2(700, 700),
})
shop_window.visible = false
_hud_root.add_child(shop_window)
_npc_shop_screen = PanelContainer.new()
_npc_shop_screen.set_script(NpcShopScreenScript)
shop_window.call("set_content", _npc_shop_screen)
```

- [ ] **Step 10: Refactor DeathScreen creation**

DeathScreen is a full-screen overlay. Wrap with GameWindow (all features disabled):

```gdscript
var death_window := PanelContainer.new()
death_window.set_script(GameWindowScript)
death_window.call("setup", "death_screen", "", {
	"draggable": false, "resizable": false,
	"minimizable": false, "closable": false,
})
_hud_root.add_child(death_window)
_death_screen = ColorRect.new()
_death_screen.set_script(DeathScreenScript)
death_window.call("set_content", _death_screen)
```

- [ ] **Step 11: Add Taskbar to HUD**

Add at end of `_setup_hud()`, before DeathScreen (so death screen overlays it):

```gdscript
var taskbar := PanelContainer.new()
taskbar.set_script(TaskbarScript)
_hud_root.add_child(taskbar)
```

- [ ] **Step 12: Refactor hotkey handling**

In `_unhandled_key_input()` (around line 100–107), change:

```gdscript
# Before:
KEY_I:
	if _inventory_screen and _inventory_screen.has_method("toggle"):
		_inventory_screen.toggle()
		get_viewport().set_input_as_handled()

# After:
KEY_I:
	WindowManager.toggle_window("inventory")
	get_viewport().set_input_as_handled()
```

- [ ] **Step 13: Connect visibility refresh for InventoryScreen**

InventoryScreen.toggle() previously called `_refresh_all()` on open. Since WindowManager now controls visibility, connect InventoryScreen's `visibility_changed` signal to trigger refresh:

```gdscript
# After creating _inventory_screen and adding to inv_window:
_inventory_screen.visibility_changed.connect(func():
	if _inventory_screen.visible and _inventory_screen.has_method("_refresh_all"):
		_inventory_screen._refresh_all()
)
```

- [ ] **Step 14: Commit**

```bash
git add client/scenes/game/GameWorld.gd
git commit -m "feat: refactor _setup_hud() to wrap all panels in GameWindow"
```

---

## Task 7: Remove ESC Handlers from Panels

**Files:**
- Modify: `client/scenes/ui/game_hud/InventoryScreen.gd` (lines 72–76)
- Modify: `client/scenes/ui/game_hud/NpcShopScreen.gd` (lines 74–79)
- Modify: `client/scenes/ui/game_hud/NpcDialog.gd` (lines 39–44)

GameWindow now owns ESC-to-close behavior. Remove duplicate handlers from panels.

- [ ] **Step 1: Remove ESC handler from InventoryScreen.gd**

In `_input()` (line 72–81), remove the ESC handling lines but **keep** the drag cancellation logic:

```gdscript
# Before (lines 72-81):
func _input(event: InputEvent) -> void:
	if not visible:
		return
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		visible = false
		get_viewport().set_input_as_handled()
	if _dragging and event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and not event.pressed:
		_cancel_drag()

# After:
func _input(event: InputEvent) -> void:
	if not visible:
		return
	if _dragging and event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and not event.pressed:
		_cancel_drag()
```

- [ ] **Step 2: Remove ESC handler from NpcShopScreen.gd**

Remove the entire `_input()` function (lines 74–79) since it only contained ESC handling.

- [ ] **Step 3: Remove ESC handler from NpcDialog.gd**

Remove the entire `_input()` function (lines 39–44) since it only contained ESC handling.

- [ ] **Step 4: Commit**

```bash
git add client/scenes/ui/game_hud/InventoryScreen.gd client/scenes/ui/game_hud/NpcShopScreen.gd client/scenes/ui/game_hud/NpcDialog.gd
git commit -m "refactor: remove ESC handlers from panels (GameWindow owns close behavior)"
```

---

## Task 8: Smoke Test & Layout Persistence Verification

**Files:**
- No new files — manual verification

- [ ] **Step 1: Run all existing tests**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/`
Expected: All tests PASS (existing + new).

- [ ] **Step 2: Run all window system tests specifically**

Run: `cd client && godot --headless --script addons/gdUnit4/bin/GdUnitCmdTool.gd -a res://tests/autoloads/WindowManagerTest.gd -a res://tests/window_system/`
Expected: All PASS.

- [ ] **Step 3: Commit final state**

If all tests pass, no additional commit needed — all changes already committed in previous tasks.
