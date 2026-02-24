## NetworkManager.gd  (Autoload: "NetworkManager")
## Manages the TCP connection to the FlyAgain server.
##
## Connection lifecycle:
##   1. Call connect_to_server(host, port)
##   2. Await `connected_to_server` or `connection_failed` signal
##   3. Use send_*() methods to transmit packets
##   4. React to incoming packet signals
##
## Heartbeat is sent automatically every HEARTBEAT_INTERVAL seconds.
## On disconnect, up to MAX_RECONNECT_ATTEMPTS are made with a 2 s delay.
extends Node

# ---- Signals ----

## Emitted when the TCP connection is established.
signal connected_to_server

## Emitted when the connection is lost unexpectedly.
signal disconnected_from_server

## Emitted after all reconnect attempts have been exhausted.
## @param reason  Human-readable failure reason.
signal connection_failed(reason: String)

## Incoming packet signals — UI screens connect to these.
signal login_response(data: Dictionary)
signal register_response(data: Dictionary)
signal error_response(data: Dictionary)
signal server_message(data: Dictionary)
signal enter_world_response(data: Dictionary)
signal character_list_response(data: Dictionary)


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

var _state:           _State = _State.IDLE
var _tcp:             StreamPeerTCP = StreamPeerTCP.new()
var _target_host:     String = DEFAULT_HOST
var _target_port:     int    = DEFAULT_PORT
var _reconnect_count: int    = 0
var _connect_elapsed: float  = 0.0
var _heartbeat_elapsed: float = 0.0
var _reconnect_elapsed: float = 0.0

# Packet reassembly
var _recv_buf:   PackedByteArray = PackedByteArray()
var _frame_len:  int = -1  # -1 = awaiting the 4-byte length header


# ---- Godot lifecycle ----

func _ready() -> void:
	set_process(true)


func _process(delta: float) -> void:
	match _state:
		_State.CONNECTING:
			_poll_connecting(delta)
		_State.CONNECTED:
			_poll_connected(delta)
		_State.RECONNECTING:
			_poll_reconnecting(delta)


# ---- Public API ----

## Initiates a connection to the server.
## Signals `connected_to_server` on success or `connection_failed` on exhausted retries.
## Safe to call when already connected or connecting; emits the appropriate signal.
func connect_to_server(host: String = DEFAULT_HOST, port: int = DEFAULT_PORT) -> void:
	if _state == _State.CONNECTED:
		# Already connected — callers awaiting this signal can proceed immediately.
		connected_to_server.emit()
		return
	if _state == _State.CONNECTING or _state == _State.RECONNECTING:
		# A connection attempt is already in flight — callers should just await the signal.
		return
	_target_host     = host
	_target_port     = port
	_reconnect_count = 0
	_start_connect()


## Gracefully closes the TCP connection.
func disconnect_from_server() -> void:
	_state = _State.IDLE
	_tcp.disconnect_from_host()
	_recv_buf.clear()
	_frame_len = -1


## Returns true when the TCP socket is fully connected.
func is_server_connected() -> bool:
	return _state == _State.CONNECTED


## Sends a LoginRequest to the login-service.
func send_login(username: String, password: String) -> void:
	_send(PacketProtocol.OPCODE_LOGIN_REQUEST,
		ProtoEncoder.encode_login_request(username, password))


## Sends a RegisterRequest to the login-service.
func send_register(username: String, email: String, password: String) -> void:
	_send(PacketProtocol.OPCODE_REGISTER_REQUEST,
		ProtoEncoder.encode_register_request(username, email, password))


## Sends a CharacterSelectRequest to the account-service.
func send_character_select(character_id: String) -> void:
	_send(PacketProtocol.OPCODE_CHARACTER_SELECT,
		ProtoEncoder.encode_character_select(character_id, GameState.jwt))


## Sends a CharacterCreateRequest to the account-service.
func send_character_create(char_name: String, character_class: String) -> void:
	_send(PacketProtocol.OPCODE_CHARACTER_CREATE,
		ProtoEncoder.encode_character_create(char_name, character_class, GameState.jwt))


## Sends a CharacterListRequest to the account-service.
func send_character_list_request() -> void:
	_send(PacketProtocol.OPCODE_CHARACTER_LIST_REQUEST,
		ProtoEncoder.encode_character_list_request(GameState.jwt))


# ---- Connection state handlers ----

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

	# Check socket health first
	var status := _tcp.get_status()
	if status != StreamPeerTCP.STATUS_CONNECTED:
		_on_disconnected()
		return

	# Drain incoming bytes
	_drain_and_parse()

	# Heartbeat
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


# ---- Packet I/O ----

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
		# Step 1: read the 4-byte length header
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

		# Step 2: wait for the full frame
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


# ---- Utility ----

## Wraps push_print to only output in debug builds.
func push_print(msg: String) -> void:
	if OS.is_debug_build():
		print(msg)
