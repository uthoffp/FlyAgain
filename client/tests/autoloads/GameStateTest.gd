## GameStateTest.gd
## Unit tests for the GameState autoload (session & character data).
class_name GameStateTest
extends GdUnitTestSuite

var _state: Node  # GameState extends Node


func before_test() -> void:
	_state = auto_free(GameState.new())


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
		{"id": "11111111-1111-1111-1111-111111111111", "name": "Krieger", "character_class": "WARRIOR", "level": 10},
		{"id": "22222222-2222-2222-2222-222222222222", "name": "Magier", "character_class": "MAGE", "level": 5},
	]
	assert_array(_state.characters).has_size(2)
	assert_str(_state.characters[0]["name"]).is_equal("Krieger")
	assert_str(_state.characters[1]["name"]).is_equal("Magier")


func test_selected_character_id() -> void:
	_state.selected_character_id = "550e8400-e29b-41d4-a716-446655440000"
	assert_str(_state.selected_character_id).is_equal("550e8400-e29b-41d4-a716-446655440000")
