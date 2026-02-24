## NetworkManagerTest.gd
## Unit tests for NetworkManager: frame parsing, opcode dispatch, and state machine.
##
## Strategy: We bypass the real TCP connection by directly manipulating the
## internal buffer (_recv_buf) and calling _parse_frames() / _dispatch_frame().
## GDScript has no access modifiers, so all vars/methods are accessible.
class_name NetworkManagerTest
extends GdUnitTestSuite

const NetworkManagerScript = preload("res://autoloads/NetworkManager.gd")

var _nm: Node  # NetworkManager instance


func before_test() -> void:
	_nm = auto_free(NetworkManagerScript.new())
	# Prevent _process from polling a real TCP socket
	_nm.set_process(false)


# ---- Helper: build a raw TCP frame (length + opcode + payload) ----

func _build_raw_frame(opcode: int, payload: PackedByteArray) -> PackedByteArray:
	return PacketProtocol.build_packet(opcode, payload)


# ---- Helper: build a protobuf varint field ----

func _varint_field(field_num: int, value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_encode_varint((field_num << 3) | 0))
	buf.append_array(_encode_varint(value))
	return buf


func _string_field(field_num: int, value: String) -> PackedByteArray:
	var payload := value.to_utf8_buffer()
	var buf := PackedByteArray()
	buf.append_array(_encode_varint((field_num << 3) | 2))
	buf.append_array(_encode_varint(payload.size()))
	buf.append_array(payload)
	return buf


func _encode_varint(value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	var v := value
	while v > 127:
		buf.append((v & 0x7F) | 0x80)
		v >>= 7
	buf.append(v & 0x7F)
	return buf


# ==== State Machine ====

func test_initial_state_is_idle() -> void:
	assert_bool(_nm.is_server_connected()).is_false()


func test_is_server_connected_only_when_connected() -> void:
	# IDLE
	assert_bool(_nm.is_server_connected()).is_false()
	# Simulate CONNECTED
	_nm._state = NetworkManagerScript._State.CONNECTED
	assert_bool(_nm.is_server_connected()).is_true()
	# Simulate CONNECTING
	_nm._state = NetworkManagerScript._State.CONNECTING
	assert_bool(_nm.is_server_connected()).is_false()
	# Simulate RECONNECTING
	_nm._state = NetworkManagerScript._State.RECONNECTING
	assert_bool(_nm.is_server_connected()).is_false()
	# Simulate FAILED
	_nm._state = NetworkManagerScript._State.FAILED
	assert_bool(_nm.is_server_connected()).is_false()


func test_disconnect_resets_state() -> void:
	_nm._state = NetworkManagerScript._State.CONNECTED
	_nm._recv_buf = PackedByteArray([0x01, 0x02, 0x03])
	_nm._frame_len = 42

	_nm.disconnect_from_server()

	assert_int(_nm._state).is_equal(NetworkManagerScript._State.IDLE)
	assert_array(_nm._recv_buf).is_empty()
	assert_int(_nm._frame_len).is_equal(-1)


# ==== Reconnect Logic ====

func test_on_connect_error_triggers_reconnect() -> void:
	_nm._reconnect_count = 0
	_nm._on_connect_error()
	# Should transition to RECONNECTING (not FAILED) since count < MAX
	assert_int(_nm._state).is_equal(NetworkManagerScript._State.RECONNECTING)
	assert_int(_nm._reconnect_count).is_equal(1)


func test_on_connect_error_exhausts_retries() -> void:
	_nm._reconnect_count = NetworkManagerScript.MAX_RECONNECT_ATTEMPTS - 1
	# Monitor the connection_failed signal
	monitor_signals(_nm)

	_nm._on_connect_error()

	assert_int(_nm._state).is_equal(NetworkManagerScript._State.FAILED)
	await assert_signal(_nm).is_emitted("connection_failed")


func test_on_connected_resets_reconnect_count() -> void:
	_nm._reconnect_count = 2
	monitor_signals(_nm)

	_nm._on_connected()

	assert_int(_nm._state).is_equal(NetworkManagerScript._State.CONNECTED)
	assert_int(_nm._reconnect_count).is_equal(0)
	await assert_signal(_nm).is_emitted("connected_to_server")


func test_on_disconnected_emits_signal() -> void:
	_nm._state = NetworkManagerScript._State.CONNECTED
	monitor_signals(_nm)

	_nm._on_disconnected()

	assert_int(_nm._state).is_equal(NetworkManagerScript._State.IDLE)
	await assert_signal(_nm).is_emitted("disconnected_from_server")


# ==== Frame Parsing (_parse_frames) ====

func test_parse_single_complete_frame() -> void:
	# Build a heartbeat frame (opcode 0x0601, empty payload is fine)
	var payload := PackedByteArray()
	payload.append_array(_varint_field(1, 5000))   # client_time
	payload.append_array(_varint_field(2, 6000))   # server_time
	var frame := _build_raw_frame(PacketProtocol.OPCODE_HEARTBEAT, payload)

	_nm._state = NetworkManagerScript._State.CONNECTED
	_nm._recv_buf = frame
	_nm._frame_len = -1

	# Heartbeat is silently consumed (no signal emitted), just verify no crash
	_nm._parse_frames()

	# Buffer should be fully consumed
	assert_array(_nm._recv_buf).is_empty()
	assert_int(_nm._frame_len).is_equal(-1)


func test_parse_incomplete_header() -> void:
	# Only 3 bytes of the 4-byte header
	_nm._recv_buf = PackedByteArray([0x00, 0x00, 0x00])
	_nm._frame_len = -1

	_nm._parse_frames()

	# Should leave buffer untouched, waiting for more data
	assert_int(_nm._recv_buf.size()).is_equal(3)
	assert_int(_nm._frame_len).is_equal(-1)


func test_parse_incomplete_payload() -> void:
	# Header says frame_len = 10, but we only provide 4 bytes of payload
	_nm._recv_buf = PackedByteArray([
		0x00, 0x00, 0x00, 0x0A,  # length = 10
		0x06, 0x01, 0xAA, 0xBB,  # opcode + 2 payload bytes (need 8 more)
	])
	_nm._frame_len = -1

	_nm._parse_frames()

	# Should have consumed the header but waiting for full frame
	assert_int(_nm._frame_len).is_equal(10)
	assert_int(_nm._recv_buf.size()).is_equal(4)  # opcode + partial payload


func test_parse_multiple_frames_in_one_buffer() -> void:
	# Build two complete frames back to back
	var payload1 := PackedByteArray()
	payload1.append_array(_varint_field(1, 1))   # success = true
	payload1.append_array(_string_field(2, ""))   # empty error
	var frame1 := _build_raw_frame(PacketProtocol.OPCODE_REGISTER_RESPONSE, payload1)

	var payload2 := PackedByteArray()
	payload2.append_array(_varint_field(1, 100))
	payload2.append_array(_varint_field(2, 200))
	var frame2 := _build_raw_frame(PacketProtocol.OPCODE_HEARTBEAT, payload2)

	_nm._state = NetworkManagerScript._State.CONNECTED
	_nm._recv_buf = PackedByteArray()
	_nm._recv_buf.append_array(frame1)
	_nm._recv_buf.append_array(frame2)
	_nm._frame_len = -1

	monitor_signals(_nm)
	_nm._parse_frames()

	# Both frames consumed
	assert_array(_nm._recv_buf).is_empty()
	assert_int(_nm._frame_len).is_equal(-1)
	# register_response signal should have been emitted for frame1
	await assert_signal(_nm).is_emitted("register_response")


func test_parse_invalid_frame_length_zero() -> void:
	# frame_len = 0 is invalid (< 2 minimum for opcode)
	_nm._state = NetworkManagerScript._State.CONNECTED
	_nm._recv_buf = PackedByteArray([0x00, 0x00, 0x00, 0x00])
	_nm._frame_len = -1

	monitor_signals(_nm)
	_nm._parse_frames()

	# Should disconnect due to invalid frame
	await assert_signal(_nm).is_emitted("disconnected_from_server")


func test_parse_oversized_frame_drops_connection() -> void:
	# frame_len > MAX_FRAME_BYTES (65535)
	_nm._state = NetworkManagerScript._State.CONNECTED
	_nm._recv_buf = PackedByteArray([0x00, 0x01, 0x00, 0x00])  # 65536
	_nm._frame_len = -1

	monitor_signals(_nm)
	_nm._parse_frames()

	await assert_signal(_nm).is_emitted("disconnected_from_server")


# ==== Opcode Dispatch (_dispatch_frame) ====

func test_dispatch_login_response() -> void:
	var payload := PackedByteArray()
	payload.append_array(_varint_field(1, 1))          # success
	payload.append_array(_string_field(2, "jwt.tok"))  # jwt

	# Frame = opcode(2 bytes) + payload
	var frame := PackedByteArray()
	frame.append(0x00)
	frame.append(0x02)  # OPCODE_LOGIN_RESPONSE
	frame.append_array(payload)

	monitor_signals(_nm)
	_nm._dispatch_frame(frame)

	await assert_signal(_nm).is_emitted("login_response")


func test_dispatch_register_response() -> void:
	var payload := PackedByteArray()
	payload.append_array(_varint_field(1, 1))  # success

	var frame := PackedByteArray()
	frame.append(0x00)
	frame.append(0x07)  # OPCODE_REGISTER_RESPONSE
	frame.append_array(payload)

	monitor_signals(_nm)
	_nm._dispatch_frame(frame)

	await assert_signal(_nm).is_emitted("register_response")


func test_dispatch_error_response() -> void:
	var payload := PackedByteArray()
	payload.append_array(_varint_field(1, 1))
	payload.append_array(_varint_field(2, 500))
	payload.append_array(_string_field(3, "Internal error"))

	var frame := PackedByteArray()
	frame.append(0x06)
	frame.append(0x03)  # OPCODE_ERROR_RESPONSE
	frame.append_array(payload)

	monitor_signals(_nm)
	_nm._dispatch_frame(frame)

	await assert_signal(_nm).is_emitted("error_response")


func test_dispatch_server_message() -> void:
	var payload := PackedByteArray()
	payload.append_array(_string_field(1, "info"))
	payload.append_array(_string_field(2, "Maintenance in 10 min"))

	var frame := PackedByteArray()
	frame.append(0x06)
	frame.append(0x02)  # OPCODE_SERVER_MESSAGE
	frame.append_array(payload)

	monitor_signals(_nm)
	_nm._dispatch_frame(frame)

	await assert_signal(_nm).is_emitted("server_message")


func test_dispatch_enter_world_response() -> void:
	var payload := PackedByteArray()
	payload.append_array(_varint_field(1, 1))  # success

	var frame := PackedByteArray()
	frame.append(0x00)
	frame.append(0x04)  # OPCODE_ENTER_WORLD
	frame.append_array(payload)

	monitor_signals(_nm)
	_nm._dispatch_frame(frame)

	await assert_signal(_nm).is_emitted("enter_world_response")


func test_dispatch_character_list_response() -> void:
	var frame := PackedByteArray()
	frame.append(0x00)
	frame.append(0x09)  # OPCODE_CHARACTER_LIST_RESPONSE
	# empty payload

	monitor_signals(_nm)
	_nm._dispatch_frame(frame)

	await assert_signal(_nm).is_emitted("character_list_response")


func test_dispatch_heartbeat_silent() -> void:
	var payload := PackedByteArray()
	payload.append_array(_varint_field(1, 1000))
	payload.append_array(_varint_field(2, 2000))

	var frame := PackedByteArray()
	frame.append(0x06)
	frame.append(0x01)  # OPCODE_HEARTBEAT
	frame.append_array(payload)

	# Heartbeat should NOT emit any of the data signals
	monitor_signals(_nm)
	_nm._dispatch_frame(frame)

	await assert_signal(_nm).is_not_emitted("login_response")
	await assert_signal(_nm).is_not_emitted("register_response")
	await assert_signal(_nm).is_not_emitted("error_response")


func test_dispatch_empty_frame_ignored() -> void:
	# Frame with less than 2 bytes should be silently ignored
	_nm._dispatch_frame(PackedByteArray([0x00]))
	_nm._dispatch_frame(PackedByteArray())
	# No crash = success


# ==== Send guard ====

func test_send_rejected_when_not_connected() -> void:
	_nm._state = NetworkManagerScript._State.IDLE
	# send_login internally calls _send() which checks state
	# This should not crash; it just logs a warning
	_nm.send_login("user", "pass")
	# No crash = success (the warning is expected)
