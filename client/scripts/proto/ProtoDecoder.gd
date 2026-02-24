## ProtoDecoder.gd
## Manual Protocol Buffer 3 decoder for FlyAgain server messages.
## Create an instance per payload; call the appropriate decode_*() method.
##
## Wire types: 0=Varint  1=64-bit  2=LenDelimited  5=32-bit
class_name ProtoDecoder
extends RefCounted

var _data: PackedByteArray
var _pos:  int = 0


func _init(data: PackedByteArray) -> void:
	_data = data
	_pos  = 0


# ---- Public decode methods ----

## Decodes a LoginResponse message.
## Returns a Dictionary with keys:
##   success (bool), jwt (String), characters (Array[Dict]),
##   error_message (String), hmac_secret (String),
##   account_service_host (String), account_service_port (int)
func decode_login_response() -> Dictionary:
	var result := {
		"success":              false,
		"jwt":                  "",
		"characters":           [],
		"error_message":        "",
		"hmac_secret":          "",
		"account_service_host": "",
		"account_service_port": 0,
	}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["success"]              = _read_varint() != 0
			2: result["jwt"]                  = _read_string()
			3:
				var sub := ProtoDecoder.new(_read_bytes_ld())
				result["characters"].append(sub.decode_character_info())
			4: result["error_message"]        = _read_string()
			5: result["hmac_secret"]          = _read_string()
			6: result["account_service_host"] = _read_string()
			7: result["account_service_port"] = _read_varint()
			_: _skip(wt)
	return result


## Decodes a CharacterInfo sub-message.
## Returns a Dictionary with keys: id (String), name (String), character_class (String), level (int)
func decode_character_info() -> Dictionary:
	var result := {"id": "", "name": "", "character_class": "", "level": 0}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["id"]              = _read_string()
			2: result["name"]            = _read_string()
			3: result["character_class"] = _read_string()
			4: result["level"]           = _read_varint()
			_: _skip(wt)
	return result


## Decodes a RegisterResponse message.
## Returns: { success (bool), error_message (String) }
func decode_register_response() -> Dictionary:
	var result := {"success": false, "error_message": ""}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["success"]       = _read_varint() != 0
			2: result["error_message"] = _read_string()
			_: _skip(wt)
	return result


## Decodes an ErrorResponse message.
## Returns: { original_opcode (int), error_code (int), message (String) }
func decode_error_response() -> Dictionary:
	var result := {"original_opcode": 0, "error_code": 0, "message": ""}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["original_opcode"] = _read_varint()
			2: result["error_code"]      = _read_varint()
			3: result["message"]         = _read_string()
			_: _skip(wt)
	return result


## Decodes a Heartbeat message.
## Returns: { client_time (int), server_time (int) }
func decode_heartbeat() -> Dictionary:
	var result := {"client_time": 0, "server_time": 0}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["client_time"]  = _read_varint()
			2: result["server_time"]  = _read_varint()
			_: _skip(wt)
	return result


## Decodes an EnterWorldResponse message.
## Returns: { success (bool), error_message (String), world_service_host (String),
##            world_service_tcp_port (int), world_service_udp_port (int) }
## Used as the response for both CHARACTER_CREATE and CHARACTER_SELECT.
func decode_enter_world_response() -> Dictionary:
	var result := {
		"success":              false,
		"error_message":        "",
		"world_service_host":   "",
		"world_service_tcp_port": 0,
		"world_service_udp_port": 0,
	}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["success"]                = _read_varint() != 0
			4: result["error_message"]          = _read_string()
			5: result["world_service_host"]     = _read_string()
			6: result["world_service_tcp_port"] = _read_varint()
			7: result["world_service_udp_port"] = _read_varint()
			_: _skip(wt)
	return result


## Decodes a CharacterListResponse { characters=1 (repeated CharacterInfo) }.
## Returns: { characters: Array[Dict] }
func decode_character_list_response() -> Dictionary:
	var result := {"characters": []}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1:
				var sub := ProtoDecoder.new(_read_bytes_ld())
				result["characters"].append(sub.decode_character_info())
			_: _skip(wt)
	return result


## Decodes a ServerMessage { type, text }.
func decode_server_message() -> Dictionary:
	var result := {"type": "", "text": ""}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["type"] = _read_string()
			2: result["text"] = _read_string()
			_: _skip(wt)
	return result


# ---- Private helpers ----

func _has_bytes() -> bool:
	return _pos < _data.size()


func _read_byte() -> int:
	if _pos >= _data.size():
		return 0
	var b  := _data[_pos]
	_pos   += 1
	return b


## Reads a varint (unsigned, up to 64-bit) from the current position.
func _read_varint() -> int:
	var result := 0
	var shift  := 0
	while true:
		var b := _read_byte()
		result |= (b & 0x7F) << shift
		shift  += 7
		if (b & 0x80) == 0:
			break
		if shift >= 64:
			push_error("ProtoDecoder: varint overflow")
			break
	return result


## Reads the next field tag and returns [field_number, wire_type].
## Returns an empty array when no more data is available.
func _next_tag() -> Array:
	if not _has_bytes():
		return []
	var tag := _read_varint()
	return [tag >> 3, tag & 0x07]


## Reads a length-delimited byte sequence.
func _read_bytes_ld() -> PackedByteArray:
	var length := _read_varint()
	if _pos + length > _data.size():
		push_error("ProtoDecoder: LD field out of bounds (pos=%d len=%d size=%d)" % [_pos, length, _data.size()])
		return PackedByteArray()
	var result := _data.slice(_pos, _pos + length)
	_pos += length
	return result


## Reads a length-delimited UTF-8 string.
func _read_string() -> String:
	return _read_bytes_ld().get_string_from_utf8()


## Skips over an unknown field based on its wire type.
func _skip(wire_type: int) -> void:
	match wire_type:
		0: _read_varint()           # Varint — consume variable bytes
		1: _pos += 8                 # 64-bit fixed
		2: _pos += _read_varint()   # Length-delimited
		5: _pos += 4                 # 32-bit fixed
		_: push_error("ProtoDecoder: unknown wire type %d" % wire_type)
