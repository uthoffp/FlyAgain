## UIManager.gd  (Autoload: "UIManager")
## Manages screen transitions with fade animation.
##
## Usage:
##   UIManager.push_screen("login")      # Navigate to new screen, add to stack
##   UIManager.pop_screen()              # Return to previous screen
##   UIManager.replace_screen("login")   # Replace stack (no back navigation)
##
## Screen names are defined in SCREENS below.
extends Node

# ---- Screen registry ----

const SCREENS: Dictionary = {
	"login":       "res://scenes/ui/screens/LoginScreen.tscn",
	"register":    "res://scenes/ui/screens/RegisterScreen.tscn",
	"char_select": "res://scenes/ui/screens/CharacterSelectScreen.tscn",
	"char_create": "res://scenes/ui/screens/CharacterCreateScreen.tscn",
}

const FADE_DURATION := 0.2

# ---- Internal state ----

var _stack:         Array[String] = []
var _fade_layer:    CanvasLayer
var _fade_rect:     ColorRect
var _transitioning: bool = false


# ---- Lifecycle ----

func _ready() -> void:
	_setup_fade_overlay()


func _setup_fade_overlay() -> void:
	_fade_layer       = CanvasLayer.new()
	_fade_layer.layer = 128
	_fade_layer.name  = "FadeLayer"
	add_child(_fade_layer)

	_fade_rect              = ColorRect.new()
	_fade_rect.name         = "FadeRect"
	_fade_rect.color        = Color.BLACK
	_fade_rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_fade_rect.modulate.a   = 0.0
	_fade_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	_fade_layer.add_child(_fade_rect)


# ---- Public API ----

## Registers the initial screen without a transition.
## Call this in _ready() of the scene set as main_scene in project.godot,
## so the navigation stack is correctly seeded for pop_screen() to work.
func set_initial_screen(screen_name: String) -> void:
	if _stack.is_empty():
		_stack.push_back(screen_name)


## Pushes a new screen onto the navigation stack.
## Use pop_screen() to return to the current screen.
func push_screen(screen_name: String) -> void:
	if _transitioning:
		return
	_stack.push_back(screen_name)
	_transition_to(screen_name)


## Pops the current screen and returns to the previous one.
## Does nothing if there is only one screen on the stack.
func pop_screen() -> void:
	if _transitioning or _stack.size() <= 1:
		return
	_stack.pop_back()
	_transition_to(_stack.back())


## Replaces the entire navigation stack with a single screen.
## Use for top-level navigation (e.g., logout → login).
func replace_screen(screen_name: String) -> void:
	if _transitioning:
		return
	_stack.clear()
	_stack.push_back(screen_name)
	_transition_to(screen_name)


## Returns the name of the current screen, or "" if stack is empty.
func current_screen() -> String:
	if _stack.is_empty():
		return ""
	return _stack.back()


# ---- Internal ----

func _transition_to(screen_name: String) -> void:
	if not SCREENS.has(screen_name):
		push_error("UIManager: unknown screen '%s'" % screen_name)
		return

	_transitioning = true
	_resize_fade_rect()

	# Fade out
	var tween := create_tween()
	tween.tween_property(_fade_rect, "modulate:a", 1.0, FADE_DURATION)
	await tween.finished

	# Change scene
	get_tree().change_scene_to_file(SCREENS[screen_name])
	await get_tree().process_frame
	await get_tree().process_frame  # Wait two frames for scene to settle

	_resize_fade_rect()

	# Fade in
	tween = create_tween()
	tween.tween_property(_fade_rect, "modulate:a", 0.0, FADE_DURATION)
	await tween.finished

	_transitioning = false


func _resize_fade_rect() -> void:
	var size := get_viewport().get_visible_rect().size
	_fade_rect.size     = size
	_fade_rect.position = Vector2.ZERO
