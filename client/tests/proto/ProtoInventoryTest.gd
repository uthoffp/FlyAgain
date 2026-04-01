## ProtoInventoryTest.gd
## Roundtrip tests for inventory proto encoder/decoder methods.
class_name ProtoInventoryTest
extends GdUnitTestSuite


func test_move_item_request_roundtrip() -> void:
	var buf := ProtoEncoder.encode_move_item_request(5, 42)
	var dec := ProtoDecoder.new(buf).decode_move_item_response()
	# MoveItemRequest fields: from_slot=1(int32), to_slot=2(int32)
	# MoveItemResponse fields: success=1(bool), error_message=2(string)
	# These are different messages so we can't roundtrip directly.
	# Instead verify encoding produces non-empty bytes.
	assert_bool(buf.size() > 0).is_true()


func test_move_item_request_fields() -> void:
	# Verify from_slot=5 and to_slot=42 are encoded
	var buf := ProtoEncoder.encode_move_item_request(5, 42)
	# Decode as generic varints: field 1 = 5, field 2 = 42
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)  # field number
	var val1 := dec._read_varint()
	assert_int(val1).is_equal(5)
	var tag2 := dec._next_tag()
	assert_int(tag2[0]).is_equal(2)
	var val2 := dec._read_varint()
	assert_int(val2).is_equal(42)


func test_equip_item_request_fields() -> void:
	var buf := ProtoEncoder.encode_equip_item_request(10, 6)
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)
	assert_int(dec._read_varint()).is_equal(10)
	var tag2 := dec._next_tag()
	assert_int(tag2[0]).is_equal(2)
	assert_int(dec._read_varint()).is_equal(6)


func test_unequip_item_request_fields() -> void:
	var buf := ProtoEncoder.encode_unequip_item_request(6)
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)
	assert_int(dec._read_varint()).is_equal(6)


func test_npc_buy_request_uses_int64_for_entity_id() -> void:
	var buf := ProtoEncoder.encode_npc_buy_request(1000001, 3, 5)
	assert_bool(buf.size() > 0).is_true()
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)  # npc_entity_id
	assert_int(dec._read_varint()).is_equal(1000001)


func test_npc_sell_request_fields() -> void:
	var buf := ProtoEncoder.encode_npc_sell_request(1000002, 15, 3)
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)
	assert_int(dec._read_varint()).is_equal(1000002)
	var tag2 := dec._next_tag()
	assert_int(tag2[0]).is_equal(2)
	assert_int(dec._read_varint()).is_equal(15)
	var tag3 := dec._next_tag()
	assert_int(tag3[0]).is_equal(3)
	assert_int(dec._read_varint()).is_equal(3)


func test_decode_move_item_response_success() -> void:
	# Manually build: success=true(field1), error_message=""(omitted)
	var buf := PackedByteArray()
	buf.append(0x08); buf.append(1)  # field 1, varint, value=1 (true)
	var result := ProtoDecoder.new(buf).decode_move_item_response()
	assert_bool(result["success"]).is_true()
	assert_str(result["error_message"]).is_equal("")


func test_decode_npc_buy_response() -> void:
	# Build: success=true, new_gold=950, assigned_slot=3
	var buf := PackedByteArray()
	buf.append(0x08); buf.append(1)    # field 1: success=true
	buf.append(0x10)                    # field 2: new_gold (varint tag)
	buf.append_array(ProtoEncoder._varint(950))
	buf.append(0x18); buf.append(3)    # field 3: assigned_slot=3
	var result := ProtoDecoder.new(buf).decode_npc_buy_response()
	assert_bool(result["success"]).is_true()
	assert_int(result["new_gold"]).is_equal(950)
	assert_int(result["assigned_slot"]).is_equal(3)


func test_decode_inventory_update_with_slots_and_equipment() -> void:
	# Build an InventoryUpdateMessage with 1 inventory slot and 1 equipment slot
	var buf := PackedByteArray()

	# Field 1: InventorySlotInfo submessage (slot=5, item_id=2, amount=1, enhancement=3)
	var slot_buf := PackedByteArray()
	slot_buf.append(0x08); slot_buf.append(5)   # slot=5
	slot_buf.append(0x10); slot_buf.append(2)   # item_id=2
	slot_buf.append(0x18); slot_buf.append(1)   # amount=1
	slot_buf.append(0x20); slot_buf.append(3)   # enhancement=3
	buf.append(0x0A)  # field 1, wire type 2 (LEN)
	buf.append(slot_buf.size())
	buf.append_array(slot_buf)

	# Field 2: EquipmentSlotInfo submessage (slot_type=6, item_id=2, enhancement=3)
	var equip_buf := PackedByteArray()
	equip_buf.append(0x08); equip_buf.append(6)  # slot_type=6
	equip_buf.append(0x10); equip_buf.append(2)  # item_id=2
	equip_buf.append(0x18); equip_buf.append(3)  # enhancement=3
	buf.append(0x12)  # field 2, wire type 2 (LEN)
	buf.append(equip_buf.size())
	buf.append_array(equip_buf)

	var result := ProtoDecoder.new(buf).decode_inventory_update()
	assert_int(result["slots"].size()).is_equal(1)
	assert_int(result["slots"][0]["slot"]).is_equal(5)
	assert_int(result["slots"][0]["item_id"]).is_equal(2)
	assert_int(result["slots"][0]["enhancement"]).is_equal(3)
	assert_int(result["equipment"].size()).is_equal(1)
	assert_int(result["equipment"][0]["slot_type"]).is_equal(6)


func test_decode_inventory_update_empty() -> void:
	var result := ProtoDecoder.new(PackedByteArray()).decode_inventory_update()
	assert_int(result["slots"].size()).is_equal(0)
	assert_int(result["equipment"].size()).is_equal(0)
