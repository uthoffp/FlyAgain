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

## True when the chat input field is focused — blocks gameplay input (WASD, skills, etc.)
var chat_input_active: bool = false

# ---- Target state ----
var selected_target_id:      int    = 0
var selected_target_name:    String = ""
var selected_target_level:   int    = 0
var selected_target_hp:      int    = 0
var selected_target_max_hp:  int    = 0
var auto_attack_active:      bool   = false

# ---- Combat UI state ----
var is_dead:           bool       = false
var skill_cooldowns:   Dictionary = {}  # { skill_id: float (end_time from Time.get_ticks_msec()) }

# ---- Inventory & Equipment ----
## Inventory: 100 slots (index = slot number).
## Each entry: null (empty) or { "item_id": int, "amount": int, "enhancement": int }
var inventory_slots: Array = []
## Equipment: keyed by slot_type (0-6).
## Each value: null or { "item_id": int, "enhancement": int }
var equipment_slots: Dictionary = {}

signal inventory_changed
signal equipment_changed


func _ready() -> void:
	_init_inventory()


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
	is_dead                = false
	skill_cooldowns        = {}
	_init_inventory()


func _init_inventory() -> void:
	inventory_slots = []
	inventory_slots.resize(100)
	for i in range(100):
		inventory_slots[i] = null
	equipment_slots = {}
	for slot_type in range(7):
		equipment_slots[slot_type] = null


## Returns true if a valid session is active.
func is_authenticated() -> bool:
	return not jwt.is_empty()


## Merges a server InventoryUpdate delta into local state.
## item_id == 0 means the slot was cleared.
func merge_inventory_update(slots: Array, equipment: Array) -> void:
	var inv_changed := false
	var equip_changed := false

	for slot_data in slots:
		var slot_idx: int = slot_data.get("slot", -1)
		if slot_idx < 0 or slot_idx >= 100:
			continue
		var item_id: int = slot_data.get("item_id", 0)
		if item_id == 0:
			inventory_slots[slot_idx] = null
		else:
			inventory_slots[slot_idx] = {
				"item_id": item_id,
				"amount": slot_data.get("amount", 1),
				"enhancement": slot_data.get("enhancement", 0),
			}
		inv_changed = true

	for equip_data in equipment:
		var slot_type: int = equip_data.get("slot_type", -1)
		if slot_type < 0 or slot_type > 6:
			continue
		var item_id: int = equip_data.get("item_id", 0)
		if item_id == 0:
			equipment_slots[slot_type] = null
		else:
			equipment_slots[slot_type] = {
				"item_id": item_id,
				"enhancement": equip_data.get("enhancement", 0),
			}
		equip_changed = true

	if inv_changed:
		inventory_changed.emit()
	if equip_changed:
		equipment_changed.emit()
