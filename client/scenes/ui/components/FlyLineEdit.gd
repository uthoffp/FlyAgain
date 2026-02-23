## FlyLineEdit.gd
## Labeled text input component.
## Renders a small field label above a styled LineEdit.
##
## Usage:
##   var field = preload("res://scenes/ui/components/FlyLineEdit.tscn").instantiate()
##   field.label_text       = "Benutzername"
##   field.placeholder_text = "3–16 Zeichen"
##   field.secret           = false
##   var value := field.text
@tool
class_name FlyLineEdit
extends VBoxContainer

## Text shown above the input field.
@export var label_text: String = "":
	set(v):
		label_text = v
		_update_label()

## Placeholder shown inside the empty input.
@export var placeholder_text: String = "":
	set(v):
		placeholder_text = v
		_update_input()

## When true the input is rendered as a password field.
@export var secret: bool = false:
	set(v):
		secret = v
		_update_input()

## Max allowed input length (0 = unlimited).
@export var max_length: int = 0:
	set(v):
		max_length = v
		_update_input()

## Signal emitted when the user presses Enter inside the field.
signal text_submitted(new_text: String)

# Node references (assigned in _ready after scene is ready)
var _field_label: Label
var _input:       LineEdit


func _ready() -> void:
	_field_label = $FieldLabel
	_input       = $Input

	add_theme_constant_override("separation", 4)

	_update_label()
	_update_input()
	_input.text_submitted.connect(func(t): text_submitted.emit(t))


## Current text value.
var text: String:
	get:
		return _input.text if _input else ""
	set(v):
		if _input:
			_input.text = v


## Clears the input field.
func clear() -> void:
	if _input:
		_input.clear()


## Gives focus to the underlying LineEdit.
func focus_input() -> void:
	if _input:
		_input.grab_focus()


# ---- Private ----

func _update_label() -> void:
	if not is_node_ready() or not _field_label:
		return
	_field_label.text    = label_text
	_field_label.visible = not label_text.is_empty()
	_field_label.add_theme_font_size_override("font_size", 12)
	_field_label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)


func _update_input() -> void:
	if not is_node_ready() or not _input:
		return
	_input.placeholder_text = placeholder_text
	_input.secret           = secret
	_input.max_length       = max_length
	_input.custom_minimum_size = Vector2(0, 40)
