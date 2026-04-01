## UdpConnectionTest.gd
## Tests for UdpConnection: byte encoding, packet framing, HMAC, sequence.
## These tests focus on the static encoding helpers and protocol logic,
## NOT actual socket I/O (which requires a running server).
class_name UdpConnectionTest
extends GdUnitTestSuite


# ---- Big-endian encoding: int64 ----

func test_encode_int64_be_zero() -> void:
	var buf := UdpConnection._encode_int64_be(0)
	assert_int(buf.size()).is_equal(8)
	for i in range(8):
		assert_int(buf[i]).is_equal(0)


func test_encode_int64_be_positive() -> void:
	# Value 1 → last byte = 0x01
	var buf := UdpConnection._encode_int64_be(1)
	assert_int(buf.size()).is_equal(8)
	assert_int(buf[7]).is_equal(1)
	for i in range(7):
		assert_int(buf[i]).is_equal(0)


func test_encode_int64_be_large_value() -> void:
	# 0x0102030405060708
	var val: int = 0x0102030405060708
	var buf := UdpConnection._encode_int64_be(val)
	assert_int(buf[0]).is_equal(0x01)
	assert_int(buf[1]).is_equal(0x02)
	assert_int(buf[2]).is_equal(0x03)
	assert_int(buf[3]).is_equal(0x04)
	assert_int(buf[4]).is_equal(0x05)
	assert_int(buf[5]).is_equal(0x06)
	assert_int(buf[6]).is_equal(0x07)
	assert_int(buf[7]).is_equal(0x08)


func test_encode_int64_be_0xFF_all_bytes() -> void:
	# -1 in two's complement should produce all 0xFF bytes
	var buf := UdpConnection._encode_int64_be(-1)
	assert_int(buf.size()).is_equal(8)
	for i in range(8):
		assert_int(buf[i]).is_equal(0xFF)


# ---- Big-endian encoding: uint32 ----

func test_encode_uint32_be_zero() -> void:
	var buf := UdpConnection._encode_uint32_be(0)
	assert_int(buf.size()).is_equal(4)
	for i in range(4):
		assert_int(buf[i]).is_equal(0)


func test_encode_uint32_be_value() -> void:
	# 0x01020304
	var buf := UdpConnection._encode_uint32_be(0x01020304)
	assert_int(buf[0]).is_equal(0x01)
	assert_int(buf[1]).is_equal(0x02)
	assert_int(buf[2]).is_equal(0x03)
	assert_int(buf[3]).is_equal(0x04)


func test_encode_uint32_be_max() -> void:
	# 0xFFFFFFFF
	var buf := UdpConnection._encode_uint32_be(0xFFFFFFFF)
	for i in range(4):
		assert_int(buf[i]).is_equal(0xFF)


func test_encode_uint32_be_256() -> void:
	# 256 = 0x00000100
	var buf := UdpConnection._encode_uint32_be(256)
	assert_int(buf[0]).is_equal(0x00)
	assert_int(buf[1]).is_equal(0x00)
	assert_int(buf[2]).is_equal(0x01)
	assert_int(buf[3]).is_equal(0x00)


# ---- Big-endian encoding: uint16 ----

func test_encode_uint16_be_zero() -> void:
	var buf := UdpConnection._encode_uint16_be(0)
	assert_int(buf.size()).is_equal(2)
	assert_int(buf[0]).is_equal(0)
	assert_int(buf[1]).is_equal(0)


func test_encode_uint16_be_value() -> void:
	# Opcode 0x0B01 = MOVEMENT_INPUT
	var buf := UdpConnection._encode_uint16_be(0x0B01)
	assert_int(buf[0]).is_equal(0x0B)
	assert_int(buf[1]).is_equal(0x01)


func test_encode_uint16_be_max() -> void:
	var buf := UdpConnection._encode_uint16_be(0xFFFF)
	assert_int(buf[0]).is_equal(0xFF)
	assert_int(buf[1]).is_equal(0xFF)


func test_encode_uint16_be_single_byte() -> void:
	# 42 = 0x002A
	var buf := UdpConnection._encode_uint16_be(42)
	assert_int(buf[0]).is_equal(0x00)
	assert_int(buf[1]).is_equal(0x2A)


# ---- Packet framing constants ----

func test_min_packet_size_is_46() -> void:
	# 8 (session) + 4 (seq) + 2 (opcode) + 32 (HMAC) = 46
	assert_int(UdpConnection.MIN_PACKET_SIZE).is_equal(46)


func test_max_packet_size() -> void:
	assert_int(UdpConnection.MAX_UDP_PACKET_SIZE).is_equal(512)


# ---- Sequence counter ----

func test_initial_sequence_is_zero() -> void:
	var udp := UdpConnection.new()
	assert_int(udp.get_sequence()).is_equal(0)


func test_not_connected_by_default() -> void:
	var udp := UdpConnection.new()
	assert_bool(udp.is_connected_to_server()).is_false()


# ---- HMAC computation (integration) ----

func test_hmac_produces_32_bytes() -> void:
	# We test HMAC indirectly by connecting with a test secret and inspecting state
	# Since _compute_hmac_sha256 is instance-private, we verify via the packet format
	# that HMAC produces correct length. This is a structural test.
	var udp := UdpConnection.new()
	# Set up internal state manually for testing
	udp._hmac_secret = "test-secret".to_utf8_buffer()
	var test_data := PackedByteArray([0x01, 0x02, 0x03, 0x04])
	var hmac := udp._compute_hmac_sha256(test_data)
	assert_int(hmac.size()).is_equal(32)


func test_hmac_deterministic() -> void:
	var udp := UdpConnection.new()
	udp._hmac_secret = "my-secret-key".to_utf8_buffer()
	var data := PackedByteArray([0xDE, 0xAD, 0xBE, 0xEF])
	var hmac1 := udp._compute_hmac_sha256(data)
	var hmac2 := udp._compute_hmac_sha256(data)
	assert_int(hmac1.size()).is_equal(32)
	# Same input + same key = same HMAC
	for i in range(32):
		assert_int(hmac1[i]).is_equal(hmac2[i])


func test_hmac_different_data_different_result() -> void:
	var udp := UdpConnection.new()
	udp._hmac_secret = "my-secret-key".to_utf8_buffer()
	var hmac1 := udp._compute_hmac_sha256(PackedByteArray([0x01]))
	var hmac2 := udp._compute_hmac_sha256(PackedByteArray([0x02]))
	# Different input → different HMAC (with overwhelmingly high probability)
	var different := false
	for i in range(32):
		if hmac1[i] != hmac2[i]:
			different = true
			break
	assert_bool(different).is_true()


func test_hmac_different_key_different_result() -> void:
	var data := PackedByteArray([0x01, 0x02, 0x03])
	var udp1 := UdpConnection.new()
	udp1._hmac_secret = "key-one".to_utf8_buffer()
	var hmac1 := udp1._compute_hmac_sha256(data)

	var udp2 := UdpConnection.new()
	udp2._hmac_secret = "key-two".to_utf8_buffer()
	var hmac2 := udp2._compute_hmac_sha256(data)

	var different := false
	for i in range(32):
		if hmac1[i] != hmac2[i]:
			different = true
			break
	assert_bool(different).is_true()


# ---- Disconnect ----

func test_disconnect_resets_state() -> void:
	var udp := UdpConnection.new()
	udp.disconnect_from_server()
	assert_bool(udp.is_connected_to_server()).is_false()
	assert_int(udp.get_sequence()).is_equal(0)
