## GameState.gd  (Autoload: "GameState")
## Holds all client-side session and character data.
## Persists across scene changes. Reset on logout or new login.
extends Node

# ---- Auth session ----
var jwt:                  String = ""
var hmac_secret:          String = ""

# ---- Account service redirect (from LoginResponse) ----
var account_service_host: String = ""
var account_service_port: int    = 0

# ---- World service redirect (from EnterWorldResponse) ----
var world_service_host:   String = ""
var world_service_tcp_port: int  = 0
var world_service_udp_port: int  = 0

# ---- Character data ----
## Array of Dictionaries: { id, name, character_class, level }
var characters: Array = []
## Selected character ID (set after CharacterSelect)
var selected_character_id: int = 0


## Resets all session state. Call on logout or session expiry.
func reset() -> void:
	jwt                   = ""
	hmac_secret           = ""
	account_service_host  = ""
	account_service_port  = 0
	world_service_host    = ""
	world_service_tcp_port = 0
	world_service_udp_port = 0
	characters            = []
	selected_character_id = 0


## Returns true if a valid session is active.
func is_authenticated() -> bool:
	return not jwt.is_empty()
