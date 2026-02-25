## NetworkManager.gd  (Autoload: "NetworkManager")
## Manages TCP connections to FlyAgain server services and UDP to the world-service.
##
## Connection lifecycle:
##   1. Login phase: TCP to login-service (7777)
##   2. Account phase: TCP to account-service (7779)
##   3. World phase: TCP to world-service (7780) + UDP to world-service (7781)
##
## Each phase replaces the previous connections.
## Heartbeat is sent automatically every HEARTBEAT_INTERVAL seconds on the active TCP.
extends Node

# ---- Signals (auth / account phase) ----

## Emitted when the TCP connection is established.
signal connected_to_server

## Emitted when the connection is lost unexpectedly.
signal disconnected_from_server

## Emitted after all reconnect attempts have been exhausted.
signal connection_failed(reason: String)

## Incoming packet signals — UI screens connect to these.
signal login_response(data: Dictionary)
signal register_response(data: Dictionary)
signal error_response(data: Dictionary)
signal server_message(data: Dictionary)
signal enter_world_response(data: Dictionary)
signal character_list_response(data: Dictionary)

# ---- Signals (world phase) ----

signal world_connected
signal world_disconnected
signal zone_data_received(data: Dictionary)
signal entity_spawned(data: Dictionary)
signal entity_despawned(data: Dictionary)
signal entity_position_updated(data: Dictionary)
signal position_corrected(data: Dictionary)


# ---- Configuration ----

const DEFAULT_HOST             := "127.0.0.1"
const DEFAULT_PORT             := 7777
const MAX_RECONNECT_ATTEMPTS   := 3
const RECONNECT_DELAY_SEC      := 2.0
const CONNECT_TIMEOUT_SEC      := 10.0
const HEARTBEAT_INTERVAL_SEC   := 5.0
const MAX_FRAME_BYTES          := 65535


# ---- Internal state machine ----

enum _State { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED }

# Auth/account TCP connection
var _state:           _State = _State.IDLE
var _tcp:             StreamPeerTCP = StreamPeerTCP.new()
var _target_host:     String = DEFAULT_HOST
var _target_port:     int    = DEFAULT_PORT
var _reconnect_count: int    = 0
var _connect_elapsed: float  = 0.0
var _heartbeat_elapsed: float = 0.0
var _reconnect_elapsed: float = 0.0
var _recv_buf:   PackedByteArray = PackedByteArray()
var _frame_len:  int = -1

# World-service TCP connection
var _world_state:       _State = _State.IDLE
var _world_tcp:         StreamPeerTCP = null
var _world_host:        String = ""
var _world_tcp_port:    int = 0
var _world_connect_elapsed: float = 0.0
var _world_heartbeat_elapsed: float = 0.0
var _world_recv_buf:    PackedByteArray = PackedByteArray()
var _world_frame_len:   int = -1

# World-service UDP connection
var _udp: UdpConnection = null


# ---- Godot lifecycle ----

func _ready() -> void:
	set_process(true)


func _process(delta: float) -> void:
	# Auth/account TCP polling
	match _state:
		_State.CONNECTING:
			_poll_connecting(delta)
		_State.CONNECTED:
			_poll_connected(delta)
		_State.RECONNECTING:
			_poll_reconnecting(delta)

	# World TCP polling
	match _world_state:
		_State.CONNECTING:
			_poll_world_connecting(delta)
		_State.CONNECTED:
			_poll_world_connected(delta)

	# UDP polling
	if _udp != null and _udp.is_connected_to_server():
		_udp.poll()


# ---- Public API (auth / account phase) ----

## Initiates a connection to the server.
## Signals `connected_to_server` on success or `connection_failed` on exhausted retries.
func connect_to_server(host: String = DEFAULT_HOST, port: int = DEFAULT_PORT) -> void:
	if _state == _State.CONNECTED:
		connected_to_server.emit()
		return
	if _state == _State.CONNECTING or _state == _State.RECONNECTING:
		return
	_target_host     = host
	_target_port     = port
	_reconnect_count = 0
	_start_connect()


## Gracefully closes the auth/account TCP connection.
func disconnect_from_server() -> void:
	_state = _State.IDLE
	_tcp.disconnect_from_host()
	_recv_buf.clear()
	_frame_len = -1


## Returns true when the auth/account TCP socket is fully connected.
func is_server_connected() -> bool:
	return _state == _State.CONNECTED


func send_login(username: String, password: String) -> void:
	_send(PacketProtocol.OPCODE_LOGIN_REQUEST,
		ProtoEncoder.encode_login_request(username, password))


func send_register(username: String, email: String, password: String) -> void:
	_send(PacketProtocol.OPCODE_REGISTER_REQUEST,
		ProtoEncoder.encode_register_request(username, email, password))


func send_character_select(character_id: String) -> void:
	_send(PacketProtocol.OPCODE_CHARACTER_SELECT,
		ProtoEncoder.encode_character_select(character_id, GameState.jwt))


func send_character_create(char_name: String, character_class: String) -> void:
	_send(PacketProtocol.OPCODE_CHARACTER_CREATE,
		ProtoEncoder.encode_character_create(char_name, character_class, GameState.jwt))


func send_character_list_request() -> void:
	_send(PacketProtocol.OPCODE_CHARACTER_LIST_REQUEST,
		ProtoEncoder.encode_character_list_request(GameState.jwt))


# ---- Public API (world phase) ----

## Connects to the world-service (TCP + UDP).
## Disconnects from the current auth/account TCP first.
func connect_to_world(host: String, tcp_port: int, udp_port: int, session_token: int) -> void:
	# Close account-service connection
	disconnect_from_server()

	# Set up world TCP
	_world_host = host
	_world_tcp_port = tcp_port
	_world_tcp = StreamPeerTCP.new()
	_world_recv_buf.clear()
	_world_frame_len = -1
	_world_connect_elapsed = 0.0
	_world_heartbeat_elapsed = 0.0
	_world_state = _State.CONNECTING
	var err := _world_tcp.connect_to_host(host, tcp_port)
	if err != OK:
		push_error("NetworkManager: world TCP connect failed: %s" % error_string(err))
		_world_state = _State.FAILED
		world_disconnected.emit()
		return

	# Set up UDP and connect incoming packet signal
	_udp = UdpConnection.new()
	_udp.packet_received.connect(_on_udp_packet_received)
	var udp_err := _udp.connect_to_server(host, udp_port, session_token, GameState.hmac_secret)
	if udp_err != OK:
		push_error("NetworkManager: world UDP connect failed: %s" % error_string(udp_err))


## Disconnects from the world-service (TCP + UDP).
func disconnect_from_world() -> void:
	_world_state = _State.IDLE
	if _world_tcp != null:
		_world_tcp.disconnect_from_host()
	_world_recv_buf.clear()
	_world_frame_len = -1
	if _udp != null:
		if _udp.packet_received.is_connected(_on_udp_packet_received):
			_udp.packet_received.disconnect(_on_udp_packet_received)
		_udp.disconnect_from_server()
		_udp = null


## Returns true when the world-service TCP is connected.
func is_world_connected() -> bool:
	return _world_state == _State.CONNECTED


## Sends an EnterWorldRequest over world TCP.
func send_enter_world(jwt: String, character_id: String, session_id: String) -> void:
	_send_world(PacketProtocol.OPCODE_ENTER_WORLD,
		ProtoEncoder.encode_enter_world_request(jwt, character_id, session_id))


## Sends a logout request to the world-service, then disconnects all connections.
func send_logout() -> void:
	if _world_state == _State.CONNECTED:
		_send_world(PacketProtocol.OPCODE_LOGOUT_REQUEST,
			ProtoEncoder.encode_logout_request(GameState.session_id))
	disconnect_from_world()
	disconnect_from_server()


## Sends a MovementInput over UDP.
func send_movement_input(
	position: Vector3, rotation: float,
	dx: float, dy: float, dz: float,
	is_moving: bool, is_flying: bool, sequence: int
) -> void:
	if _udp == null or not _udp.is_connected_to_server():
		return
	_udp.send_packet(PacketProtocol.OPCODE_MOVEMENT_INPUT,
		ProtoEncoder.encode_movement_input(position, rotation, dx, dy, dz,
			is_moving, is_flying, sequence))


# ---- Auth/account TCP connection handling ----

func _start_connect() -> void:
	_tcp              = StreamPeerTCP.new()
	_recv_buf.clear()
	_frame_len        = -1
	_connect_elapsed  = 0.0
	_state            = _State.CONNECTING
	var err := _tcp.connect_to_host(_target_host, _target_port)
	if err != OK:
		push_warning("NetworkManager: connect_to_host() returned %s" % error_string(err))
		_on_connect_error()


func _poll_connecting(delta: float) -> void:
	_tcp.poll()
	_connect_elapsed += delta
	var status := _tcp.get_status()
	if status == StreamPeerTCP.STATUS_CONNECTED:
		_on_connected()
	elif status == StreamPeerTCP.STATUS_ERROR or status == StreamPeerTCP.STATUS_NONE:
		_on_connect_error()
	elif _connect_elapsed >= CONNECT_TIMEOUT_SEC:
		_tcp.disconnect_from_host()
		_on_connect_error()


func _poll_connected(delta: float) -> void:
	_tcp.poll()
	var status := _tcp.get_status()
	if status != StreamPeerTCP.STATUS_CONNECTED:
		_on_disconnected()
		return
	_drain_and_parse()
	_heartbeat_elapsed += delta
	if _heartbeat_elapsed >= HEARTBEAT_INTERVAL_SEC:
		_heartbeat_elapsed = 0.0
		_send_heartbeat()


func _poll_reconnecting(delta: float) -> void:
	_reconnect_elapsed += delta
	if _reconnect_elapsed >= RECONNECT_DELAY_SEC:
		_reconnect_elapsed = 0.0
		_start_connect()


func _on_connected() -> void:
	_state             = _State.CONNECTED
	_heartbeat_elapsed = 0.0
	_reconnect_count   = 0
	push_print("NetworkManager: connected to %s:%d" % [_target_host, _target_port])
	connected_to_server.emit()


func _on_connect_error() -> void:
	_reconnect_count += 1
	if _reconnect_count < MAX_RECONNECT_ATTEMPTS:
		push_warning("NetworkManager: connect failed, retry %d/%d in %.1fs" % [
			_reconnect_count, MAX_RECONNECT_ATTEMPTS, RECONNECT_DELAY_SEC])
		_state             = _State.RECONNECTING
		_reconnect_elapsed = 0.0
	else:
		_state = _State.FAILED
		push_error("NetworkManager: all reconnect attempts exhausted")
		connection_failed.emit("Verbindung zum Server fehlgeschlagen (%s:%d)" % [_target_host, _target_port])


func _on_disconnected() -> void:
	_state = _State.IDLE
	push_warning("NetworkManager: connection lost")
	disconnected_from_server.emit()


# ---- World TCP connection handling ----

func _poll_world_connecting(delta: float) -> void:
	_world_tcp.poll()
	_world_connect_elapsed += delta
	var status := _world_tcp.get_status()
	if status == StreamPeerTCP.STATUS_CONNECTED:
		_on_world_connected()
	elif status == StreamPeerTCP.STATUS_ERROR or status == StreamPeerTCP.STATUS_NONE:
		_on_world_connect_failed()
	elif _world_connect_elapsed >= CONNECT_TIMEOUT_SEC:
		_world_tcp.disconnect_from_host()
		_on_world_connect_failed()


func _poll_world_connected(delta: float) -> void:
	_world_tcp.poll()
	var status := _world_tcp.get_status()
	if status != StreamPeerTCP.STATUS_CONNECTED:
		_on_world_disconnected()
		return
	_drain_and_parse_world()
	_world_heartbeat_elapsed += delta
	if _world_heartbeat_elapsed >= HEARTBEAT_INTERVAL_SEC:
		_world_heartbeat_elapsed = 0.0
		_send_world_heartbeat()


func _on_world_connected() -> void:
	_world_state = _State.CONNECTED
	_world_heartbeat_elapsed = 0.0
	push_print("NetworkManager: world connected to %s:%d" % [_world_host, _world_tcp_port])
	world_connected.emit()


func _on_world_connect_failed() -> void:
	_world_state = _State.FAILED
	push_error("NetworkManager: world TCP connection failed")
	world_disconnected.emit()


func _on_world_disconnected() -> void:
	_world_state = _State.IDLE
	push_warning("NetworkManager: world connection lost")
	disconnect_from_world()
	world_disconnected.emit()


# ---- Packet I/O (auth/account TCP) ----

func _drain_and_parse() -> void:
	var available := _tcp.get_available_bytes()
	if available > 0:
		var res := _tcp.get_data(available)
		if res[0] == OK:
			_recv_buf.append_array(res[1] as PackedByteArray)
		else:
			push_error("NetworkManager: recv error %s" % error_string(res[0]))
			_on_disconnected()
			return
	_parse_frames()


func _parse_frames() -> void:
	while true:
		if _frame_len == -1:
			if _recv_buf.size() < 4:
				return
			_frame_len = (_recv_buf[0] << 24) | (_recv_buf[1] << 16) \
					   | (_recv_buf[2] << 8)  |  _recv_buf[3]
			_recv_buf  = _recv_buf.slice(4)
			if _frame_len > MAX_FRAME_BYTES or _frame_len < 2:
				push_error("NetworkManager: invalid frame length %d — dropping connection" % _frame_len)
				_on_disconnected()
				return
		if _recv_buf.size() < _frame_len:
			return
		var frame     := _recv_buf.slice(0, _frame_len)
		_recv_buf      = _recv_buf.slice(_frame_len)
		_frame_len     = -1
		_dispatch_frame(frame)


func _dispatch_frame(frame: PackedByteArray) -> void:
	if frame.size() < 2:
		return
	var opcode  := (frame[0] << 8) | frame[1]
	var payload := frame.slice(2) if frame.size() > 2 else PackedByteArray()

	match opcode:
		PacketProtocol.OPCODE_LOGIN_RESPONSE:
			login_response.emit(ProtoDecoder.new(payload).decode_login_response())
		PacketProtocol.OPCODE_REGISTER_RESPONSE:
			register_response.emit(ProtoDecoder.new(payload).decode_register_response())
		PacketProtocol.OPCODE_ERROR_RESPONSE:
			error_response.emit(ProtoDecoder.new(payload).decode_error_response())
		PacketProtocol.OPCODE_SERVER_MESSAGE:
			server_message.emit(ProtoDecoder.new(payload).decode_server_message())
		PacketProtocol.OPCODE_ENTER_WORLD:
			enter_world_response.emit(ProtoDecoder.new(payload).decode_enter_world_response())
		PacketProtocol.OPCODE_CHARACTER_LIST_RESPONSE:
			character_list_response.emit(ProtoDecoder.new(payload).decode_character_list_response())
		PacketProtocol.OPCODE_HEARTBEAT:
			pass  # Server heartbeat echo — no action needed
		_:
			push_warning("NetworkManager: unhandled opcode %s" % PacketProtocol.opcode_name(opcode))


func _send(opcode: int, payload: PackedByteArray) -> void:
	if _state != _State.CONNECTED:
		push_warning("NetworkManager: cannot send %s — not connected" % PacketProtocol.opcode_name(opcode))
		return
	var err := _tcp.put_data(PacketProtocol.build_packet(opcode, payload))
	if err != OK:
		push_error("NetworkManager: send error %s" % error_string(err))
		_on_disconnected()


func _send_heartbeat() -> void:
	var client_time_ms := int(Time.get_unix_time_from_system() * 1000)
	_send(PacketProtocol.OPCODE_HEARTBEAT,
		ProtoEncoder.encode_heartbeat(client_time_ms))


# ---- Packet I/O (world TCP) ----

func _drain_and_parse_world() -> void:
	var available := _world_tcp.get_available_bytes()
	if available > 0:
		var res := _world_tcp.get_data(available)
		if res[0] == OK:
			_world_recv_buf.append_array(res[1] as PackedByteArray)
		else:
			push_error("NetworkManager: world recv error %s" % error_string(res[0]))
			_on_world_disconnected()
			return
	_parse_world_frames()


func _parse_world_frames() -> void:
	while true:
		if _world_frame_len == -1:
			if _world_recv_buf.size() < 4:
				return
			_world_frame_len = (_world_recv_buf[0] << 24) | (_world_recv_buf[1] << 16) \
							 | (_world_recv_buf[2] << 8)  |  _world_recv_buf[3]
			_world_recv_buf  = _world_recv_buf.slice(4)
			if _world_frame_len > MAX_FRAME_BYTES or _world_frame_len < 2:
				push_error("NetworkManager: invalid world frame length %d" % _world_frame_len)
				_on_world_disconnected()
				return
		if _world_recv_buf.size() < _world_frame_len:
			return
		var frame     := _world_recv_buf.slice(0, _world_frame_len)
		_world_recv_buf = _world_recv_buf.slice(_world_frame_len)
		_world_frame_len = -1
		_dispatch_world_frame(frame)


func _dispatch_world_frame(frame: PackedByteArray) -> void:
	if frame.size() < 2:
		return
	var opcode  := (frame[0] << 8) | frame[1]
	var payload := frame.slice(2) if frame.size() > 2 else PackedByteArray()

	match opcode:
		PacketProtocol.OPCODE_ZONE_DATA:
			var data := ProtoDecoder.new(payload).decode_zone_data()
			print("[NET] ZONE_DATA: zone=%d channel=%d my_entity_id=%d entities=%d" % [
				data.get("zone_id", 0), data.get("channel_id", 0),
				data.get("my_entity_id", 0), data.get("entities", []).size()])
			zone_data_received.emit(data)
		PacketProtocol.OPCODE_ENTER_WORLD:
			# EnterWorldHandler sends this on error (success path sends ZONE_DATA instead)
			var data := ProtoDecoder.new(payload).decode_enter_world_response()
			if not data.get("success", false):
				push_error("NetworkManager: world entry rejected: %s" % data.get("error_message", ""))
				error_response.emit({
					"original_opcode": PacketProtocol.OPCODE_ENTER_WORLD,
					"error_code": 403,
					"message": data.get("error_message", "World entry failed.")
				})
		PacketProtocol.OPCODE_ENTITY_SPAWN:
			var data := ProtoDecoder.new(payload).decode_entity_spawn()
			print("[NET] ENTITY_SPAWN: id=%d type=%d name=%s pos=%s" % [
				data.get("entity_id", 0), data.get("entity_type", 0),
				data.get("name", ""), data.get("position", {})])
			entity_spawned.emit(data)
		PacketProtocol.OPCODE_ENTITY_DESPAWN:
			var data := ProtoDecoder.new(payload).decode_entity_despawn()
			print("[NET] ENTITY_DESPAWN: id=%d" % data.get("entity_id", 0))
			entity_despawned.emit(data)
		PacketProtocol.OPCODE_ENTITY_POSITION:
			var data := ProtoDecoder.new(payload).decode_entity_position_update()
			entity_position_updated.emit(data)
		PacketProtocol.OPCODE_POSITION_CORRECTION:
			position_corrected.emit(ProtoDecoder.new(payload).decode_position_correction())
		PacketProtocol.OPCODE_ERROR_RESPONSE:
			var data := ProtoDecoder.new(payload).decode_error_response()
			print("[NET] ERROR: opcode=%d code=%d msg=%s" % [
				data.get("original_opcode", 0), data.get("error_code", 0), data.get("message", "")])
			error_response.emit(data)
		PacketProtocol.OPCODE_SERVER_MESSAGE:
			server_message.emit(ProtoDecoder.new(payload).decode_server_message())
		PacketProtocol.OPCODE_HEARTBEAT:
			pass
		_:
			push_warning("NetworkManager: unhandled world opcode %s" % PacketProtocol.opcode_name(opcode))


func _send_world(opcode: int, payload: PackedByteArray) -> void:
	if _world_state != _State.CONNECTED:
		push_warning("NetworkManager: cannot send world %s — not connected" % PacketProtocol.opcode_name(opcode))
		return
	var err := _world_tcp.put_data(PacketProtocol.build_packet(opcode, payload))
	if err != OK:
		push_error("NetworkManager: world send error %s" % error_string(err))
		_on_world_disconnected()


func _send_world_heartbeat() -> void:
	var client_time_ms := int(Time.get_unix_time_from_system() * 1000)
	_send_world(PacketProtocol.OPCODE_HEARTBEAT,
		ProtoEncoder.encode_heartbeat(client_time_ms))


# ---- UDP incoming packet handling ----

func _on_udp_packet_received(opcode: int, payload: PackedByteArray) -> void:
	match opcode:
		PacketProtocol.OPCODE_ENTITY_POSITION:
			entity_position_updated.emit(
				ProtoDecoder.new(payload).decode_entity_position_update())
		PacketProtocol.OPCODE_POSITION_CORRECTION:
			position_corrected.emit(
				ProtoDecoder.new(payload).decode_position_correction())
		_:
			push_warning("NetworkManager: unhandled UDP opcode %s" % PacketProtocol.opcode_name(opcode))


# ---- Utility ----

## Wraps push_print to only output in debug builds.
func push_print(msg: String) -> void:
	if OS.is_debug_build():
		print(msg)
