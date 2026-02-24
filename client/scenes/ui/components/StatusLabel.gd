## StatusLabel.gd
## Displays error, success, or info feedback messages.
##
## Usage:
##   status.show_error("Falsches Passwort.")
##   status.show_success("Konto erstellt!")
##   status.show_info("Verbinde...")
##   status.clear()
class_name StatusLabel
extends Label


func _ready() -> void:
	autowrap_mode       = TextServer.AUTOWRAP_WORD_SMART
	horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	visible              = false


## Shows a red error message.
func show_error(msg: String) -> void:
	text    = msg
	visible = true
	add_theme_color_override("font_color", Colors.TEXT_ERROR)


## Shows a green success message.
func show_success(msg: String) -> void:
	text    = msg
	visible = true
	add_theme_color_override("font_color", Colors.TEXT_SUCCESS)


## Shows a blue informational message.
func show_info(msg: String) -> void:
	text    = msg
	visible = true
	add_theme_color_override("font_color", Colors.TEXT_INFO)


## Hides the label and clears its text.
func clear() -> void:
	text    = ""
	visible = false
