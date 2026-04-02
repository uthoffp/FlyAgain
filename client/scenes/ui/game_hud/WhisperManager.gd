## WhisperManager.gd
## Manages whisper windows (max 5 simultaneous).
extends Node

const MAX_WINDOWS := 5
const WhisperWindowScript := preload("res://scenes/ui/game_hud/WhisperWindow.gd")
const GameWindowScript := preload("res://scenes/ui/window_system/GameWindow.gd")

var _windows: Dictionary = {}
var _hud_root: Control = null
var _window_offset: int = 0


func initialize(hud_root: Control) -> void:
	_hud_root = hud_root
	NetworkManager.chat_broadcast_received.connect(_on_chat_broadcast)


func _on_chat_broadcast(data: Dictionary) -> void:
	var channel_type: int = data.get("channel_type", 0)
	if channel_type == 2:
		var sender: String = data.get("sender_name", "")
		if sender.is_empty():
			return
		var win := _open_or_get(sender)
		win.add_incoming(sender, data.get("text", ""))
	elif channel_type == 3:
		var target: String = data.get("target_name", "")
		if target.is_empty():
			return
		var win := _open_or_get(target)
		win.add_outgoing(data.get("text", ""))


func _open_or_get(player_name: String) -> PanelContainer:
	var key := player_name.to_lower()
	if _windows.has(key):
		var entry: Dictionary = _windows[key]
		entry["game_window"].visible = true
		return entry["whisper"]

	if _count_visible() >= MAX_WINDOWS:
		_hide_oldest()

	var game_win := PanelContainer.new()
	game_win.set_script(GameWindowScript)
	_window_offset = (_window_offset + 1) % 5
	var x_pos := 480 + _window_offset * 30
	var y_pos := 400 + _window_offset * 20
	game_win.call("setup", "whisper_" + key, tr("WHISPER_TITLE").replace("{name}", player_name), {
		"draggable": true, "resizable": true,
		"minimizable": true, "closable": true,
		"default_position": Vector2(x_pos, y_pos),
		"default_size": Vector2(320, 250),
		"min_size": Vector2(250, 180),
		"max_size": Vector2(500, 400),
	})
	_hud_root.add_child(game_win)

	var whisper_content := PanelContainer.new()
	whisper_content.set_script(WhisperWindowScript)
	game_win.call("set_content", whisper_content)
	whisper_content.call("setup", player_name)
	whisper_content.whisper_sent.connect(_on_whisper_sent)
	game_win.window_closed.connect(_on_window_closed)

	_windows[key] = {"game_window": game_win, "whisper": whisper_content}
	return whisper_content


func _count_visible() -> int:
	var count := 0
	for key in _windows:
		if _windows[key]["game_window"].visible:
			count += 1
	return count


func _hide_oldest() -> void:
	var oldest_key: String = ""
	var oldest_time: float = INF
	for key in _windows:
		var entry: Dictionary = _windows[key]
		if entry["game_window"].visible and entry["whisper"].last_activity < oldest_time:
			oldest_time = entry["whisper"].last_activity
			oldest_key = key
	if oldest_key != "":
		_windows[oldest_key]["game_window"].visible = false


func _on_whisper_sent(target_name: String, text: String) -> void:
	NetworkManager.send_chat_message(2, text, target_name)


func _on_window_closed(window_id: String) -> void:
	var key := window_id.replace("whisper_", "")
	if _windows.has(key):
		_windows[key]["game_window"].visible = false
