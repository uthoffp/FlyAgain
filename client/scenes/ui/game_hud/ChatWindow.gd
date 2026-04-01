## ChatWindow.gd
## Main chat window showing Say and Shout messages with tab filtering and input.
extends PanelContainer

enum Channel { SAY = 0, SHOUT = 1 }
enum Filter { ALL, SAY, SHOUT }

const MAX_MESSAGES := 200
const COLORS := {
	"say": Color(1.0, 1.0, 1.0),
	"shout": Color(1.0, 1.0, 0.3),
	"system": Color(1.0, 0.3, 0.3),
	"name": Color(0.5, 0.8, 1.0),
}

var _current_filter: Filter = Filter.ALL
var _messages: Array[Dictionary] = []

var _tab_all: Button
var _tab_say: Button
var _tab_shout: Button
var _message_display: RichTextLabel
var _scroll_container: ScrollContainer
var _input_field: LineEdit
var _vbox: VBoxContainer


func _ready() -> void:
	_build_ui()
	NetworkManager.chat_broadcast_received.connect(_on_chat_broadcast)
	NetworkManager.zone_data_received.connect(_on_zone_changed)


func _build_ui() -> void:
	_vbox = VBoxContainer.new()
	_vbox.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(_vbox)

	var tabs := HBoxContainer.new()
	tabs.custom_minimum_size.y = 28
	_vbox.add_child(tabs)

	_tab_all = _make_tab(tr("CHAT_ALL"), Filter.ALL)
	_tab_say = _make_tab(tr("CHAT_SAY"), Filter.SAY)
	_tab_shout = _make_tab(tr("CHAT_SHOUT"), Filter.SHOUT)
	tabs.add_child(_tab_all)
	tabs.add_child(_tab_say)
	tabs.add_child(_tab_shout)
	_update_tab_highlight()

	_scroll_container = ScrollContainer.new()
	_scroll_container.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_scroll_container.follow_focus = false
	_vbox.add_child(_scroll_container)

	_message_display = RichTextLabel.new()
	_message_display.bbcode_enabled = true
	_message_display.scroll_following = true
	_message_display.selection_enabled = true
	_message_display.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_message_display.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_message_display.fit_content = true
	_scroll_container.add_child(_message_display)

	_input_field = LineEdit.new()
	_input_field.placeholder_text = tr("CHAT_PLACEHOLDER")
	_input_field.max_length = 200
	_input_field.custom_minimum_size.y = 32
	_input_field.text_submitted.connect(_on_text_submitted)
	_input_field.focus_entered.connect(_on_input_focused)
	_input_field.focus_exited.connect(_on_input_unfocused)
	_vbox.add_child(_input_field)


func _make_tab(label: String, filter: Filter) -> Button:
	var btn := Button.new()
	btn.text = label
	btn.pressed.connect(func(): _set_filter(filter))
	return btn


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_ENTER or event.keycode == KEY_KP_ENTER:
			if not _input_field.has_focus():
				_input_field.grab_focus()
				get_viewport().set_input_as_handled()
		elif event.keycode == KEY_ESCAPE:
			if _input_field.has_focus():
				_input_field.release_focus()
				get_viewport().set_input_as_handled()


func _on_input_focused() -> void:
	GameState.chat_input_active = true
	_input_field.placeholder_text = tr("CHAT_INPUT_PLACEHOLDER")


func _on_input_unfocused() -> void:
	GameState.chat_input_active = false
	_input_field.placeholder_text = tr("CHAT_PLACEHOLDER")


func _on_text_submitted(text: String) -> void:
	_input_field.clear()
	var trimmed := text.strip_edges()
	if trimmed.is_empty():
		return
	_parse_and_send(trimmed)


func _parse_and_send(text: String) -> void:
	if text.begins_with("/shout "):
		var msg := text.substr(7).strip_edges()
		if not msg.is_empty():
			NetworkManager.send_chat_message(Channel.SHOUT, msg)
	elif text.begins_with("/say "):
		var parts := text.substr(5).strip_edges()
		var space_idx := parts.find(" ")
		if space_idx > 0:
			var target := parts.substr(0, space_idx)
			var msg := parts.substr(space_idx + 1).strip_edges()
			if not target.is_empty() and not msg.is_empty():
				NetworkManager.send_chat_message(2, msg, target)
	else:
		NetworkManager.send_chat_message(Channel.SAY, text)


func _on_chat_broadcast(data: Dictionary) -> void:
	var channel_type: int = data.get("channel_type", 0)
	if channel_type >= 2:
		return
	_add_message(data)


func _add_message(data: Dictionary) -> void:
	_messages.append(data)
	if _messages.size() > MAX_MESSAGES:
		_messages.pop_front()
	_refresh_display()


func _on_zone_changed(_data: Dictionary) -> void:
	_messages.clear()
	_message_display.clear()


func _set_filter(filter: Filter) -> void:
	_current_filter = filter
	_update_tab_highlight()
	_refresh_display()


func _update_tab_highlight() -> void:
	_tab_all.button_pressed = _current_filter == Filter.ALL
	_tab_say.button_pressed = _current_filter == Filter.SAY
	_tab_shout.button_pressed = _current_filter == Filter.SHOUT


func _refresh_display() -> void:
	_message_display.clear()
	for msg in _messages:
		var ct: int = msg.get("channel_type", 0)
		if _current_filter == Filter.SAY and ct != 0:
			continue
		if _current_filter == Filter.SHOUT and ct != 1:
			continue
		_append_formatted(msg)


func _append_formatted(data: Dictionary) -> void:
	var ct: int = data.get("channel_type", 0)
	var sender: String = data.get("sender_name", "")
	var text: String = data.get("text", "")

	var channel_label: String
	var channel_color: Color
	match ct:
		0:
			channel_label = "[Say]"
			channel_color = COLORS["say"]
		1:
			channel_label = "[Shout]"
			channel_color = COLORS["shout"]
		_:
			channel_label = "[System]"
			channel_color = COLORS["system"]

	var name_hex := COLORS["name"].to_html(false)
	var ch_hex := channel_color.to_html(false)
	_message_display.append_text(
		"[color=#%s]%s[/color] [color=#%s]%s[/color]: %s\n" % [
			ch_hex, channel_label, name_hex, sender, text
		])
