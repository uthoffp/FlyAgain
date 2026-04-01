## PacketProtocolTest.gd
## Unit tests for packet framing and opcode constants.
class_name PacketProtocolTest
extends GdUnitTestSuite


# ---- build_packet ----

func test_build_packet_total_size() -> void:
	var payload := PackedByteArray([0x01, 0x02, 0x03])
	var packet := PacketProtocol.build_packet(0x0001, payload)
	# 4 (length prefix) + 2 (opcode) + 3 (payload) = 9
	assert_int(packet.size()).is_equal(9)


func test_build_packet_length_prefix() -> void:
	var payload := PackedByteArray([0xAA, 0xBB])
	var packet := PacketProtocol.build_packet(0x0001, payload)
	# frame_length = 2 (opcode) + 2 (payload) = 4
	# Big-endian: 0x00 0x00 0x00 0x04
	assert_int(packet[0]).is_equal(0x00)
	assert_int(packet[1]).is_equal(0x00)
	assert_int(packet[2]).is_equal(0x00)
	assert_int(packet[3]).is_equal(0x04)


func test_build_packet_opcode_encoding() -> void:
	var packet := PacketProtocol.build_packet(0x0601, PackedByteArray())
	# Opcode 0x0601 big-endian at bytes 4-5
	assert_int(packet[4]).is_equal(0x06)
	assert_int(packet[5]).is_equal(0x01)


func test_build_packet_empty_payload() -> void:
	var packet := PacketProtocol.build_packet(PacketProtocol.OPCODE_HEARTBEAT, PackedByteArray())
	# Total: 4 + 2 + 0 = 6
	assert_int(packet.size()).is_equal(6)
	# frame_length = 2
	assert_int(packet[3]).is_equal(2)


func test_build_packet_payload_preserved() -> void:
	var payload := PackedByteArray([0xDE, 0xAD, 0xBE, 0xEF])
	var packet := PacketProtocol.build_packet(0x0001, payload)
	# Payload starts at byte 6
	assert_int(packet[6]).is_equal(0xDE)
	assert_int(packet[7]).is_equal(0xAD)
	assert_int(packet[8]).is_equal(0xBE)
	assert_int(packet[9]).is_equal(0xEF)


# ---- opcode_name ----

func test_opcode_name_auth() -> void:
	assert_str(PacketProtocol.opcode_name(0x0001)).is_equal("LOGIN_REQUEST")
	assert_str(PacketProtocol.opcode_name(0x0002)).is_equal("LOGIN_RESPONSE")
	assert_str(PacketProtocol.opcode_name(0x0003)).is_equal("CHARACTER_SELECT")
	assert_str(PacketProtocol.opcode_name(0x0004)).is_equal("ENTER_WORLD")
	assert_str(PacketProtocol.opcode_name(0x0005)).is_equal("CHARACTER_CREATE")
	assert_str(PacketProtocol.opcode_name(0x0006)).is_equal("REGISTER_REQUEST")
	assert_str(PacketProtocol.opcode_name(0x0007)).is_equal("REGISTER_RESPONSE")


func test_opcode_name_system() -> void:
	assert_str(PacketProtocol.opcode_name(0x0601)).is_equal("HEARTBEAT")
	assert_str(PacketProtocol.opcode_name(0x0602)).is_equal("SERVER_MESSAGE")
	assert_str(PacketProtocol.opcode_name(0x0603)).is_equal("ERROR_RESPONSE")


func test_opcode_name_unknown() -> void:
	var opcode_str := PacketProtocol.opcode_name(0xFFFF)
	assert_str(opcode_str).contains("UNKNOWN")
	assert_str(opcode_str).contains("FFFF")
