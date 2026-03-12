## CharacterSelectScreen.gd
## Shows the list of characters belonging to the logged-in account.
## Characters are loaded from GameState.characters (populated during login).
## The visual structure is defined in CharacterSelectScreen.tscn.
extends Control

# ---- Node references ----

@onready var _char_slots: HBoxContainer  = $CenterContainer/OuterVBox/SelectPanel/PanelVBox/CharSlots
@onready var _create_btn: FlyButton      = $CenterContainer/OuterVBox/SelectPanel/PanelVBox/CreateButton
@onready var _logout_btn: FlyButton      = $CenterContainer/OuterVBox/SelectPanel/PanelVBox/LogoutButton
@onready var _status: StatusLabel        = $CenterContainer/OuterVBox/SelectPanel/PanelVBox/StatusLabel

const MAX_CHARACTERS := 3

# Class descriptions shown in each slot
const CLASS_DESCRIPTIONS: Dictionary = {
	"Warrior":  "Tank / Melee\nHigh HP and strong attacks",
	"Mage":     "Ranged DPS\nElemental magic with high damage",
	"Assassin": "Melee DPS\nFast, critical hits",
	"Cleric":   "Healer / Support\nHealing and buffs for the group",
}


# ---- Lifecycle ----

func _ready() -> void:
	theme = ThemeFactory.create_main_theme()
	_connect_signals()
	_build_character_slots()


func _exit_tree() -> void:
	_disconnect_signals()


# ---- Signals ----

func _connect_signals() -> void:
	_create_btn.pressed.connect(_on_create_pressed)
	_logout_btn.pressed.connect(_on_logout_pressed)

	NetworkManager.error_response.connect(_on_error_response)


func _disconnect_signals() -> void:
	if NetworkManager.error_response.is_connected(_on_error_response):
		NetworkManager.error_response.disconnect(_on_error_response)


# ---- Character slot building ----

func _build_character_slots() -> void:
	# Clear any existing slots
	for child in _char_slots.get_children():
		child.queue_free()

	var characters := GameState.characters
	var slot_count: int = maxi(characters.size(), 1)  # Always show at least one slot
	slot_count          = mini(slot_count, MAX_CHARACTERS)

	# Fill existing character slots
	for i in range(characters.size()):
		var char_data: Dictionary = characters[i]
		_char_slots.add_child(_make_character_slot(char_data))

	# Fill empty placeholder slots up to MAX_CHARACTERS
	for i in range(characters.size(), MAX_CHARACTERS):
		_char_slots.add_child(_make_empty_slot())

	# Hide create button if already at max
	_create_btn.visible = characters.size() < MAX_CHARACTERS


func _make_character_slot(char_data: Dictionary) -> PanelContainer:
	var panel       := PanelContainer.new()
	panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL

	var vbox := VBoxContainer.new()
	vbox.add_theme_constant_override("separation", 8)
	panel.add_child(vbox)

	# Character name
	var name_label := Label.new()
	name_label.text                = char_data.get("name", "?")
	name_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	name_label.add_theme_font_size_override("font_size", 18)
	name_label.add_theme_color_override("font_color", Colors.TEXT_TITLE)
	vbox.add_child(name_label)

	# Class and level
	var char_class: String = char_data.get("character_class", "")
	var level: int         = char_data.get("level", 1)
	var info_label := Label.new()
	info_label.text                = "%s  •  Lv. %d" % [char_class, level]
	info_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	info_label.add_theme_font_size_override("font_size", 13)
	info_label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)
	vbox.add_child(info_label)

	# Class description
	var desc_label := Label.new()
	desc_label.text                = CLASS_DESCRIPTIONS.get(char_class, "")
	desc_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	desc_label.autowrap_mode        = TextServer.AUTOWRAP_WORD_SMART
	desc_label.add_theme_font_size_override("font_size", 12)
	desc_label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)
	vbox.add_child(desc_label)

	var spacer := Control.new()
	spacer.custom_minimum_size = Vector2(0, 8)
	vbox.add_child(spacer)

	# Select button
	var select_btn := preload("res://scenes/ui/components/FlyButton.tscn").instantiate() as FlyButton
	select_btn.label_text = "Spielen"
	var char_id: String = char_data.get("id", "")
	select_btn.pressed.connect(func(): _on_character_selected(char_id))
	vbox.add_child(select_btn)

	return panel


func _make_empty_slot() -> PanelContainer:
	var panel       := PanelContainer.new()
	panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL

	var vbox := VBoxContainer.new()
	vbox.alignment = BoxContainer.ALIGNMENT_CENTER
	panel.add_child(vbox)

	var empty_label := Label.new()
	empty_label.text                = "— Freier Slot —"
	empty_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	empty_label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)
	vbox.add_child(empty_label)

	return panel


# ---- Button / event handlers ----

func _on_character_selected(character_id: String) -> void:
	GameState.selected_character_id = character_id
	_status.show_info("Lade Charakter...")
	_set_interactive(false)

	NetworkManager.send_character_select(character_id)

	# Wait for EnterWorldResponse from account-service
	var response: Dictionary = await NetworkManager.enter_world_response
	if not response.get("success", false):
		_set_interactive(true)
		_status.show_error(response.get("error_message", "Serverfehler."))
		return

	# Store world service connection info
	GameState.world_service_host = response.get("world_service_host", "")
	GameState.world_service_tcp_port = response.get("world_service_tcp_port", 0)
	GameState.world_service_udp_port = response.get("world_service_udp_port", 0)

	# Store spawn position from server (persisted last logout location)
	var pos: Dictionary = response.get("position", {})
	GameState.player_position = Vector3(
		pos.get("x", 0.0), pos.get("y", 0.0), pos.get("z", 0.0))

	# Store character stats
	var stats: Dictionary = response.get("stats", {})
	GameState.player_level   = stats.get("level", 1)
	GameState.player_hp      = stats.get("hp", 100)
	GameState.player_max_hp  = stats.get("max_hp", 100)
	GameState.player_mp      = stats.get("mp", 50)
	GameState.player_max_mp  = stats.get("max_mp", 50)
	GameState.player_str     = stats.get("str", 0)
	GameState.player_sta     = stats.get("sta", 0)
	GameState.player_dex     = stats.get("dex", 0)
	GameState.player_int     = stats.get("int_", 0)
	GameState.player_xp      = stats.get("xp", 0)
	GameState.player_xp_to_next_level = stats.get("xp_to_next_level", 100)

	_status.show_info("Verbinde mit Welt-Server...")

	# Determine connection parameters
	var host: String = GameState.world_service_host
	if host.is_empty():
		host = NetworkManager.DEFAULT_HOST
	var tcp_port: int = GameState.world_service_tcp_port
	if tcp_port == 0:
		tcp_port = 7780
	var udp_port: int = GameState.world_service_udp_port
	if udp_port == 0:
		udp_port = 7781

	# Connect to world service (TCP + UDP)
	NetworkManager.connect_to_world(host, tcp_port, udp_port, GameState.session_token)
	await NetworkManager.world_connected

	# Transition to the 3D game world
	UIManager.enter_game_world()


func _on_create_pressed() -> void:
	UIManager.push_screen("char_create")


func _on_logout_pressed() -> void:
	GameState.reset()
	NetworkManager.disconnect_from_server()
	UIManager.replace_screen("login")


func _on_error_response(data: Dictionary) -> void:
	_set_interactive(true)
	_status.show_error(data.get("message", "Serverfehler."))


# ---- Helpers ----

func _set_interactive(enabled: bool) -> void:
	_create_btn.disabled = not enabled
	_logout_btn.disabled = not enabled
	for slot in _char_slots.get_children():
		for btn in _get_buttons_recursive(slot):
			btn.disabled = not enabled


func _get_buttons_recursive(node: Node) -> Array[Button]:
	var buttons: Array[Button] = []
	for child in node.get_children():
		if child is Button:
			buttons.append(child)
		buttons.append_array(_get_buttons_recursive(child))
	return buttons
