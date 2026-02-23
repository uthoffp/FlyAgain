## LoadingSpinner.gd
## Animated "connecting" indicator using a text-based dot animation.
##
## Usage:
##   spinner.start("Verbinde...")
##   spinner.stop()
class_name LoadingSpinner
extends VBoxContainer

const _DOT_FRAMES  := [".", "..", "...", ".."]
const _DOT_INTERVAL := 0.4

var _dot_index:   int   = 0
var _elapsed:     float = 0.0
var _base_message: String = "Verbinde"

var _message_label: Label
var _dot_label:     Label


func _ready() -> void:
	alignment = BoxContainer.ALIGNMENT_CENTER
	visible   = false

	_message_label                   = $MessageLabel
	_dot_label                       = $DotLabel

	_message_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_dot_label.horizontal_alignment     = HORIZONTAL_ALIGNMENT_CENTER

	_message_label.add_theme_color_override("font_color", Colors.TEXT_INFO)
	_dot_label.add_theme_color_override("font_color",     Colors.GOLD)


func _process(delta: float) -> void:
	if not visible:
		return
	_elapsed += delta
	if _elapsed >= _DOT_INTERVAL:
		_elapsed    = 0.0
		_dot_index  = (_dot_index + 1) % _DOT_FRAMES.size()
		_dot_label.text = _DOT_FRAMES[_dot_index]


## Shows the spinner with an optional message.
func start(msg: String = "Verbinde") -> void:
	_base_message       = msg
	_message_label.text = msg
	_dot_label.text     = "."
	_dot_index          = 0
	_elapsed            = 0.0
	visible             = true
	set_process(true)


## Hides the spinner.
func stop() -> void:
	visible = false
	set_process(false)
