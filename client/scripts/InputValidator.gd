## InputValidator.gd
## Shared input validation rules for login, registration, and character creation.
## All methods are static — no instance needed.
class_name InputValidator
extends RefCounted

## Minimum username length.
const USERNAME_MIN_LENGTH := 3
## Maximum username length.
const USERNAME_MAX_LENGTH := 16
## Minimum password length.
const PASSWORD_MIN_LENGTH := 8
## Minimum character name length.
const CHAR_NAME_MIN_LENGTH := 3
## Maximum character name length.
const CHAR_NAME_MAX_LENGTH := 16

# Precompiled-style patterns (compiled on first use)
const _USERNAME_PATTERN := "^[a-zA-Z0-9\\-]+$"
const _CHAR_NAME_PATTERN := "^[a-zA-Z][a-zA-Z0-9\\-]{2,15}$"
const _EMAIL_PATTERN := "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"

# Reserved names that cannot be used as character names.
const _NAME_BLACKLIST := [
	"admin", "administrator", "moderator", "gamemaster", "support",
	"system", "server", "flyagain", "gm", "mod", "dev", "developer",
	"npc", "monster", "boss", "null", "undefined", "test",
]


## Validates a username. Returns an empty string on success, or an error message.
static func validate_username(username: String) -> String:
	if username.length() < USERNAME_MIN_LENGTH or username.length() > USERNAME_MAX_LENGTH:
		return "Benutzername muss %d–%d Zeichen lang sein." % [USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH]
	if not _matches_pattern(username, _USERNAME_PATTERN):
		return "Benutzername darf nur Buchstaben, Ziffern und Bindestriche enthalten."
	return ""


## Validates a login username (only minimum length required).
static func validate_login_username(username: String) -> String:
	if username.length() < USERNAME_MIN_LENGTH:
		return "Benutzername muss mindestens %d Zeichen lang sein." % USERNAME_MIN_LENGTH
	return ""


## Validates an email address. Returns an empty string on success, or an error message.
static func validate_email(email: String) -> String:
	if not _matches_pattern(email, _EMAIL_PATTERN):
		return "Bitte eine gültige E-Mail-Adresse eingeben."
	return ""


## Validates a password. Returns an empty string on success, or an error message.
static func validate_password(password: String) -> String:
	if password.length() < PASSWORD_MIN_LENGTH:
		return "Passwort muss mindestens %d Zeichen lang sein." % PASSWORD_MIN_LENGTH
	return ""


## Validates that password and confirmation match.
static func validate_password_match(password: String, confirm: String) -> String:
	if password != confirm:
		return "Die Passwörter stimmen nicht überein."
	return ""


## Validates a character name. Returns an empty string on success, or an error message.
static func validate_character_name(char_name: String) -> String:
	if char_name.length() < CHAR_NAME_MIN_LENGTH or char_name.length() > CHAR_NAME_MAX_LENGTH:
		return TranslationServer.translate("CHAR_NAME_LENGTH_ERROR") % [CHAR_NAME_MIN_LENGTH, CHAR_NAME_MAX_LENGTH]
	if not _matches_pattern(char_name, _CHAR_NAME_PATTERN):
		return TranslationServer.translate("CHAR_NAME_INVALID_ERROR")
	if char_name.to_lower() in _NAME_BLACKLIST:
		return TranslationServer.translate("CHAR_NAME_BLACKLIST_ERROR")
	return ""


## Valid class names and which ones are currently enabled for selection.
const _VALID_CLASSES := ["Warrior", "Mage", "Assassin", "Cleric"]
const _ENABLED_CLASSES := ["Warrior", "Mage", "Assassin", "Cleric"]

## Returns an empty string if the class selection is valid, or an error message.
static func validate_class_selection(selected_class: String) -> String:
	if selected_class.is_empty():
		return "Bitte eine Klasse auswählen."
	if selected_class not in _VALID_CLASSES:
		return "Ungültige Klasse."
	if selected_class not in _ENABLED_CLASSES:
		return "Diese Klasse ist noch nicht verfügbar."
	return ""


# ---- Private helpers ----

static func _matches_pattern(input: String, pattern: String) -> bool:
	var rx := RegEx.new()
	rx.compile(pattern)
	return rx.search(input) != null
