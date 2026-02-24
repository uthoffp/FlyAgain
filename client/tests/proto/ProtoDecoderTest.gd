## ProtoDecoderTest.gd
## Unit tests for the manual Protocol Buffer 3 decoder.
class_name ProtoDecoderTest
extends GdUnitTestSuite


# ---- Helpers: build protobuf fields as raw bytes ----

## Builds a varint field: tag(varint) + value(varint)
func _varint_field(field_num: int, value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_encode_varint((field_num << 3) | 0))  # tag
	buf.append_array(_encode_varint(value))
	return buf


## Builds a string field: tag(LD) + length(varint) + UTF-8 bytes
func _string_field(field_num: int, value: String) -> PackedByteArray:
	var payload := value.to_utf8_buffer()
	var buf := PackedByteArray()
	buf.append_array(_encode_varint((field_num << 3) | 2))  # tag
	buf.append_array(_encode_varint(payload.size()))
	buf.append_array(payload)
	return buf


## Builds a length-delimited sub-message field: tag(LD) + length + sub-message bytes
func _submessage_field(field_num: int, sub: PackedByteArray) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_encode_varint((field_num << 3) | 2))
	buf.append_array(_encode_varint(sub.size()))
	buf.append_array(sub)
	return buf


## Encodes an integer as a varint byte sequence
func _encode_varint(value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	var v := value
	while v > 127:
		buf.append((v & 0x7F) | 0x80)
		v >>= 7
	buf.append(v & 0x7F)
	return buf


# ---- RegisterResponse ----

func test_decode_register_response_success() -> void:
	var data := PackedByteArray()
	data.append_array(_varint_field(1, 1))  # success = true
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_register_response()
	assert_bool(result["success"]).is_true()
	assert_str(result["error_message"]).is_empty()


func test_decode_register_response_failure_with_message() -> void:
	var data := PackedByteArray()
	data.append_array(_varint_field(1, 0))               # success = false
	data.append_array(_string_field(2, "Name taken"))     # error_message
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_register_response()
	assert_bool(result["success"]).is_false()
	assert_str(result["error_message"]).is_equal("Name taken")


func test_decode_register_response_empty_payload() -> void:
	var decoder := ProtoDecoder.new(PackedByteArray())
	var result := decoder.decode_register_response()
	assert_bool(result["success"]).is_false()
	assert_str(result["error_message"]).is_empty()


# ---- Heartbeat ----

func test_decode_heartbeat() -> void:
	var data := PackedByteArray()
	data.append_array(_varint_field(1, 1000))   # client_time
	data.append_array(_varint_field(2, 2000))   # server_time
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_heartbeat()
	assert_int(result["client_time"]).is_equal(1000)
	assert_int(result["server_time"]).is_equal(2000)


func test_decode_heartbeat_empty() -> void:
	var decoder := ProtoDecoder.new(PackedByteArray())
	var result := decoder.decode_heartbeat()
	assert_int(result["client_time"]).is_equal(0)
	assert_int(result["server_time"]).is_equal(0)


# ---- ErrorResponse ----

func test_decode_error_response() -> void:
	var data := PackedByteArray()
	data.append_array(_varint_field(1, 0x0001))         # original_opcode
	data.append_array(_varint_field(2, 403))             # error_code
	data.append_array(_string_field(3, "Forbidden"))     # message
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_error_response()
	assert_int(result["original_opcode"]).is_equal(1)
	assert_int(result["error_code"]).is_equal(403)
	assert_str(result["message"]).is_equal("Forbidden")


func test_decode_error_response_empty() -> void:
	var decoder := ProtoDecoder.new(PackedByteArray())
	var result := decoder.decode_error_response()
	assert_int(result["original_opcode"]).is_equal(0)
	assert_int(result["error_code"]).is_equal(0)
	assert_str(result["message"]).is_empty()


# ---- CharacterInfo ----

func test_decode_character_info() -> void:
	var data := PackedByteArray()
	data.append_array(_string_field(1, "550e8400-e29b-41d4-a716-446655440000"))  # id (UUID string)
	data.append_array(_string_field(2, "Krieger"))       # name
	data.append_array(_string_field(3, "WARRIOR"))       # character_class
	data.append_array(_varint_field(4, 10))              # level
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_character_info()
	assert_str(result["id"]).is_equal("550e8400-e29b-41d4-a716-446655440000")
	assert_str(result["name"]).is_equal("Krieger")
	assert_str(result["character_class"]).is_equal("WARRIOR")
	assert_int(result["level"]).is_equal(10)


func test_decode_character_info_defaults() -> void:
	var decoder := ProtoDecoder.new(PackedByteArray())
	var result := decoder.decode_character_info()
	assert_str(result["id"]).is_empty()
	assert_str(result["name"]).is_empty()
	assert_str(result["character_class"]).is_empty()
	assert_int(result["level"]).is_equal(0)


# ---- ServerMessage ----

func test_decode_server_message() -> void:
	var data := PackedByteArray()
	data.append_array(_string_field(1, "info"))
	data.append_array(_string_field(2, "Server restarting"))
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_server_message()
	assert_str(result["type"]).is_equal("info")
	assert_str(result["text"]).is_equal("Server restarting")


# ---- EnterWorldResponse ----

func test_decode_enter_world_response_success() -> void:
	var data := PackedByteArray()
	data.append_array(_varint_field(1, 1))                      # success
	data.append_array(_string_field(5, "world.flyagain.de"))     # host
	data.append_array(_varint_field(6, 7780))                    # tcp_port
	data.append_array(_varint_field(7, 7781))                    # udp_port
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_enter_world_response()
	assert_bool(result["success"]).is_true()
	assert_str(result["world_service_host"]).is_equal("world.flyagain.de")
	assert_int(result["world_service_tcp_port"]).is_equal(7780)
	assert_int(result["world_service_udp_port"]).is_equal(7781)


func test_decode_enter_world_response_failure() -> void:
	var data := PackedByteArray()
	data.append_array(_varint_field(1, 0))                        # success = false
	data.append_array(_string_field(4, "Character not found"))    # error_message
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_enter_world_response()
	assert_bool(result["success"]).is_false()
	assert_str(result["error_message"]).is_equal("Character not found")


# ---- LoginResponse with embedded CharacterInfo ----

func test_decode_login_response_full() -> void:
	# Build a CharacterInfo sub-message
	var char_data := PackedByteArray()
	char_data.append_array(_string_field(1, "a1b2c3d4-e5f6-7890-abcd-ef1234567890"))  # id (UUID)
	char_data.append_array(_string_field(2, "Magier"))     # name
	char_data.append_array(_string_field(3, "MAGE"))       # class
	char_data.append_array(_varint_field(4, 25))           # level

	var data := PackedByteArray()
	data.append_array(_varint_field(1, 1))                          # success
	data.append_array(_string_field(2, "eyJhbGciOi.jwt.sig"))      # jwt
	data.append_array(_submessage_field(3, char_data))              # characters[0]
	data.append_array(_string_field(5, "hmac-secret-key"))          # hmac_secret
	data.append_array(_string_field(6, "account.flyagain.de"))      # account_service_host
	data.append_array(_varint_field(7, 7779))                       # account_service_port

	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_login_response()
	assert_bool(result["success"]).is_true()
	assert_str(result["jwt"]).is_equal("eyJhbGciOi.jwt.sig")
	assert_str(result["hmac_secret"]).is_equal("hmac-secret-key")
	assert_str(result["account_service_host"]).is_equal("account.flyagain.de")
	assert_int(result["account_service_port"]).is_equal(7779)
	# Verify embedded character
	assert_array(result["characters"]).has_size(1)
	var char_info: Dictionary = result["characters"][0]
	assert_str(char_info["id"]).is_equal("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
	assert_str(char_info["name"]).is_equal("Magier")
	assert_str(char_info["character_class"]).is_equal("MAGE")
	assert_int(char_info["level"]).is_equal(25)


func test_decode_login_response_multiple_characters() -> void:
	var char1 := PackedByteArray()
	char1.append_array(_string_field(1, "11111111-1111-1111-1111-111111111111"))
	char1.append_array(_string_field(2, "Krieger"))
	char1.append_array(_string_field(3, "WARRIOR"))
	char1.append_array(_varint_field(4, 10))

	var char2 := PackedByteArray()
	char2.append_array(_string_field(1, "22222222-2222-2222-2222-222222222222"))
	char2.append_array(_string_field(2, "Magier"))
	char2.append_array(_string_field(3, "MAGE"))
	char2.append_array(_varint_field(4, 5))

	var data := PackedByteArray()
	data.append_array(_varint_field(1, 1))                 # success
	data.append_array(_string_field(2, "jwt"))             # jwt
	data.append_array(_submessage_field(3, char1))         # character 1
	data.append_array(_submessage_field(3, char2))         # character 2

	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_login_response()
	assert_array(result["characters"]).has_size(2)
	assert_str(result["characters"][0]["name"]).is_equal("Krieger")
	assert_str(result["characters"][1]["name"]).is_equal("Magier")


# ---- Unknown field skipping ----

func test_unknown_varint_field_skipped() -> void:
	var data := PackedByteArray()
	data.append_array(_varint_field(1, 1))          # success (known)
	data.append_array(_varint_field(99, 12345))     # unknown field 99
	data.append_array(_string_field(2, ""))          # error_message
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_register_response()
	assert_bool(result["success"]).is_true()


func test_unknown_string_field_skipped() -> void:
	var data := PackedByteArray()
	data.append_array(_string_field(50, "garbage"))  # unknown LD field
	data.append_array(_varint_field(1, 1000))        # client_time
	var decoder := ProtoDecoder.new(data)
	var result := decoder.decode_heartbeat()
	assert_int(result["client_time"]).is_equal(1000)
