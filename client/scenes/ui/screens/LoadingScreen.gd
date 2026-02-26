## LoadingScreen.gd
## Simple loading screen shown during zone transitions.
extends Control


@onready var _label: Label = $CenterContainer/VBoxContainer/LoadingLabel
@onready var _dots_label: Label = $CenterContainer/VBoxContainer/DotsLabel

var _dot_patterns: Array = [".", "..", "...", ".."]
var _dot_index: int = 0
var _timer: float = 0.0
const DOT_INTERVAL := 0.4


func _ready() -> void:
	theme = ThemeFactory.create_main_theme()
	_label.text = "Loading..."
	_dots_label.text = "."


func _process(delta: float) -> void:
	_timer += delta
	if _timer >= DOT_INTERVAL:
		_timer -= DOT_INTERVAL
		_dot_index = (_dot_index + 1) % _dot_patterns.size()
		_dots_label.text = _dot_patterns[_dot_index]


## Set the loading message.
func set_message(msg: String) -> void:
	if _label:
		_label.text = msg
