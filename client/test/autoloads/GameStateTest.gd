## GameStateTest.gd
## Unit tests for the GameState autoload (session & character data).
class_name GameStateTest
extends GdUnitTestSuite

const GameStateScript = preload("res://autoloads/GameState.gd")

var _state: Node  # GameState extends Node


func before_test() -> void:
	_state = auto_free(GameStateScript.new())


# ---- Authentication ----

func test_initial_state_unauthenticated() -> void:
	assert_bool(_state.is_authenticated()).is_false()
	assert_str(_state.jwt).is_empty()


func test_is_authenticated_with_jwt() -> void:
	_state.jwt = "eyJhbGciOiJIUzI1NiJ9.test.sig"
	assert_bool(_state.is_authenticated()).is_true()


func test_is_authenticated_empty_jwt() -> void:
	_state.jwt = ""
	assert_bool(_state.is_authenticated()).is_false()


# ---- Reset ----

func test_reset_clears_all_fields() -> void:
	_state.jwt = "some-jwt"
	_state.hmac_secret = "secret"
	_state.account_service_host = "localhost"
	_state.account_service_port = 7779
	_state.world_service_host = "localhost"
	_state.world_service_tcp_port = 7780
	_state.world_service_udp_port = 7781
	_state.characters = [{"id": "abc-123", "name": "Test"}]
	_state.selected_character_id = "abc-123"

	_state.reset()

	assert_str(_state.jwt).is_empty()
	assert_str(_state.hmac_secret).is_empty()
	assert_str(_state.account_service_host).is_empty()
	assert_int(_state.account_service_port).is_equal(0)
	assert_str(_state.world_service_host).is_empty()
	assert_int(_state.world_service_tcp_port).is_equal(0)
	assert_int(_state.world_service_udp_port).is_equal(0)
	assert_array(_state.characters).is_empty()
	assert_str(_state.selected_character_id).is_empty()


func test_reset_makes_unauthenticated() -> void:
	_state.jwt = "valid-jwt"
	assert_bool(_state.is_authenticated()).is_true()
	_state.reset()
	assert_bool(_state.is_authenticated()).is_false()


# ---- Character data ----

func test_characters_storage() -> void:
	_state.characters = [
		{"id": "11111111-1111-1111-1111-111111111111", "name": "TestWarrior", "character_class": "WARRIOR", "level": 10},
		{"id": "22222222-2222-2222-2222-222222222222", "name": "TestMage", "character_class": "MAGE", "level": 5},
	]
	assert_array(_state.characters).has_size(2)
	assert_str(_state.characters[0]["name"]).is_equal("TestWarrior")
	assert_str(_state.characters[1]["name"]).is_equal("TestMage")


func test_selected_character_id() -> void:
	_state.selected_character_id = "550e8400-e29b-41d4-a716-446655440000"
	assert_str(_state.selected_character_id).is_equal("550e8400-e29b-41d4-a716-446655440000")


# ---- Session identifiers ----

func test_session_id_storage() -> void:
	_state.session_id = "sess-abc-123"
	assert_str(_state.session_id).is_equal("sess-abc-123")


func test_session_token_storage() -> void:
	_state.session_token = 1234567890
	assert_int(_state.session_token).is_equal(1234567890)


func test_reset_clears_session_fields() -> void:
	_state.session_id = "sess-xyz"
	_state.session_token = 99999
	_state.reset()
	assert_str(_state.session_id).is_empty()
	assert_int(_state.session_token).is_equal(0)


# ---- Player in-world data ----

func test_entity_and_zone_defaults() -> void:
	assert_int(_state.my_entity_id).is_equal(0)
	assert_int(_state.current_zone_id).is_equal(0)
	assert_int(_state.current_channel_id).is_equal(0)
	assert_str(_state.current_zone_name).is_empty()


func test_player_position_storage() -> void:
	_state.player_position = Vector3(100.0, 50.0, 200.0)
	assert_float(_state.player_position.x).is_equal(100.0)
	assert_float(_state.player_position.y).is_equal(50.0)
	assert_float(_state.player_position.z).is_equal(200.0)


func test_reset_clears_world_data() -> void:
	_state.my_entity_id = 42
	_state.current_zone_id = 2
	_state.current_channel_id = 1
	_state.current_zone_name = "Green Plains"
	_state.player_position = Vector3(100.0, 50.0, 200.0)
	_state.player_rotation = 1.57
	_state.reset()
	assert_int(_state.my_entity_id).is_equal(0)
	assert_int(_state.current_zone_id).is_equal(0)
	assert_int(_state.current_channel_id).is_equal(0)
	assert_str(_state.current_zone_name).is_empty()
	assert_float(_state.player_position.x).is_equal(0.0)
	assert_float(_state.player_rotation).is_equal(0.0)


# ---- Player stats ----

func test_player_stats_defaults() -> void:
	assert_int(_state.player_level).is_equal(1)
	assert_int(_state.player_hp).is_equal(100)
	assert_int(_state.player_max_hp).is_equal(100)
	assert_int(_state.player_mp).is_equal(50)
	assert_int(_state.player_max_mp).is_equal(50)
	assert_int(_state.player_xp).is_equal(0)
	assert_int(_state.player_gold).is_equal(0)
	assert_int(_state.player_xp_to_next_level).is_equal(100)


func test_player_stats_storage() -> void:
	_state.player_level = 15
	_state.player_hp = 250
	_state.player_max_hp = 300
	_state.player_mp = 80
	_state.player_max_mp = 120
	_state.player_str = 30
	_state.player_sta = 25
	_state.player_dex = 20
	_state.player_int = 15
	_state.player_xp = 5000
	_state.player_gold = 10000
	_state.player_xp_to_next_level = 8000
	assert_int(_state.player_level).is_equal(15)
	assert_int(_state.player_hp).is_equal(250)
	assert_int(_state.player_max_hp).is_equal(300)
	assert_int(_state.player_mp).is_equal(80)
	assert_int(_state.player_max_mp).is_equal(120)
	assert_int(_state.player_str).is_equal(30)
	assert_int(_state.player_sta).is_equal(25)
	assert_int(_state.player_dex).is_equal(20)
	assert_int(_state.player_int).is_equal(15)
	assert_int(_state.player_xp).is_equal(5000)
	assert_int(_state.player_gold).is_equal(10000)
	assert_int(_state.player_xp_to_next_level).is_equal(8000)


func test_reset_restores_stat_defaults() -> void:
	_state.player_level = 50
	_state.player_hp = 1
	_state.player_max_hp = 999
	_state.player_mp = 0
	_state.player_max_mp = 500
	_state.player_str = 100
	_state.player_sta = 100
	_state.player_dex = 100
	_state.player_int = 100
	_state.player_xp = 99999
	_state.player_gold = 50000
	_state.player_xp_to_next_level = 200000
	_state.reset()
	assert_int(_state.player_level).is_equal(1)
	assert_int(_state.player_hp).is_equal(100)
	assert_int(_state.player_max_hp).is_equal(100)
	assert_int(_state.player_mp).is_equal(50)
	assert_int(_state.player_max_mp).is_equal(50)
	assert_int(_state.player_str).is_equal(0)
	assert_int(_state.player_sta).is_equal(0)
	assert_int(_state.player_dex).is_equal(0)
	assert_int(_state.player_int).is_equal(0)
	assert_int(_state.player_xp).is_equal(0)
	assert_int(_state.player_gold).is_equal(0)
	assert_int(_state.player_xp_to_next_level).is_equal(100)


# ---- Target state ----

func test_target_state_defaults() -> void:
	assert_int(_state.selected_target_id).is_equal(0)
	assert_str(_state.selected_target_name).is_empty()
	assert_int(_state.selected_target_level).is_equal(0)
	assert_int(_state.selected_target_hp).is_equal(0)
	assert_int(_state.selected_target_max_hp).is_equal(0)
	assert_bool(_state.auto_attack_active).is_false()


func test_target_state_storage() -> void:
	_state.selected_target_id = 42
	_state.selected_target_name = "Giant Spider"
	_state.selected_target_level = 10
	_state.selected_target_hp = 75
	_state.selected_target_max_hp = 100
	_state.auto_attack_active = true
	assert_int(_state.selected_target_id).is_equal(42)
	assert_str(_state.selected_target_name).is_equal("Giant Spider")
	assert_int(_state.selected_target_level).is_equal(10)
	assert_int(_state.selected_target_hp).is_equal(75)
	assert_int(_state.selected_target_max_hp).is_equal(100)
	assert_bool(_state.auto_attack_active).is_true()


func test_reset_clears_target_state() -> void:
	_state.selected_target_id = 99
	_state.selected_target_name = "Boss"
	_state.selected_target_level = 50
	_state.selected_target_hp = 500
	_state.selected_target_max_hp = 1000
	_state.auto_attack_active = true
	_state.reset()
	assert_int(_state.selected_target_id).is_equal(0)
	assert_str(_state.selected_target_name).is_empty()
	assert_int(_state.selected_target_level).is_equal(0)
	assert_int(_state.selected_target_hp).is_equal(0)
	assert_int(_state.selected_target_max_hp).is_equal(0)
	assert_bool(_state.auto_attack_active).is_false()
