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
	"game_world":  "res://scenes/game/GameWorld.tscn",
	"loading":     "res://scenes/ui/screens/LoadingScreen.tscn",
}

const FADE_DURATION := 0.2
const BG_TEXTURE_PATH := "res://assets/ui/title_screen_background.png"

# ---- Internal state ----

var _stack:         Array[String] = []
var _bg_layer:      CanvasLayer
var _bg_rect:       TextureRect
var _transitioning: bool = false


# ---- Lifecycle ----

func _ready() -> void:
	_setup_background()


func _setup_background() -> void:
	_bg_layer       = CanvasLayer.new()
	_bg_layer.layer = -1
	_bg_layer.name  = "BackgroundLayer"
	add_child(_bg_layer)

	_bg_rect              = TextureRect.new()
	_bg_rect.name         = "Background"
	_bg_rect.texture      = load(BG_TEXTURE_PATH)
	_bg_rect.expand_mode  = TextureRect.EXPAND_IGNORE_SIZE
	_bg_rect.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	_bg_rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_bg_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	_bg_layer.add_child(_bg_rect)
	get_tree().root.size_changed.connect(_resize_bg_rect)
	_resize_bg_rect()


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


## Transitions to the 3D game world scene.
## Hides the UI background and replaces the entire navigation stack.
func enter_game_world() -> void:
	if _transitioning:
		return
	_transitioning = true
	_bg_layer.visible = false
	_stack.clear()
	_stack.push_back("game_world")
	get_tree().change_scene_to_file(SCREENS["game_world"])
	await get_tree().process_frame
	await get_tree().process_frame
	_transitioning = false


## Returns from the game world back to a UI screen.
## Restores the UI background and replaces the navigation stack.
func leave_game_world(target_screen: String = "login") -> void:
	if _transitioning:
		return
	_bg_layer.visible = true
	replace_screen(target_screen)


# ---- Internal ----

func _transition_to(screen_name: String) -> void:
	if not SCREENS.has(screen_name):
		push_error("UIManager: unknown screen '%s'" % screen_name)
		return

	_transitioning = true

	# Fade out current scene content (background stays visible)
	var current := get_tree().current_scene
	if current:
		var tween := create_tween()
		tween.tween_property(current, "modulate:a", 0.0, FADE_DURATION)
		await tween.finished

	# Change scene
	get_tree().change_scene_to_file(SCREENS[screen_name])
	await get_tree().process_frame
	await get_tree().process_frame  # Wait two frames for scene to settle

	# Fade in new scene content
	var new_scene := get_tree().current_scene
	if new_scene:
		new_scene.modulate.a = 0.0
		var tween := create_tween()
		tween.tween_property(new_scene, "modulate:a", 1.0, FADE_DURATION)
		await tween.finished

	_transitioning = false


func _resize_bg_rect() -> void:
	var size := get_viewport().get_visible_rect().size
	_bg_rect.size     = size
	_bg_rect.position = Vector2.ZERO
