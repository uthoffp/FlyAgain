## NpcRegistry.gd
## Hardcoded NPC data matching DB seed V15.
## Sync note: mirrors server migration V15. Server changes require manual update here.
class_name NpcRegistry
extends RefCounted

# NPC data keyed by npc_def_id
const NPCS: Dictionary = {
	1: {
		"name": "NPC_WEAPON_MERCHANT",
		"zone_id": 0,
		"pos": Vector3(492.0, 0.0, 514.0),
		"shop_items": [1, 2, 3],
	},
	2: {
		"name": "NPC_ARMOR_MERCHANT",
		"zone_id": 0,
		"pos": Vector3(492.0, 0.0, 520.0),
		"shop_items": [4, 5, 6],
	},
	3: {
		"name": "NPC_POTION_MERCHANT",
		"zone_id": 0,
		"pos": Vector3(492.0, 0.0, 526.0),
		"shop_items": [7, 8],
	},
}

const PROXIMITY_RANGE := 10.0


static func get_npc(npc_def_id: int) -> Dictionary:
	return NPCS.get(npc_def_id, {})


static func get_shop_items(npc_def_id: int) -> Array:
	var npc := get_npc(npc_def_id)
	return npc.get("shop_items", [])


static func is_in_range(npc_def_id: int, player_pos: Vector3) -> bool:
	var npc := get_npc(npc_def_id)
	if npc.is_empty():
		return false
	var npc_pos: Vector3 = npc.get("pos", Vector3.ZERO)
	return player_pos.distance_to(npc_pos) <= PROXIMITY_RANGE
