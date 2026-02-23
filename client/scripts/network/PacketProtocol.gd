## PacketProtocol.gd
## Opcode constants and packet framing for the FlyAgain binary protocol.
##
## Wire format (TCP):
##   [4 bytes big-endian length][2 bytes big-endian opcode][N bytes protobuf payload]
##   Length = opcode_size (2) + payload_size — does NOT include the 4-byte length field itself.
class_name PacketProtocol
extends RefCounted

# ---- Opcodes (matching shared/proto/flyagain.proto) ----

# Auth (0x0001 – 0x0007)
const OPCODE_LOGIN_REQUEST      := 0x0001
const OPCODE_LOGIN_RESPONSE     := 0x0002
const OPCODE_CHARACTER_SELECT   := 0x0003
const OPCODE_ENTER_WORLD        := 0x0004
const OPCODE_CHARACTER_CREATE   := 0x0005
const OPCODE_REGISTER_REQUEST   := 0x0006
const OPCODE_REGISTER_RESPONSE  := 0x0007

# Movement (0x0101 – 0x0103)
const OPCODE_MOVEMENT_INPUT     := 0x0101
const OPCODE_ENTITY_POSITION    := 0x0102
const OPCODE_POSITION_CORRECTION := 0x0103

# Combat (0x0201 – 0x0206)
const OPCODE_SELECT_TARGET      := 0x0201
const OPCODE_USE_SKILL          := 0x0202
const OPCODE_DAMAGE_EVENT       := 0x0203
const OPCODE_ENTITY_DEATH       := 0x0204
const OPCODE_XP_GAIN            := 0x0205
const OPCODE_TOGGLE_AUTO_ATTACK := 0x0206

# Entity (0x0301 – 0x0303)
const OPCODE_ENTITY_SPAWN       := 0x0301
const OPCODE_ENTITY_DESPAWN     := 0x0302
const OPCODE_ENTITY_STATS_UPDATE := 0x0303

# Inventory (0x0401 – 0x0407)
const OPCODE_MOVE_ITEM          := 0x0401
const OPCODE_INVENTORY_UPDATE   := 0x0402
const OPCODE_EQUIP_ITEM         := 0x0403
const OPCODE_UNEQUIP_ITEM       := 0x0404
const OPCODE_NPC_BUY            := 0x0405
const OPCODE_NPC_SELL           := 0x0406
const OPCODE_GOLD_UPDATE        := 0x0407

# Chat (0x0501 – 0x0502)
const OPCODE_CHAT_MESSAGE       := 0x0501
const OPCODE_CHAT_BROADCAST     := 0x0502

# System (0x0601 – 0x0603)
const OPCODE_HEARTBEAT          := 0x0601
const OPCODE_SERVER_MESSAGE     := 0x0602
const OPCODE_ERROR_RESPONSE     := 0x0603

# Zone (0x0701 – 0x0703)
const OPCODE_ZONE_DATA          := 0x0701
const OPCODE_CHANNEL_SWITCH     := 0x0702
const OPCODE_CHANNEL_LIST       := 0x0703


## Builds a framed TCP packet:
##   [4-byte big-endian frame_length][2-byte big-endian opcode][payload]
## frame_length = 2 (opcode) + payload.size()
static func build_packet(opcode: int, payload: PackedByteArray) -> PackedByteArray:
	var frame_len := 2 + payload.size()
	var packet    := PackedByteArray()
	packet.resize(6)

	# Big-endian length prefix
	packet[0] = (frame_len >> 24) & 0xFF
	packet[1] = (frame_len >> 16) & 0xFF
	packet[2] = (frame_len >> 8)  & 0xFF
	packet[3] =  frame_len        & 0xFF

	# Big-endian opcode
	packet[4] = (opcode >> 8) & 0xFF
	packet[5] =  opcode       & 0xFF

	packet.append_array(payload)
	return packet


## Returns a human-readable opcode name for debugging.
static func opcode_name(opcode: int) -> String:
	match opcode:
		OPCODE_LOGIN_REQUEST:      return "LOGIN_REQUEST"
		OPCODE_LOGIN_RESPONSE:     return "LOGIN_RESPONSE"
		OPCODE_CHARACTER_SELECT:   return "CHARACTER_SELECT"
		OPCODE_ENTER_WORLD:        return "ENTER_WORLD"
		OPCODE_CHARACTER_CREATE:   return "CHARACTER_CREATE"
		OPCODE_REGISTER_REQUEST:   return "REGISTER_REQUEST"
		OPCODE_REGISTER_RESPONSE:  return "REGISTER_RESPONSE"
		OPCODE_HEARTBEAT:          return "HEARTBEAT"
		OPCODE_SERVER_MESSAGE:     return "SERVER_MESSAGE"
		OPCODE_ERROR_RESPONSE:     return "ERROR_RESPONSE"
		OPCODE_ENTITY_SPAWN:       return "ENTITY_SPAWN"
		OPCODE_ENTITY_DESPAWN:     return "ENTITY_DESPAWN"
		OPCODE_ENTITY_POSITION:    return "ENTITY_POSITION"
		OPCODE_ZONE_DATA:          return "ZONE_DATA"
		_: return "UNKNOWN(0x%04X)" % opcode
