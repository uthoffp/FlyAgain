## RegisterScreen.gd
## Handles registration form logic: validation, server communication, and navigation.
## The visual structure is defined in RegisterScreen.tscn.
extends Control

# ---- Node references ----

@onready var _username: FlyLineEdit   = $CenterContainer/OuterVBox/RegisterPanel/PanelVBox/UsernameField
@onready var _email: FlyLineEdit      = $CenterContainer/OuterVBox/RegisterPanel/PanelVBox/EmailField
@onready var _password: FlyLineEdit   = $CenterContainer/OuterVBox/RegisterPanel/PanelVBox/PasswordField
@onready var _confirm: FlyLineEdit    = $CenterContainer/OuterVBox/RegisterPanel/PanelVBox/ConfirmField
@onready var _register_btn: FlyButton = $CenterContainer/OuterVBox/RegisterPanel/PanelVBox/RegisterButton
@onready var _back_btn: FlyButton     = $CenterContainer/OuterVBox/RegisterPanel/PanelVBox/BackButton
@onready var _status: StatusLabel     = $CenterContainer/OuterVBox/RegisterPanel/PanelVBox/StatusLabel
@onready var _spinner: LoadingSpinner = $CenterContainer/OuterVBox/RegisterPanel/PanelVBox/SpinnerContainer/Spinner


# ---- Lifecycle ----

func _ready() -> void:
	theme = ThemeFactory.create_main_theme()
	_connect_signals()

	if not NetworkManager.is_server_connected():
		_status.show_info("Verbinde mit Server...")
		NetworkManager.connect_to_server()


func _exit_tree() -> void:
	_disconnect_signals()


# ---- Signals ----

func _connect_signals() -> void:
	_register_btn.pressed.connect(_on_register_pressed)
	_back_btn.pressed.connect(_on_back_pressed)
	_confirm.text_submitted.connect(func(_t): _on_register_pressed())

	NetworkManager.connection_failed.connect(_on_connection_failed)
	NetworkManager.register_response.connect(_on_register_response)
	NetworkManager.error_response.connect(_on_error_response)


func _disconnect_signals() -> void:
	if NetworkManager.connection_failed.is_connected(_on_connection_failed):
		NetworkManager.connection_failed.disconnect(_on_connection_failed)
	if NetworkManager.register_response.is_connected(_on_register_response):
		NetworkManager.register_response.disconnect(_on_register_response)
	if NetworkManager.error_response.is_connected(_on_error_response):
		NetworkManager.error_response.disconnect(_on_error_response)


# ---- Button handlers ----

func _on_register_pressed() -> void:
	var username := _username.text.strip_edges()
	var email    := _email.text.strip_edges()
	var password := _password.text
	var confirm  := _confirm.text

	var err := InputValidator.validate_username(username)
	if err.is_empty():
		err = InputValidator.validate_email(email)
	if err.is_empty():
		err = InputValidator.validate_password(password)
	if err.is_empty():
		err = InputValidator.validate_password_match(password, confirm)
	if not err.is_empty():
		_status.show_error(err)
		return

	_status.clear()
	_set_interactive(false)
	_spinner.start("Registrieren")

	if not NetworkManager.is_server_connected():
		await NetworkManager.connected_to_server
		if not NetworkManager.is_server_connected():
			return

	NetworkManager.send_register(username, email, password)


func _on_back_pressed() -> void:
	UIManager.pop_screen()


# ---- Network response handlers ----

func _on_connection_failed(reason: String) -> void:
	_spinner.stop()
	_set_interactive(true)
	_status.show_error(reason)


func _on_register_response(data: Dictionary) -> void:
	_spinner.stop()

	if not data.get("success", false):
		_set_interactive(true)
		_status.show_error(data.get("error_message", "Registrierung fehlgeschlagen."))
		return

	_status.show_success("Konto erstellt! Bitte jetzt anmelden.")
	await get_tree().create_timer(1.5).timeout
	UIManager.pop_screen()


func _on_error_response(data: Dictionary) -> void:
	_spinner.stop()
	_set_interactive(true)
	_status.show_error(data.get("message", "Serverfehler."))


# ---- Helpers ----

func _set_interactive(enabled: bool) -> void:
	_register_btn.disabled = not enabled
	_back_btn.disabled     = not enabled
	for field: FlyLineEdit in [_username, _email, _password, _confirm]:
		field.get_node("Input").editable = enabled


