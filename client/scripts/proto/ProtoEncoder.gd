## ProtoEncoder.gd
## Manual Protocol Buffer 3 encoder for FlyAgain messages.
## All methods are static — no instance needed.
##
## Proto3 wire format reference: https://protobuf.dev/programming-guides/encoding/
##   Wire types: 0=Varint  1=64-bit  2=LenDelimited  5=32-bit
##   Field tag:  (field_number << 3) | wire_type
class_name ProtoEncoder
extends RefCounted

# Wire type constants
const _WT_VARINT := 0
const _WT_LEN    := 2


## Encodes a LoginRequest { username=1, password=2 }.
static func encode_login_request(username: String, password: String) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_string_field(1, username))
	buf.append_array(_string_field(2, password))
	return buf


## Encodes a RegisterRequest { username=1, email=2, password=3 }.
static func encode_register_request(
	username: String,
	email: String,
	password: String
) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_string_field(1, username))
	buf.append_array(_string_field(2, email))
	buf.append_array(_string_field(3, password))
	return buf


## Encodes a Heartbeat { client_time=1 }.
static func encode_heartbeat(client_time_ms: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_int64_field(1, client_time_ms))
	return buf


## Encodes a CharacterSelectRequest { character_id=1, jwt=2 }.
static func encode_character_select(character_id: int, jwt: String) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_int64_field(1, character_id))
	buf.append_array(_string_field(2, jwt))
	return buf


## Encodes a CharacterCreateRequest { name=1, character_class=2, jwt=3 }.
static func encode_character_create(
	name: String,
	character_class: String,
	jwt: String
) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_string_field(1, name))
	buf.append_array(_string_field(2, character_class))
	buf.append_array(_string_field(3, jwt))
	return buf


# ---- Private helpers ----

static func _tag(field_num: int, wire_type: int) -> int:
	return (field_num << 3) | wire_type


## Encodes a non-negative integer as a varint byte sequence.
static func _varint(value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	var v   := value
	while v > 127:
		buf.append((v & 0x7F) | 0x80)
		v >>= 7
	buf.append(v & 0x7F)
	return buf


## Encodes a string field (wire type 2: length-delimited).
## Skips the field if the string is empty (proto3 default-value omission).
static func _string_field(field_num: int, value: String) -> PackedByteArray:
	if value.is_empty():
		return PackedByteArray()
	var payload := value.to_utf8_buffer()
	var buf     := PackedByteArray()
	buf.append_array(_varint(_tag(field_num, _WT_LEN)))
	buf.append_array(_varint(payload.size()))
	buf.append_array(payload)
	return buf


## Encodes an int32 field (wire type 0: varint).
## Skips the field if value == 0 (proto3 default-value omission).
static func _int32_field(field_num: int, value: int) -> PackedByteArray:
	if value == 0:
		return PackedByteArray()
	var buf := PackedByteArray()
	buf.append_array(_varint(_tag(field_num, _WT_VARINT)))
	buf.append_array(_varint(value))
	return buf


## Encodes an int64 field (wire type 0: varint).
## Skips the field if value == 0 (proto3 default-value omission).
static func _int64_field(field_num: int, value: int) -> PackedByteArray:
	if value == 0:
		return PackedByteArray()
	var buf := PackedByteArray()
	buf.append_array(_varint(_tag(field_num, _WT_VARINT)))
	buf.append_array(_varint(value))
	return buf


## Encodes a bool field (wire type 0: varint).
## Skips the field if value == false (proto3 default-value omission).
static func _bool_field(field_num: int, value: bool) -> PackedByteArray:
	if not value:
		return PackedByteArray()
	var buf := PackedByteArray()
	buf.append_array(_varint(_tag(field_num, _WT_VARINT)))
	buf.append(1)
	return buf
