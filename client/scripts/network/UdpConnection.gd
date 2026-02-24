## UdpConnection.gd
## Handles the UDP socket connection to the world-service.
## Signs all outgoing packets with HMAC-SHA256 matching the server's UdpServer.kt wire format:
##   [8B session_token BE][4B sequence BE][2B opcode BE][payload][32B HMAC-SHA256]
class_name UdpConnection
extends RefCounted


signal packet_received(opcode: int, payload: PackedByteArray)


# ---- Constants ----

const SESSION_TOKEN_LEN := 8
const SEQUENCE_LEN := 4
const OPCODE_LEN := 2
const HMAC_LEN := 32
const MIN_PACKET_SIZE := SESSION_TOKEN_LEN + SEQUENCE_LEN + OPCODE_LEN + HMAC_LEN  # 46 bytes
const MAX_UDP_PACKET_SIZE := 512


# ---- Internal state ----

var _udp: PacketPeerUDP = null
var _session_token: int = 0      # 8-byte signed long (GDScript int is 64-bit)
var _hmac_secret: PackedByteArray = PackedByteArray()
var _sequence: int = 0           # monotonically increasing uint32
var _connected: bool = false


# ---- Public API ----

## Connects to the UDP server.
## session_token: 8-byte session token from the server.
## hmac_secret_hex: Hex-encoded HMAC secret string from LoginResponse.
func connect_to_server(host: String, port: int, session_token: int, hmac_secret_str: String) -> Error:
	_session_token = session_token
	_hmac_secret = hmac_secret_str.to_utf8_buffer()
	_sequence = 0

	# PacketPeerUDP requires an IP address — resolve hostnames first
	var resolved_host := _resolve_host(host)

	_udp = PacketPeerUDP.new()
	var err := _udp.connect_to_host(resolved_host, port)
	if err == OK:
		_connected = true
	else:
		push_error("UdpConnection: connect failed: %s" % error_string(err))
	return err


## Closes the UDP connection.
func disconnect_from_server() -> void:
	if _udp != null:
		_udp.close()
	_connected = false
	_sequence = 0


## Returns true if the UDP socket is connected.
func is_connected_to_server() -> bool:
	return _connected


## Sends a UDP packet with HMAC-SHA256 authentication.
func send_packet(opcode: int, payload: PackedByteArray) -> void:
	if not _connected or _udp == null:
		return

	_sequence += 1

	# Build the data to sign: [session_token][sequence][opcode][payload]
	var data := PackedByteArray()
	data.append_array(_encode_int64_be(_session_token))
	data.append_array(_encode_uint32_be(_sequence))
	data.append_array(_encode_uint16_be(opcode))
	data.append_array(payload)

	# Compute HMAC-SHA256 over the unsigned data
	var hmac := _compute_hmac_sha256(data)

	# Append HMAC to form the final packet
	data.append_array(hmac)

	if data.size() > MAX_UDP_PACKET_SIZE:
		push_error("UdpConnection: packet too large (%d > %d)" % [data.size(), MAX_UDP_PACKET_SIZE])
		return

	_udp.put_packet(data)


## Polls for incoming UDP packets. Call from _process().
func poll() -> void:
	if _udp == null or not _connected:
		return
	while _udp.get_available_packet_count() > 0:
		var packet := _udp.get_packet()
		_handle_incoming(packet)


## Returns the current sequence number.
func get_sequence() -> int:
	return _sequence


# ---- Private: incoming packet handling ----

func _handle_incoming(packet: PackedByteArray) -> void:
	if packet.size() < MIN_PACKET_SIZE:
		return

	# Extract opcode (bytes 12-13, after session_token + sequence)
	var opcode_offset := SESSION_TOKEN_LEN + SEQUENCE_LEN
	var opcode := (packet[opcode_offset] << 8) | packet[opcode_offset + 1]

	# Extract payload (between opcode and HMAC)
	var payload_start := opcode_offset + OPCODE_LEN
	var payload_end := packet.size() - HMAC_LEN
	var payload := packet.slice(payload_start, payload_end) if payload_end > payload_start else PackedByteArray()

	packet_received.emit(opcode, payload)


# ---- Private: byte encoding helpers ----

## Encodes a 64-bit integer as 8 bytes big-endian.
static func _encode_int64_be(value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.resize(8)
	buf[0] = (value >> 56) & 0xFF
	buf[1] = (value >> 48) & 0xFF
	buf[2] = (value >> 40) & 0xFF
	buf[3] = (value >> 32) & 0xFF
	buf[4] = (value >> 24) & 0xFF
	buf[5] = (value >> 16) & 0xFF
	buf[6] = (value >> 8)  & 0xFF
	buf[7] = value          & 0xFF
	return buf


## Encodes a 32-bit unsigned integer as 4 bytes big-endian.
static func _encode_uint32_be(value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.resize(4)
	buf[0] = (value >> 24) & 0xFF
	buf[1] = (value >> 16) & 0xFF
	buf[2] = (value >> 8)  & 0xFF
	buf[3] = value          & 0xFF
	return buf


## Encodes a 16-bit unsigned integer as 2 bytes big-endian.
static func _encode_uint16_be(value: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.resize(2)
	buf[0] = (value >> 8) & 0xFF
	buf[1] = value         & 0xFF
	return buf


## Resolves a hostname to an IP address. PacketPeerUDP requires a raw IP.
static func _resolve_host(host: String) -> String:
	if host == "localhost":
		return "127.0.0.1"
	# If it already looks like an IP address, return as-is
	if host.is_valid_ip_address():
		return host
	# Use Godot's IP resolver for other hostnames
	var resolved := IP.resolve_hostname(host, IP.TYPE_IPV4)
	if resolved.is_valid_ip_address():
		return resolved
	push_error("UdpConnection: failed to resolve hostname '%s'" % host)
	return host


## Computes HMAC-SHA256 of the given data using the session HMAC secret.
func _compute_hmac_sha256(data: PackedByteArray) -> PackedByteArray:
	var ctx := HMACContext.new()
	var err := ctx.start(HashingContext.HASH_SHA256, _hmac_secret)
	if err != OK:
		push_error("UdpConnection: HMAC start failed: %s" % error_string(err))
		return PackedByteArray()
	err = ctx.update(data)
	if err != OK:
		push_error("UdpConnection: HMAC update failed: %s" % error_string(err))
		return PackedByteArray()
	return ctx.finish()
