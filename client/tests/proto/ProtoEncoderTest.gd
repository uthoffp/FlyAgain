## ProtoEncoderTest.gd
## Unit tests for the manual Protocol Buffer 3 encoder.
class_name ProtoEncoderTest
extends GdUnitTestSuite


# ---- Helper: decode a varint from a buffer at a given offset ----

func _read_varint_at(buf: PackedByteArray, offset: int) -> Array:
	var result := 0
	var shift := 0
	var pos := offset
	while pos < buf.size():
		var b := buf[pos]
		result |= (b & 0x7F) << shift
		shift += 7
		pos += 1
		if (b & 0x80) == 0:
			break
	return [result, pos]  # [value, new_offset]


# ---- LoginRequest ----

func test_encode_login_request_basic() -> void:
	var buf := ProtoEncoder.encode_login_request("admin", "pass123")
	# Field 1 (username): tag = (1 << 3) | 2 = 0x0A
	assert_int(buf[0]).is_equal(0x0A)
	# Length of "admin" = 5
	assert_int(buf[1]).is_equal(5)
	# "admin" bytes
	assert_str(buf.slice(2, 7).get_string_from_utf8()).is_equal("admin")
	# Field 2 (password): tag = (2 << 3) | 2 = 0x12
	assert_int(buf[7]).is_equal(0x12)
	# Length of "pass123" = 7
	assert_int(buf[8]).is_equal(7)
	assert_str(buf.slice(9, 16).get_string_from_utf8()).is_equal("pass123")


func test_encode_login_request_empty_fields_omitted() -> void:
	# Proto3: empty strings are omitted
	var buf := ProtoEncoder.encode_login_request("", "")
	assert_int(buf.size()).is_equal(0)


func test_encode_login_request_partial_empty() -> void:
	# Only password provided, username omitted
	var buf := ProtoEncoder.encode_login_request("", "secret")
	# Should start with field 2 tag (0x12), not field 1
	assert_int(buf[0]).is_equal(0x12)


# ---- RegisterRequest ----

func test_encode_register_request() -> void:
	var buf := ProtoEncoder.encode_register_request("user1", "u@e.com", "secret")
	# Field 1: tag 0x0A
	assert_int(buf[0]).is_equal(0x0A)
	# Verify all three fields are present by checking total size
	# "user1"(5) + "u@e.com"(7) + "secret"(6) + 3 tags + 3 lengths = 24
	assert_int(buf.size()).is_equal(24)


func test_encode_register_request_all_empty() -> void:
	var buf := ProtoEncoder.encode_register_request("", "", "")
	assert_int(buf.size()).is_equal(0)


# ---- Heartbeat ----

func test_encode_heartbeat_nonzero() -> void:
	var buf := ProtoEncoder.encode_heartbeat(12345)
	assert_that(buf.size()).is_greater(0)
	# Field 1, wire type varint: tag = (1 << 3) | 0 = 0x08
	assert_int(buf[0]).is_equal(0x08)


func test_encode_heartbeat_zero_omitted() -> void:
	# Proto3: value 0 should be omitted
	var buf := ProtoEncoder.encode_heartbeat(0)
	assert_int(buf.size()).is_equal(0)


func test_encode_heartbeat_small_value() -> void:
	# Value 100 < 128 -> single varint byte
	var buf := ProtoEncoder.encode_heartbeat(100)
	# tag(1 byte) + varint(1 byte) = 2 bytes
	assert_int(buf.size()).is_equal(2)
	assert_int(buf[0]).is_equal(0x08)
	assert_int(buf[1]).is_equal(100)


func test_encode_heartbeat_large_value() -> void:
	# Value 300 > 127 -> multi-byte varint
	var buf := ProtoEncoder.encode_heartbeat(300)
	assert_int(buf[0]).is_equal(0x08)  # tag
	# 300 = 0b100101100 -> varint: 0xAC 0x02
	assert_int(buf[1]).is_equal(0xAC)
	assert_int(buf[2]).is_equal(0x02)


# ---- CharacterSelect ----

func test_encode_character_select() -> void:
	var buf := ProtoEncoder.encode_character_select("550e8400-e29b-41d4-a716-446655440000", "jwt.token")
	# Field 1 (character_id): tag = (1 << 3) | 2 = 0x0A (length-delimited string)
	assert_int(buf[0]).is_equal(0x0A)
	# Length of UUID string = 36
	assert_int(buf[1]).is_equal(36)
	# Field 2 (jwt): tag = (2 << 3) | 2 = 0x12
	# Offset: 1 (tag) + 1 (len) + 36 (uuid) = 38
	assert_int(buf[38]).is_equal(0x12)


func test_encode_character_select_empty_id_omitted() -> void:
	# character_id = "" is omitted (proto3 default), jwt stays
	var buf := ProtoEncoder.encode_character_select("", "jwt.token")
	# Should start directly with field 2 tag
	assert_int(buf[0]).is_equal(0x12)


# ---- CharacterCreate ----

func test_encode_character_create() -> void:
	var buf := ProtoEncoder.encode_character_create("TestWarrior", "WARRIOR", "jwt.tok")
	assert_int(buf[0]).is_equal(0x0A)  # Field 1 tag
	assert_that(buf.size()).is_greater(0)


# ---- Round-trip: Heartbeat (encode + decode) ----

func test_heartbeat_roundtrip() -> void:
	var time_ms := 1708123456789
	var encoded := ProtoEncoder.encode_heartbeat(time_ms)
	var decoder := ProtoDecoder.new(encoded)
	var result := decoder.decode_heartbeat()
	assert_int(result["client_time"]).is_equal(time_ms)


# ---- Varint encoding edge cases ----

func test_varint_single_byte() -> void:
	# Encode heartbeat with value 1 -> tag + single varint byte
	var buf := ProtoEncoder.encode_heartbeat(1)
	assert_int(buf.size()).is_equal(2)  # tag + 1 byte varint
	assert_int(buf[1]).is_equal(1)


func test_varint_boundary_127() -> void:
	# 127 fits in single varint byte
	var buf := ProtoEncoder.encode_heartbeat(127)
	assert_int(buf.size()).is_equal(2)
	assert_int(buf[1]).is_equal(127)


func test_varint_boundary_128() -> void:
	# 128 requires two varint bytes
	var buf := ProtoEncoder.encode_heartbeat(128)
	assert_int(buf.size()).is_equal(3)  # tag + 2 byte varint
	# 128 = 0x80 -> varint: 0x80 0x01
	assert_int(buf[1]).is_equal(0x80)
	assert_int(buf[2]).is_equal(0x01)
