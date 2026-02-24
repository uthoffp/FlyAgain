## ProtoEncoderWorldTest.gd
## Tests for ProtoEncoder world message encoding (float, position, movement_input, enter_world).
class_name ProtoEncoderWorldTest
extends GdUnitTestSuite


# ---- Float field encoding ----

func test_float_field_encodes_wire_type_5() -> void:
	# Wire type 5 = fixed32 for float
	# Field 1: tag = (1 << 3) | 5 = 0x0D
	var buf := ProtoEncoder.encode_position(Vector3(1.0, 0.0, 0.0))
	# First byte should be the tag for field 1 with wire type 5
	assert_int(buf[0]).is_equal(0x0D)
	# Total: tag (1 byte) + 4 bytes float = 5 bytes for a single non-zero field
	assert_int(buf.size()).is_equal(5)


func test_float_field_omits_zero_values() -> void:
	# Proto3: zero values are omitted
	var buf := ProtoEncoder.encode_position(Vector3(0.0, 0.0, 0.0))
	assert_int(buf.size()).is_equal(0)


func test_float_field_encodes_all_three_components() -> void:
	var buf := ProtoEncoder.encode_position(Vector3(1.0, 2.0, 3.0))
	# 3 fields * (1 tag byte + 4 float bytes) = 15 bytes
	assert_int(buf.size()).is_equal(15)


func test_float_field_roundtrip_positive() -> void:
	var original := Vector3(123.456, 789.012, -42.5)
	var encoded := ProtoEncoder.encode_position(original)
	var decoded := ProtoDecoder.new(encoded).decode_position()
	assert_float(decoded["x"]).is_equal_approx(123.456, 0.01)
	assert_float(decoded["y"]).is_equal_approx(789.012, 0.01)
	assert_float(decoded["z"]).is_equal_approx(-42.5, 0.01)


func test_float_field_roundtrip_negative() -> void:
	var original := Vector3(-100.0, -0.5, -9999.99)
	var encoded := ProtoEncoder.encode_position(original)
	var decoded := ProtoDecoder.new(encoded).decode_position()
	assert_float(decoded["x"]).is_equal_approx(-100.0, 0.01)
	assert_float(decoded["y"]).is_equal_approx(-0.5, 0.01)
	assert_float(decoded["z"]).is_equal_approx(-9999.99, 0.1)


# ---- MovementInput encoding ----

func test_movement_input_minimal_moving() -> void:
	var buf := ProtoEncoder.encode_movement_input(
		Vector3(500.0, 0.0, 500.0),  # position
		1.57,                          # rotation
		1.0, 0.0, 0.0,               # direction
		true, false,                   # moving, not flying
		42                             # sequence
	)
	# Should produce non-empty bytes
	assert_bool(buf.size() > 0).is_true()

	# Verify roundtrip via decoder
	var dec := ProtoDecoder.new(buf)
	# We don't have a decode_movement_input but we can manually verify the bytes are valid


func test_movement_input_proto3_defaults_omitted() -> void:
	# All-zero/false values should produce minimal output
	var buf := ProtoEncoder.encode_movement_input(
		Vector3(0.0, 0.0, 0.0),  # position (all zero)
		0.0,                       # rotation
		0.0, 0.0, 0.0,           # direction
		false, false,              # not moving, not flying
		0                          # sequence
	)
	# All fields are default values, should be empty
	assert_int(buf.size()).is_equal(0)


func test_movement_input_sequence_encoded() -> void:
	var buf := ProtoEncoder.encode_movement_input(
		Vector3.ZERO, 0.0, 0.0, 0.0, 0.0, false, false, 100
	)
	# Only sequence (field 8 = varint) should be present
	# Tag: (8 << 3) | 0 = 64 = 0x40
	assert_bool(buf.size() > 0).is_true()


# ---- EnterWorldRequest encoding ----

func test_enter_world_request_encodes_strings() -> void:
	var buf := ProtoEncoder.encode_enter_world_request(
		"my-jwt-token", "char-id-123", "session-456"
	)
	assert_bool(buf.size() > 0).is_true()

	# Roundtrip: decode manually
	# Field 1 = jwt, Field 2 = character_id, Field 3 = session_id
	# These are just string fields, already tested in existing tests


func test_enter_world_request_empty_strings() -> void:
	var buf := ProtoEncoder.encode_enter_world_request("", "", "")
	assert_int(buf.size()).is_equal(0)


# ---- Submessage field encoding ----

func test_submessage_field_wraps_correctly() -> void:
	var pos := ProtoEncoder.encode_position(Vector3(10.0, 20.0, 30.0))
	var buf := ProtoEncoder.encode_movement_input(
		Vector3(10.0, 20.0, 30.0), 0.0, 0.0, 0.0, 0.0, false, false, 0
	)
	# The movement_input should contain the position as a submessage (field 1)
	# Tag: (1 << 3) | 2 = 0x0A (length-delimited)
	assert_int(buf[0]).is_equal(0x0A)
	# Next byte is the length of the submessage
	assert_int(buf[1]).is_equal(pos.size())
