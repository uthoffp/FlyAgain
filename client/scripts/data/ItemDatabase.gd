## ItemDatabase.gd
## Hardcoded item definitions matching DB seed V11.
## Sync note: mirrors server migration V11. Server changes require manual update here.
class_name ItemDatabase
extends RefCounted

# Item types (matches server ItemDefinitionCache constants)
const TYPE_WEAPON     := 0
const TYPE_ARMOR      := 1
const TYPE_QUEST_ITEM := 2
const TYPE_CONSUMABLE := 3

# Equipment slot types
const EQUIP_HEAD   := 0
const EQUIP_CHEST  := 1
const EQUIP_LEGS   := 2
const EQUIP_FEET   := 3
const EQUIP_HANDS  := 4
const EQUIP_BACK   := 5
const EQUIP_WEAPON := 6

# Rarity levels
const RARITY_COMMON   := 0
const RARITY_UNCOMMON := 1
const RARITY_RARE     := 2
const RARITY_EPIC     := 3

# Rarity colors for UI
const RARITY_COLORS := {
	RARITY_COMMON:   Color(0.9, 0.9, 0.9),
	RARITY_UNCOMMON: Color(0.3, 0.8, 0.3),
	RARITY_RARE:     Color(0.3, 0.5, 1.0),
	RARITY_EPIC:     Color(0.7, 0.3, 0.9),
}

# Item type colors for slot background
const TYPE_COLORS := {
	TYPE_WEAPON:     Color(0.7, 0.2, 0.2, 0.6),
	TYPE_ARMOR:      Color(0.2, 0.3, 0.7, 0.6),
	TYPE_CONSUMABLE: Color(0.2, 0.6, 0.3, 0.6),
	TYPE_QUEST_ITEM: Color(0.6, 0.5, 0.2, 0.6),
}

# All items keyed by item_id — mirrors V11__seed_items_and_loot_tables.sql
const ITEMS: Dictionary = {
	1: {
		"name": "ITEM_WOODEN_SWORD", "type": TYPE_WEAPON, "subtype": 0,
		"level_req": 1, "class_req": 0, "rarity": RARITY_COMMON,
		"base_attack": 5, "base_defense": 0, "base_hp": 0, "base_mp": 0,
		"buy_price": 10, "sell_price": 5, "stackable": false, "max_stack": 1,
		"description": "ITEM_WOODEN_SWORD_DESC",
	},
	2: {
		"name": "ITEM_IRON_SWORD", "type": TYPE_WEAPON, "subtype": 0,
		"level_req": 5, "class_req": 0, "rarity": RARITY_COMMON,
		"base_attack": 12, "base_defense": 0, "base_hp": 0, "base_mp": 0,
		"buy_price": 100, "sell_price": 50, "stackable": false, "max_stack": 1,
		"description": "ITEM_IRON_SWORD_DESC",
	},
	3: {
		"name": "ITEM_STEEL_SWORD", "type": TYPE_WEAPON, "subtype": 0,
		"level_req": 10, "class_req": 0, "rarity": RARITY_UNCOMMON,
		"base_attack": 22, "base_defense": 0, "base_hp": 0, "base_mp": 0,
		"buy_price": 500, "sell_price": 250, "stackable": false, "max_stack": 1,
		"description": "ITEM_STEEL_SWORD_DESC",
	},
	4: {
		"name": "ITEM_LEATHER_ARMOR", "type": TYPE_ARMOR, "subtype": 1,
		"level_req": 1, "class_req": 0, "rarity": RARITY_COMMON,
		"base_attack": 0, "base_defense": 3, "base_hp": 0, "base_mp": 0,
		"buy_price": 15, "sell_price": 7, "stackable": false, "max_stack": 1,
		"description": "ITEM_LEATHER_ARMOR_DESC",
	},
	5: {
		"name": "ITEM_CHAIN_ARMOR", "type": TYPE_ARMOR, "subtype": 1,
		"level_req": 5, "class_req": 0, "rarity": RARITY_COMMON,
		"base_attack": 0, "base_defense": 8, "base_hp": 0, "base_mp": 0,
		"buy_price": 120, "sell_price": 60, "stackable": false, "max_stack": 1,
		"description": "ITEM_CHAIN_ARMOR_DESC",
	},
	6: {
		"name": "ITEM_PLATE_ARMOR", "type": TYPE_ARMOR, "subtype": 1,
		"level_req": 10, "class_req": 0, "rarity": RARITY_UNCOMMON,
		"base_attack": 0, "base_defense": 15, "base_hp": 0, "base_mp": 0,
		"buy_price": 600, "sell_price": 300, "stackable": false, "max_stack": 1,
		"description": "ITEM_PLATE_ARMOR_DESC",
	},
	7: {
		"name": "ITEM_HEALTH_POTION", "type": TYPE_CONSUMABLE, "subtype": 0,
		"level_req": 1, "class_req": 0, "rarity": RARITY_COMMON,
		"base_attack": 0, "base_defense": 0, "base_hp": 50, "base_mp": 0,
		"buy_price": 5, "sell_price": 2, "stackable": true, "max_stack": 20,
		"description": "ITEM_HEALTH_POTION_DESC",
	},
	8: {
		"name": "ITEM_MANA_POTION", "type": TYPE_CONSUMABLE, "subtype": 0,
		"level_req": 1, "class_req": 0, "rarity": RARITY_COMMON,
		"base_attack": 0, "base_defense": 0, "base_hp": 0, "base_mp": 30,
		"buy_price": 8, "sell_price": 4, "stackable": true, "max_stack": 20,
		"description": "ITEM_MANA_POTION_DESC",
	},
}


static func get_item(item_id: int) -> Dictionary:
	return ITEMS.get(item_id, {})


static func get_rarity_color(rarity: int) -> Color:
	return RARITY_COLORS.get(rarity, Color.WHITE)


static func get_type_color(type: int) -> Color:
	return TYPE_COLORS.get(type, Color(0.3, 0.3, 0.3, 0.6))


## Returns the equipment slot type for an item, or -1 if not equipable.
static func get_equip_slot(item_def: Dictionary) -> int:
	var type: int = item_def.get("type", -1)
	match type:
		TYPE_WEAPON:
			return EQUIP_WEAPON
		TYPE_ARMOR:
			return item_def.get("subtype", -1)
		_:
			return -1


static func is_equipable(item_def: Dictionary) -> bool:
	return get_equip_slot(item_def) >= 0
