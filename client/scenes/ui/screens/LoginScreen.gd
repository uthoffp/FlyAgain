## LoginScreen.gd
## Handles login form logic: validation, server communication, and navigation.
## The visual structure is defined in LoginScreen.tscn.
extends Control

# ---- Node references ----

@onready var _username: FlyLineEdit  = $CenterContainer/OuterVBox/LoginPanel/PanelVBox/UsernameField
@onready var _password: FlyLineEdit  = $CenterContainer/OuterVBox/LoginPanel/PanelVBox/PasswordField
@onready var _login_btn: FlyButton   = $CenterContainer/OuterVBox/LoginPanel/PanelVBox/LoginButton
@onready var _register_link: FlyButton = $CenterContainer/OuterVBox/LoginPanel/PanelVBox/RegisterLink
@onready var _status: StatusLabel    = $CenterContainer/OuterVBox/LoginPanel/PanelVBox/StatusLabel
@onready var _spinner: LoadingSpinner = $CenterContainer/OuterVBox/LoginPanel/PanelVBox/SpinnerContainer/Spinner


# ---- Lifecycle ----

func _ready() -> void:
	theme = ThemeFactory.create_main_theme()
	UIManager.set_initial_screen("login")
	_connect_signals()
	NetworkManager.connect_to_server()


func _exit_tree() -> void:
	_disconnect_signals()


# ---- Signals ----

func _connect_signals() -> void:
	_login_btn.pressed.connect(_on_login_pressed)
	_register_link.pressed.connect(_on_register_pressed)
	_username.text_submitted.connect(func(_t): _on_login_pressed())
	_password.text_submitted.connect(func(_t): _on_login_pressed())

	NetworkManager.connected_to_server.connect(_on_connected)
	NetworkManager.connection_failed.connect(_on_connection_failed)
	NetworkManager.disconnected_from_server.connect(_on_disconnected)
	NetworkManager.login_response.connect(_on_login_response)
	NetworkManager.error_response.connect(_on_error_response)


func _disconnect_signals() -> void:
	if NetworkManager.connected_to_server.is_connected(_on_connected):
		NetworkManager.connected_to_server.disconnect(_on_connected)
	if NetworkManager.connection_failed.is_connected(_on_connection_failed):
		NetworkManager.connection_failed.disconnect(_on_connection_failed)
	if NetworkManager.disconnected_from_server.is_connected(_on_disconnected):
		NetworkManager.disconnected_from_server.disconnect(_on_disconnected)
	if NetworkManager.login_response.is_connected(_on_login_response):
		NetworkManager.login_response.disconnect(_on_login_response)
	if NetworkManager.error_response.is_connected(_on_error_response):
		NetworkManager.error_response.disconnect(_on_error_response)


# ---- Button handlers ----

func _on_login_pressed() -> void:
	var username := _username.text.strip_edges()
	var password := _password.text

	var err := InputValidator.validate_login_username(username)
	if err.is_empty():
		err = InputValidator.validate_password(password)
	if not err.is_empty():
		_status.show_error(err)
		return

	_status.clear()
	_set_interactive(false)
	_spinner.start("Anmelden")

	if not NetworkManager.is_server_connected():
		_status.show_info("Verbinde mit Server...")
		await NetworkManager.connected_to_server
		if not NetworkManager.is_server_connected():
			return  # connection_failed signal will handle the error

	NetworkManager.send_login(username, password)


func _on_register_pressed() -> void:
	UIManager.push_screen("register")


# ---- Network response handlers ----

func _on_connected() -> void:
	_spinner.stop()
	_status.show_success("Verbunden.")


func _on_connection_failed(reason: String) -> void:
	_spinner.stop()
	_set_interactive(true)
	_status.show_error(reason)


func _on_disconnected() -> void:
	_spinner.stop()
	_set_interactive(true)
	_status.show_error("Verbindung getrennt.")


func _on_login_response(data: Dictionary) -> void:
	_spinner.stop()

	if not data.get("success", false):
		_set_interactive(true)
		_status.show_error(data.get("error_message", "Anmeldung fehlgeschlagen."))
		return

	# Populate GameState from login response
	GameState.jwt                 = data.get("jwt", "")
	GameState.hmac_secret         = data.get("hmac_secret", "")
	GameState.account_service_host = data.get("account_service_host", "")
	GameState.account_service_port = data.get("account_service_port", 0)
	GameState.characters          = data.get("characters", [])

	# Always reconnect to account-service (fallback to default host:7779 if not provided)
	var acct_host: String = GameState.account_service_host if not GameState.account_service_host.is_empty() else NetworkManager.DEFAULT_HOST
	var acct_port: int    = GameState.account_service_port if GameState.account_service_port > 0 else 7779
	NetworkManager.disconnect_from_server()
	NetworkManager.connect_to_server(acct_host, acct_port)
	await NetworkManager.connected_to_server

	UIManager.replace_screen("char_select")


func _on_error_response(data: Dictionary) -> void:
	_spinner.stop()
	_set_interactive(true)
	_status.show_error(data.get("message", "Serverfehler."))


# ---- Helpers ----

func _set_interactive(enabled: bool) -> void:
	_login_btn.disabled    = not enabled
	_register_link.disabled = not enabled
	_username.get_node("Input").editable = enabled
	_password.get_node("Input").editable = enabled
