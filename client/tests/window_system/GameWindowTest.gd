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
	var titlebar: PanelContainer = _window.call("get_title_bar")
	assert_object(titlebar).is_not_null()


func test_has_content_container() -> void:
	_window.call("setup", "test", "Test Window", {})
	await get_tree().process_frame
	var container: PanelContainer = _window.call("get_content_container")
	assert_object(container).is_not_null()


func test_set_content_adds_child_to_container() -> void:
	_window.call("setup", "test", "Test Window", {})
	await get_tree().process_frame
	var content: Control = auto_free(Control.new())
	_window.call("set_content", content)
	var container: PanelContainer = _window.call("get_content_container")
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
	var titlebar: PanelContainer = _window.call("get_title_bar")
	assert_bool(titlebar.visible).is_false()


func test_titlebar_visible_when_any_feature_enabled() -> void:
	_window.call("setup", "test", "Test", {
		"draggable": true,
		"resizable": false,
		"minimizable": false,
		"closable": false,
	})
	await get_tree().process_frame
	var titlebar: PanelContainer = _window.call("get_title_bar")
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


# ---- Resize ----

func test_resize_handles_created_when_resizable() -> void:
	_window.call("setup", "test", "Test", {"resizable": true})
	await get_tree().process_frame
	var handles: Array = _window.call("get_resize_handles")
	assert_int(handles.size()).is_equal(8)


func test_no_resize_handles_when_not_resizable() -> void:
	_window.call("setup", "test", "Test", {"resizable": false})
	await get_tree().process_frame
	var handles: Array = _window.call("get_resize_handles")
	assert_int(handles.size()).is_equal(0)


func test_window_respects_min_size() -> void:
	_window.call("setup", "test", "Test", {
		"min_size": Vector2(100, 80),
		"default_size": Vector2(100, 80),
	})
	await get_tree().process_frame
	assert_float(_window.size.x).is_greater_equal(100.0)
	assert_float(_window.size.y).is_greater_equal(80.0)
