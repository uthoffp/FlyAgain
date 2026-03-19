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
