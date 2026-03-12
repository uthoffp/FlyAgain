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
var world_service_host:     String = ""
var world_service_tcp_port: int    = 0
var world_service_udp_port: int    = 0

# ---- Session identifiers ----
var session_id:    String = ""
var session_token: int    = 0   # 8-byte session token for UDP HMAC

# ---- Character data ----
## Array of Dictionaries: { id, name, character_class, level }
var characters: Array = []
## Selected character ID (set after CharacterSelect)
var selected_character_id: String = ""

# ---- Player in-world data ----
var my_entity_id:       int     = 0
var current_zone_id:    int     = 0
var current_channel_id: int     = 0
var current_zone_name:  String  = ""
var player_position:    Vector3 = Vector3.ZERO
var player_rotation:    float   = 0.0

# ---- Player stats (from server) ----
var player_level:   int = 1
var player_hp:      int = 100
var player_max_hp:  int = 100
var player_mp:      int = 50
var player_max_mp:  int = 50
var player_str:     int = 0
var player_sta:     int = 0
var player_dex:     int = 0
var player_int:     int = 0
var player_xp:      int = 0
var player_gold:    int = 0
var player_xp_to_next_level: int = 100

# ---- Target state ----
var selected_target_id:      int    = 0
var selected_target_name:    String = ""
var selected_target_level:   int    = 0
var selected_target_hp:      int    = 0
var selected_target_max_hp:  int    = 0
var auto_attack_active:      bool   = false


## Resets all session state. Call on logout or session expiry.
func reset() -> void:
	jwt                    = ""
	hmac_secret            = ""
	account_service_host   = ""
	account_service_port   = 0
	world_service_host     = ""
	world_service_tcp_port = 0
	world_service_udp_port = 0
	session_id             = ""
	session_token          = 0
	characters             = []
	selected_character_id  = ""
	my_entity_id           = 0
	current_zone_id        = 0
	current_channel_id     = 0
	current_zone_name      = ""
	player_position        = Vector3.ZERO
	player_rotation        = 0.0
	player_level           = 1
	player_hp              = 100
	player_max_hp          = 100
	player_mp              = 50
	player_max_mp          = 50
	player_str             = 0
	player_sta             = 0
	player_dex             = 0
	player_int             = 0
	player_xp              = 0
	player_gold            = 0
	player_xp_to_next_level = 100
	selected_target_id     = 0
	selected_target_name   = ""
	selected_target_level  = 0
	selected_target_hp     = 0
	selected_target_max_hp = 0
	auto_attack_active     = false


## Returns true if a valid session is active.
func is_authenticated() -> bool:
	return not jwt.is_empty()
