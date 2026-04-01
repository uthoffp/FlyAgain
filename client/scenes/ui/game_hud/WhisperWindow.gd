## WhisperWindow.gd
## Small chat window for a single whisper conversation.
extends PanelContainer

signal whisper_sent(target_name: String, text: String)
signal window_closed(target_name: String)

const COLORS := {
	"incoming": Color(1.0, 0.4, 1.0),
	"outgoing": Color(0.8, 0.6, 1.0),
	"name": Color(0.5, 0.8, 1.0),
}

var target_name: String = ""
var last_activity: float = 0.0

var _message_display: RichTextLabel
var _input_field: LineEdit
var _vbox: VBoxContainer


func setup(p_target_name: String) -> void:
	target_name = p_target_name
	last_activity = Time.get_ticks_msec() / 1000.0


func _ready() -> void:
	_build_ui()


func _build_ui() -> void:
	_vbox = VBoxContainer.new()
	_vbox.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(_vbox)

	_message_display = RichTextLabel.new()
	_message_display.bbcode_enabled = true
	_message_display.scroll_following = true
	_message_display.selection_enabled = true
	_message_display.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_message_display.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_message_display.fit_content = true
	_vbox.add_child(_message_display)

	_input_field = LineEdit.new()
	_input_field.placeholder_text = tr("CHAT_INPUT_PLACEHOLDER")
	_input_field.max_length = 200
	_input_field.custom_minimum_size.y = 28
	_input_field.text_submitted.connect(_on_text_submitted)
	_input_field.focus_entered.connect(func(): GameState.chat_input_active = true)
	_input_field.focus_exited.connect(func(): GameState.chat_input_active = false)
	_vbox.add_child(_input_field)


func add_incoming(sender_name: String, text: String) -> void:
	last_activity = Time.get_ticks_msec() / 1000.0
	var name_hex := COLORS["name"].to_html(false)
	var color_hex := COLORS["incoming"].to_html(false)
	_message_display.append_text(
		"[color=#%s][color=#%s]%s[/color]: %s[/color]\n" % [
			color_hex, name_hex, sender_name, text])


func add_outgoing(text: String) -> void:
	last_activity = Time.get_ticks_msec() / 1000.0
	var name_hex := COLORS["name"].to_html(false)
	var color_hex := COLORS["outgoing"].to_html(false)
	var my_name: String = _get_my_name()
	_message_display.append_text(
		"[color=#%s][color=#%s]%s[/color]: %s[/color]\n" % [
			color_hex, name_hex, my_name, text])


func _get_my_name() -> String:
	for c in GameState.characters:
		if c.get("id", "") == GameState.selected_character_id:
			return c.get("name", "You")
	return "You"


func _on_text_submitted(text: String) -> void:
	_input_field.clear()
	var trimmed := text.strip_edges()
	if trimmed.is_empty():
		return
	whisper_sent.emit(target_name, trimmed)
