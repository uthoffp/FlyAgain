## ProtoDecoderWorldTest.gd
## Tests for ProtoDecoder world message decoding (position, zone_data, entity_spawn, etc.).
class_name ProtoDecoderWorldTest
extends GdUnitTestSuite


# ---- Position decoding ----

func test_decode_position_all_fields() -> void:
	var encoded := ProtoEncoder.encode_position(Vector3(1.5, 2.5, 3.5))
	var dec := ProtoDecoder.new(encoded)
	var result := dec.decode_position()
	assert_float(result["x"]).is_equal_approx(1.5, 0.01)
	assert_float(result["y"]).is_equal_approx(2.5, 0.01)
	assert_float(result["z"]).is_equal_approx(3.5, 0.01)


func test_decode_position_empty_bytes() -> void:
	var dec := ProtoDecoder.new(PackedByteArray())
	var result := dec.decode_position()
	assert_float(result["x"]).is_equal(0.0)
	assert_float(result["y"]).is_equal(0.0)
	assert_float(result["z"]).is_equal(0.0)


func test_decode_position_negative_values() -> void:
	var encoded := ProtoEncoder.encode_position(Vector3(-50.0, -0.25, -999.9))
	var dec := ProtoDecoder.new(encoded)
	var result := dec.decode_position()
	assert_float(result["x"]).is_equal_approx(-50.0, 0.01)
	assert_float(result["y"]).is_equal_approx(-0.25, 0.01)
	assert_float(result["z"]).is_equal_approx(-999.9, 0.1)


func test_decode_position_single_nonzero_field() -> void:
	# Only Y is non-zero — x and z should remain at default 0.0
	var encoded := ProtoEncoder.encode_position(Vector3(0.0, 42.0, 0.0))
	var dec := ProtoDecoder.new(encoded)
	var result := dec.decode_position()
	assert_float(result["x"]).is_equal(0.0)
	assert_float(result["y"]).is_equal_approx(42.0, 0.01)
	assert_float(result["z"]).is_equal(0.0)


# ---- EntitySpawn decoding ----

func test_decode_entity_spawn_roundtrip() -> void:
	# Manually build an EntitySpawnMessage:
	# entity_id=1(varint), entity_type=2(varint), name=3(string),
	# position=4(submsg), rotation=5(float), level=6(varint),
	# hp=7(varint), max_hp=8(varint), character_class=9(varint), is_flying=10(bool)
	var buf := PackedByteArray()
	# Field 1: entity_id = 42 (varint, tag = 0x08)
	buf.append(0x08); buf.append(42)
	# Field 2: entity_type = 0 (player) — omitted (proto3 default)
	# Field 3: name = "TestPlayer" (string, tag = 0x1A)
	var name_bytes := "TestPlayer".to_utf8_buffer()
	buf.append(0x1A); buf.append(name_bytes.size()); buf.append_array(name_bytes)
	# Field 4: position submessage (tag = 0x22 = (4<<3)|2)
	var pos_bytes := ProtoEncoder.encode_position(Vector3(100.0, 5.0, 200.0))
	buf.append(0x22); buf.append(pos_bytes.size()); buf.append_array(pos_bytes)
	# Field 5: rotation = 1.57 (float, tag = 0x2D = (5<<3)|5)
	buf.append(0x2D)
	var rot_buf := PackedByteArray()
	rot_buf.resize(4)
	rot_buf.encode_float(0, 1.57)
	buf.append_array(rot_buf)
	# Field 6: level = 10 (varint, tag = 0x30)
	buf.append(0x30); buf.append(10)
	# Field 7: hp = 500 (varint, tag = 0x38)
	buf.append(0x38); buf.append_array(_varint(500))
	# Field 8: max_hp = 500 (varint, tag = 0x40)
	buf.append(0x40); buf.append_array(_varint(500))

	var dec := ProtoDecoder.new(buf)
	var result := dec.decode_entity_spawn()
	assert_int(result["entity_id"]).is_equal(42)
	assert_int(result["entity_type"]).is_equal(0)
	assert_str(result["name"]).is_equal("TestPlayer")
	assert_float(result["position"]["x"]).is_equal_approx(100.0, 0.01)
	assert_float(result["position"]["y"]).is_equal_approx(5.0, 0.01)
	assert_float(result["position"]["z"]).is_equal_approx(200.0, 0.01)
	assert_float(result["rotation"]).is_equal_approx(1.57, 0.01)
	assert_int(result["level"]).is_equal(10)
	assert_int(result["hp"]).is_equal(500)
	assert_int(result["max_hp"]).is_equal(500)


func test_decode_entity_spawn_defaults() -> void:
	# Empty bytes → all defaults
	var dec := ProtoDecoder.new(PackedByteArray())
	var result := dec.decode_entity_spawn()
	assert_int(result["entity_id"]).is_equal(0)
	assert_str(result["name"]).is_equal("")
	assert_bool(result["is_flying"]).is_false()


# ---- EntityPositionUpdate decoding ----

func test_decode_entity_position_update() -> void:
	var buf := PackedByteArray()
	# Field 1: entity_id = 7 (varint, tag = 0x08)
	buf.append(0x08); buf.append(7)
	# Field 2: position submessage (tag = 0x12 = (2<<3)|2)
	var pos_bytes := ProtoEncoder.encode_position(Vector3(50.0, 1.0, 75.0))
	buf.append(0x12); buf.append(pos_bytes.size()); buf.append_array(pos_bytes)
	# Field 3: rotation = 3.14 (float, tag = 0x1D = (3<<3)|5)
	buf.append(0x1D)
	var rot_buf := PackedByteArray()
	rot_buf.resize(4)
	rot_buf.encode_float(0, 3.14)
	buf.append_array(rot_buf)
	# Field 4: is_moving = true (varint, tag = 0x20)
	buf.append(0x20); buf.append(1)
	# Field 5: is_flying = false — omitted (proto3 default)

	var dec := ProtoDecoder.new(buf)
	var result := dec.decode_entity_position_update()
	assert_int(result["entity_id"]).is_equal(7)
	assert_float(result["position"]["x"]).is_equal_approx(50.0, 0.01)
	assert_float(result["rotation"]).is_equal_approx(3.14, 0.01)
	assert_bool(result["is_moving"]).is_true()
	assert_bool(result["is_flying"]).is_false()


# ---- PositionCorrection decoding ----

func test_decode_position_correction() -> void:
	var buf := PackedByteArray()
	# Field 1: position submessage (tag = 0x0A = (1<<3)|2)
	var pos_bytes := ProtoEncoder.encode_position(Vector3(500.0, 0.0, 500.0))
	buf.append(0x0A); buf.append(pos_bytes.size()); buf.append_array(pos_bytes)
	# Field 2: rotation = 0.0 — omitted (default)
	# Field 3: reason = "speed_violation" (tag = 0x1A = (3<<3)|2)
	var reason_bytes := "speed_violation".to_utf8_buffer()
	buf.append(0x1A); buf.append(reason_bytes.size()); buf.append_array(reason_bytes)

	var dec := ProtoDecoder.new(buf)
	var result := dec.decode_position_correction()
	assert_float(result["position"]["x"]).is_equal_approx(500.0, 0.01)
	assert_float(result["rotation"]).is_equal(0.0)
	assert_str(result["reason"]).is_equal("speed_violation")


# ---- EntityDespawn decoding ----

func test_decode_entity_despawn() -> void:
	var buf := PackedByteArray()
	# Field 1: entity_id = 99 (varint, tag = 0x08)
	buf.append(0x08); buf.append(99)

	var dec := ProtoDecoder.new(buf)
	var result := dec.decode_entity_despawn()
	assert_int(result["entity_id"]).is_equal(99)


func test_decode_entity_despawn_empty() -> void:
	var dec := ProtoDecoder.new(PackedByteArray())
	var result := dec.decode_entity_despawn()
	assert_int(result["entity_id"]).is_equal(0)


# ---- ZoneData decoding ----

func test_decode_zone_data_with_entities() -> void:
	var buf := PackedByteArray()
	# Field 1: zone_id = 1 (varint, tag = 0x08)
	buf.append(0x08); buf.append(1)
	# Field 2: channel_id = 2 (varint, tag = 0x10)
	buf.append(0x10); buf.append(2)
	# Field 3: zone_name = "Aerheim" (tag = 0x1A)
	var name_bytes := "Aerheim".to_utf8_buffer()
	buf.append(0x1A); buf.append(name_bytes.size()); buf.append_array(name_bytes)
	# Field 4: entities (repeated submessage, tag = 0x22)
	# Add one minimal entity: just entity_id=1
	var entity_buf := PackedByteArray()
	entity_buf.append(0x08); entity_buf.append(1)
	buf.append(0x22); buf.append(entity_buf.size()); buf.append_array(entity_buf)

	var dec := ProtoDecoder.new(buf)
	var result := dec.decode_zone_data()
	assert_int(result["zone_id"]).is_equal(1)
	assert_int(result["channel_id"]).is_equal(2)
	assert_str(result["zone_name"]).is_equal("Aerheim")
	assert_int(result["entities"].size()).is_equal(1)
	assert_int(result["entities"][0]["entity_id"]).is_equal(1)


func test_decode_zone_data_empty() -> void:
	var dec := ProtoDecoder.new(PackedByteArray())
	var result := dec.decode_zone_data()
	assert_int(result["zone_id"]).is_equal(0)
	assert_str(result["zone_name"]).is_equal("")
	assert_int(result["entities"].size()).is_equal(0)


func test_decode_zone_data_multiple_entities() -> void:
	var buf := PackedByteArray()
	# Field 1: zone_id = 2
	buf.append(0x08); buf.append(2)

	# Add two entities
	for eid in [10, 20]:
		var entity_buf := PackedByteArray()
		entity_buf.append(0x08); entity_buf.append(eid)
		buf.append(0x22); buf.append(entity_buf.size()); buf.append_array(entity_buf)

	var dec := ProtoDecoder.new(buf)
	var result := dec.decode_zone_data()
	assert_int(result["entities"].size()).is_equal(2)
	assert_int(result["entities"][0]["entity_id"]).is_equal(10)
	assert_int(result["entities"][1]["entity_id"]).is_equal(20)


# ---- Helper: encode a varint ----

func _varint(value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	var v := value
	while v > 127:
		buf.append((v & 0x7F) | 0x80)
		v >>= 7
	buf.append(v & 0x7F)
	return buf
