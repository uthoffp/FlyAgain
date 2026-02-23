## CharacterCreateScreen.gd
## Handles character creation: name input, class selection, server communication.
## The visual structure is defined in CharacterCreateScreen.tscn.
extends Control

# ---- Node references ----

@onready var _name_field: FlyLineEdit   = $CenterContainer/OuterVBox/CreatePanel/PanelVBox/NameField
@onready var _class_grid: GridContainer = $CenterContainer/OuterVBox/CreatePanel/PanelVBox/ClassGrid
@onready var _class_desc: Label         = $CenterContainer/OuterVBox/CreatePanel/PanelVBox/ClassDescription
@onready var _create_btn: FlyButton     = $CenterContainer/OuterVBox/CreatePanel/PanelVBox/CreateButton
@onready var _back_btn: FlyButton       = $CenterContainer/OuterVBox/CreatePanel/PanelVBox/BackButton
@onready var _status: StatusLabel       = $CenterContainer/OuterVBox/CreatePanel/PanelVBox/StatusLabel
@onready var _spinner: LoadingSpinner   = $CenterContainer/OuterVBox/CreatePanel/PanelVBox/SpinnerContainer/Spinner

const CLASS_DESCRIPTIONS: Dictionary = {
	"Krieger":   "Tank / Nahkampf — Hohe HP, starke Nahkampfangriffe. Ideal für das Frontline-Fighting.",
	"Magier":    "Ranged DPS — Elementarmagie mit hohem Schaden, jedoch niedrige HP.",
	"Assassine": "Melee DPS — Schnelle Angriffe, hohe Ausweichrate, kritische Treffer.",
	"Kleriker":  "Heiler / Support — Heilung und Buffs für die Gruppe. Unverzichtbar in Dungeons.",
}

var _selected_class: String = "Krieger"


# ---- Lifecycle ----

func _ready() -> void:
	theme = ThemeFactory.create_main_theme()
	_connect_signals()
	_select_class("Krieger")  # Default selection


func _exit_tree() -> void:
	_disconnect_signals()


# ---- Signals ----

func _connect_signals() -> void:
	_create_btn.pressed.connect(_on_create_pressed)
	_back_btn.pressed.connect(_on_back_pressed)
	_name_field.text_submitted.connect(func(_t): _on_create_pressed())

	# Class buttons — iterate GridContainer children
	for btn in _class_grid.get_children():
		if btn is FlyButton:
			var class_name_str: String = btn.label_text
			btn.pressed.connect(func(): _select_class(class_name_str))

	NetworkManager.error_response.connect(_on_error_response)


func _disconnect_signals() -> void:
	if NetworkManager.error_response.is_connected(_on_error_response):
		NetworkManager.error_response.disconnect(_on_error_response)


# ---- Class selection ----

func _select_class(class_name_str: String) -> void:
	_selected_class = class_name_str
	_class_desc.text = CLASS_DESCRIPTIONS.get(class_name_str, "")

	# Update button variants: active = PRIMARY, others = SECONDARY
	for btn in _class_grid.get_children():
		if btn is FlyButton:
			if btn.label_text == class_name_str:
				btn.variant = FlyButton.Variant.PRIMARY
			else:
				btn.variant = FlyButton.Variant.SECONDARY


# ---- Button handlers ----

func _on_create_pressed() -> void:
	var char_name := _name_field.text.strip_edges()

	if char_name.length() < 3 or char_name.length() > 16:
		_status.show_error("Name muss 3–16 Zeichen lang sein.")
		return
	if not _is_valid_char_name(char_name):
		_status.show_error("Name darf nur Buchstaben, Ziffern und Bindestriche enthalten.")
		return
	if _selected_class.is_empty():
		_status.show_error("Bitte eine Klasse auswählen.")
		return

	_status.clear()
	_set_interactive(false)
	_spinner.start("Erstelle Charakter")

	NetworkManager.send_character_create(char_name, _selected_class)

	var response: Dictionary = await _wait_for_char_create_response()
	_spinner.stop()

	if response.get("success", false):
		_status.show_success("Charakter erstellt!")
		await get_tree().create_timer(1.0).timeout
		UIManager.pop_screen()
	else:
		_set_interactive(true)
		_status.show_error(response.get("message", "Erstellung fehlgeschlagen."))


func _on_back_pressed() -> void:
	UIManager.pop_screen()


# ---- Network response handlers ----

func _on_error_response(data: Dictionary) -> void:
	_spinner.stop()
	_set_interactive(true)
	_status.show_error(data.get("message", "Serverfehler."))


# ---- Helpers ----

## Waits for character create response (enter_world_response or error_response).
## Returns a dict with "success" bool and optional "message" string.
##
## Server responses:
##   - ENTER_WORLD (0x0004) via CharacterCreateHandler: success=true/false, error_message
##   - ERROR_RESPONSE (0x0603) via PacketRouter: authentication failure (JWT missing/invalid)
func _wait_for_char_create_response() -> Dictionary:
	var result := {}
	var done   := false

	# ENTER_WORLD response: sent by CharacterCreateHandler for both success and validation errors
	var on_enter_world := func(data: Dictionary) -> void:
		if not done:
			done = true
			if data.get("success", false):
				result = {"success": true}
			else:
				result = {"success": false, "message": data.get("error_message", "Erstellung fehlgeschlagen.")}

	# ERROR_RESPONSE: sent by PacketRouter for authentication failures
	var on_error := func(data: Dictionary) -> void:
		if not done:
			done   = true
			result = {"success": false, "message": data.get("message", "Serverfehler.")}

	NetworkManager.enter_world_response.connect(on_enter_world, CONNECT_ONE_SHOT)
	NetworkManager.error_response.connect(on_error, CONNECT_ONE_SHOT)

	# Poll until we get a result (timeout after 10 seconds)
	var elapsed := 0.0
	while not done and elapsed < 10.0:
		await get_tree().process_frame
		elapsed += get_process_delta_time()

	if not done:
		if NetworkManager.enter_world_response.is_connected(on_enter_world):
			NetworkManager.enter_world_response.disconnect(on_enter_world)
		if NetworkManager.error_response.is_connected(on_error):
			NetworkManager.error_response.disconnect(on_error)
		# Timeout — assume failure (server took too long)
		return {"success": false, "message": "Zeitüberschreitung. Bitte erneut versuchen."}

	return result


func _set_interactive(enabled: bool) -> void:
	_create_btn.disabled = not enabled
	_back_btn.disabled   = not enabled
	_name_field.get_node("Input").editable = enabled
	for btn in _class_grid.get_children():
		if btn is Button:
			btn.disabled = not enabled


func _is_valid_char_name(name_str: String) -> bool:
	var rx := RegEx.new()
	rx.compile("^[a-zA-Z0-9\\-]+$")
	return rx.search(name_str) != null
