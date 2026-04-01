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
