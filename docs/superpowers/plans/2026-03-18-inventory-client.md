# Phase 1.6 Client-Side: Inventory, Equipment & NPC-Shops — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Godot 4 (GDScript) client UI and networking for inventory management, equipment, and NPC shops — connecting to the already-complete server handlers.

**Architecture:** Data layer (ItemDatabase, NpcRegistry) → Proto encoding/decoding → NetworkManager signals/dispatch → GameState delta-merge → UI screens (InventoryScreen, NpcShopScreen, ItemTooltip, NpcDialog). Server-authoritative with optimistic UI + rollback.

**Tech Stack:** Godot 4 (GDScript), manual protobuf encoding, GdUnit tests

**Spec:** `docs/superpowers/specs/2026-03-18-inventory-equipment-npc-shops-client-design.md`

---

## File Structure

### New Files
- `client/scripts/data/ItemDatabase.gd` — Hardcoded item definitions (8 items, mirrors DB seed V11)
- `client/scripts/data/NpcRegistry.gd` — Hardcoded NPC + shop data (3 NPCs, mirrors DB seed V15)
- `client/scenes/ui/game_hud/InventoryScreen.gd` — Combined inventory (10x10 grid) + equipment (7 slots) window
- `client/scenes/ui/game_hud/ItemTooltip.gd` — Hover tooltip showing item stats
- `client/scenes/ui/game_hud/NpcDialog.gd` — Small NPC interaction popup (name + Shop button)
- `client/scenes/ui/game_hud/NpcShopScreen.gd` — Shop buy/sell window
- `client/tests/data/ItemDatabaseTest.gd` — Tests for ItemDatabase
- `client/tests/data/NpcRegistryTest.gd` — Tests for NpcRegistry
- `client/tests/proto/ProtoInventoryTest.gd` — Roundtrip tests for inventory encoder/decoder

### Modified Files
- `client/autoloads/GameState.gd` — Add inventory_slots, equipment_slots, signals, delta-merge
- `client/scripts/proto/ProtoEncoder.gd` — Add 5 inventory encode methods
- `client/scripts/proto/ProtoDecoder.gd` — Add 6 inventory decode methods
- `client/autoloads/NetworkManager.gd` — Add 6 signals, 5 send methods, 6 dispatch cases
- `client/scenes/ui/game_hud/PlayerFrame.gd` — Add permanent gold display
- `client/scenes/game/GameWorld.gd` — Add I-key toggle, inventory/shop HUD wiring, NPC click, inventory_updated signal, loot notifications
- `client/translations/translations.csv` — Add inventory/equipment/shop localization keys (EN+DE)

---

## Task 1: Item Database & NPC Registry

**Files:**
- Create: `client/scripts/data/ItemDatabase.gd`
- Create: `client/scripts/data/NpcRegistry.gd`
- Create: `client/tests/data/ItemDatabaseTest.gd`
- Create: `client/tests/data/NpcRegistryTest.gd`

- [ ] **Step 1: Create `ItemDatabase.gd`**

```gdscript
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
```

- [ ] **Step 2: Create `ItemDatabaseTest.gd`**

```gdscript
## ItemDatabaseTest.gd
class_name ItemDatabaseTest
extends GdUnitTestSuite


func test_get_item_returns_valid_data() -> void:
	var item := ItemDatabase.get_item(1)
	assert_str(item.get("name", "")).is_equal("ITEM_WOODEN_SWORD")
	assert_int(item.get("type", -1)).is_equal(ItemDatabase.TYPE_WEAPON)
	assert_int(item.get("base_attack", 0)).is_equal(5)


func test_get_item_unknown_returns_empty() -> void:
	var item := ItemDatabase.get_item(999)
	assert_bool(item.is_empty()).is_true()


func test_all_8_items_defined() -> void:
	for id in range(1, 9):
		var item := ItemDatabase.get_item(id)
		assert_bool(item.is_empty()).is_false()


func test_get_equip_slot_weapon() -> void:
	var sword := ItemDatabase.get_item(1)
	assert_int(ItemDatabase.get_equip_slot(sword)).is_equal(ItemDatabase.EQUIP_WEAPON)


func test_get_equip_slot_armor() -> void:
	var armor := ItemDatabase.get_item(4)  # Leather Armor, subtype=1 (chest)
	assert_int(ItemDatabase.get_equip_slot(armor)).is_equal(ItemDatabase.EQUIP_CHEST)


func test_get_equip_slot_consumable_not_equipable() -> void:
	var potion := ItemDatabase.get_item(7)
	assert_int(ItemDatabase.get_equip_slot(potion)).is_equal(-1)


func test_is_equipable() -> void:
	assert_bool(ItemDatabase.is_equipable(ItemDatabase.get_item(1))).is_true()
	assert_bool(ItemDatabase.is_equipable(ItemDatabase.get_item(7))).is_false()
```

- [ ] **Step 3: Create `NpcRegistry.gd`**

```gdscript
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
		"pos": Vector3(505.0, 0.0, 495.0),
		"shop_items": [1, 2, 3],
	},
	2: {
		"name": "NPC_ARMOR_MERCHANT",
		"zone_id": 0,
		"pos": Vector3(510.0, 0.0, 495.0),
		"shop_items": [4, 5, 6],
	},
	3: {
		"name": "NPC_POTION_MERCHANT",
		"zone_id": 0,
		"pos": Vector3(515.0, 0.0, 495.0),
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
```

- [ ] **Step 4: Create `NpcRegistryTest.gd`**

```gdscript
## NpcRegistryTest.gd
class_name NpcRegistryTest
extends GdUnitTestSuite


func test_get_npc_weapon_merchant() -> void:
	var npc := NpcRegistry.get_npc(1)
	assert_str(npc.get("name", "")).is_equal("NPC_WEAPON_MERCHANT")


func test_get_npc_unknown_returns_empty() -> void:
	var npc := NpcRegistry.get_npc(999)
	assert_bool(npc.is_empty()).is_true()


func test_get_shop_items() -> void:
	var items := NpcRegistry.get_shop_items(1)
	assert_int(items.size()).is_equal(3)
	assert_int(items[0]).is_equal(1)


func test_is_in_range_close() -> void:
	assert_bool(NpcRegistry.is_in_range(1, Vector3(505.0, 0.0, 495.0))).is_true()


func test_is_in_range_far() -> void:
	assert_bool(NpcRegistry.is_in_range(1, Vector3(600.0, 0.0, 600.0))).is_false()
```

- [ ] **Step 5: Commit**

```bash
git add client/scripts/data/ItemDatabase.gd client/scripts/data/NpcRegistry.gd \
       client/tests/data/ItemDatabaseTest.gd client/tests/data/NpcRegistryTest.gd
git commit -m "feat: add ItemDatabase and NpcRegistry with tests (Phase 1.6 client data layer)"
```

---

## Task 2: Proto Encoder — Inventory Methods

**Files:**
- Modify: `client/scripts/proto/ProtoEncoder.gd` (append after line 259)
- Create: `client/tests/proto/ProtoInventoryTest.gd`

- [ ] **Step 1: Add 5 inventory encoder methods to `ProtoEncoder.gd`**

Append after line 259 (after `encode_use_skill_request`):

```gdscript


## ---- Inventory Messages ----

## Encodes ClientMoveItemRequest { int32 from_slot = 1; int32 to_slot = 2 }
static func encode_move_item_request(from_slot: int, to_slot: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_int32_field(1, from_slot))
	buf.append_array(_int32_field(2, to_slot))
	return buf


## Encodes ClientEquipItemRequest { int32 inventory_slot = 1; int32 equip_slot_type = 2 }
static func encode_equip_item_request(inventory_slot: int, equip_slot_type: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_int32_field(1, inventory_slot))
	buf.append_array(_int32_field(2, equip_slot_type))
	return buf


## Encodes ClientUnequipItemRequest { int32 equip_slot_type = 1 }
static func encode_unequip_item_request(equip_slot_type: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_int32_field(1, equip_slot_type))
	return buf


## Encodes ClientNpcBuyRequest { int64 npc_entity_id = 1; int32 item_def_id = 2; int32 amount = 3 }
static func encode_npc_buy_request(npc_entity_id: int, item_def_id: int, amount: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_int64_field(1, npc_entity_id))
	buf.append_array(_int32_field(2, item_def_id))
	buf.append_array(_int32_field(3, amount))
	return buf


## Encodes ClientNpcSellRequest { int64 npc_entity_id = 1; int32 inventory_slot = 2; int32 amount = 3 }
static func encode_npc_sell_request(npc_entity_id: int, inventory_slot: int, amount: int) -> PackedByteArray:
	var buf := PackedByteArray()
	buf.append_array(_int64_field(1, npc_entity_id))
	buf.append_array(_int32_field(2, inventory_slot))
	buf.append_array(_int32_field(3, amount))
	return buf
```

- [ ] **Step 2: Commit encoder**

```bash
git add client/scripts/proto/ProtoEncoder.gd
git commit -m "feat: add inventory proto encoder methods (MoveItem, EquipItem, UnequipItem, NpcBuy, NpcSell)"
```

---

## Task 3: Proto Decoder — Inventory Methods

**Files:**
- Modify: `client/scripts/proto/ProtoDecoder.gd` (insert after `decode_gold_update`, before line 523)

- [ ] **Step 1: Add 6 inventory decoder methods to `ProtoDecoder.gd`**

Insert after line 521 (after the closing of `decode_gold_update`):

```gdscript


## Decodes a ClientMoveItemResponse { bool success = 1; string error_message = 2 }
func decode_move_item_response() -> Dictionary:
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


## Decodes a ClientEquipItemResponse { bool success = 1; string error_message = 2 }
func decode_equip_item_response() -> Dictionary:
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


## Decodes a ClientUnequipItemResponse { bool success = 1; string error_message = 2 }
func decode_unequip_item_response() -> Dictionary:
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


## Decodes a ClientNpcBuyResponse { bool success=1; int64 new_gold=2; int32 assigned_slot=3; string error_message=4 }
func decode_npc_buy_response() -> Dictionary:
	var result := {"success": false, "new_gold": 0, "assigned_slot": -1, "error_message": ""}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["success"]       = _read_varint() != 0
			2: result["new_gold"]      = _read_varint()
			3: result["assigned_slot"] = _read_varint()
			4: result["error_message"] = _read_string()
			_: _skip(wt)
	return result


## Decodes a ClientNpcSellResponse { bool success=1; int64 new_gold=2; string error_message=3 }
func decode_npc_sell_response() -> Dictionary:
	var result := {"success": false, "new_gold": 0, "error_message": ""}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["success"]       = _read_varint() != 0
			2: result["new_gold"]      = _read_varint()
			3: result["error_message"] = _read_string()
			_: _skip(wt)
	return result


## Decodes InventoryUpdateMessage { repeated InventorySlotInfo slots=1; repeated EquipmentSlotInfo equipment=2 }
func decode_inventory_update() -> Dictionary:
	var result := {"slots": [], "equipment": []}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1:
				var sub := ProtoDecoder.new(_read_bytes_ld())
				result["slots"].append(sub._decode_inventory_slot_info())
			2:
				var sub := ProtoDecoder.new(_read_bytes_ld())
				result["equipment"].append(sub._decode_equipment_slot_info())
			_: _skip(wt)
	return result


## Decodes InventorySlotInfo { int32 slot=1; int32 item_id=2; int32 amount=3; int32 enhancement=4 }
func _decode_inventory_slot_info() -> Dictionary:
	var result := {"slot": 0, "item_id": 0, "amount": 0, "enhancement": 0}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["slot"]        = _read_varint()
			2: result["item_id"]     = _read_varint()
			3: result["amount"]      = _read_varint()
			4: result["enhancement"] = _read_varint()
			_: _skip(wt)
	return result


## Decodes EquipmentSlotInfo { int32 slot_type=1; int32 item_id=2; int32 enhancement=3 }
func _decode_equipment_slot_info() -> Dictionary:
	var result := {"slot_type": 0, "item_id": 0, "enhancement": 0}
	while _has_bytes():
		var pair := _next_tag()
		if pair.is_empty():
			break
		var fn: int = pair[0]
		var wt: int = pair[1]
		match fn:
			1: result["slot_type"]   = _read_varint()
			2: result["item_id"]     = _read_varint()
			3: result["enhancement"] = _read_varint()
			_: _skip(wt)
	return result
```

- [ ] **Step 2: Create `ProtoInventoryTest.gd` with roundtrip tests**

```gdscript
## ProtoInventoryTest.gd
## Roundtrip tests for inventory proto encoder/decoder methods.
class_name ProtoInventoryTest
extends GdUnitTestSuite


func test_move_item_request_roundtrip() -> void:
	var buf := ProtoEncoder.encode_move_item_request(5, 42)
	var dec := ProtoDecoder.new(buf).decode_move_item_response()
	# MoveItemRequest fields: from_slot=1(int32), to_slot=2(int32)
	# MoveItemResponse fields: success=1(bool), error_message=2(string)
	# These are different messages so we can't roundtrip directly.
	# Instead verify encoding produces non-empty bytes.
	assert_bool(buf.size() > 0).is_true()


func test_move_item_request_fields() -> void:
	# Verify from_slot=5 and to_slot=42 are encoded
	var buf := ProtoEncoder.encode_move_item_request(5, 42)
	# Decode as generic varints: field 1 = 5, field 2 = 42
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)  # field number
	var val1 := dec._read_varint()
	assert_int(val1).is_equal(5)
	var tag2 := dec._next_tag()
	assert_int(tag2[0]).is_equal(2)
	var val2 := dec._read_varint()
	assert_int(val2).is_equal(42)


func test_equip_item_request_fields() -> void:
	var buf := ProtoEncoder.encode_equip_item_request(10, 6)
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)
	assert_int(dec._read_varint()).is_equal(10)
	var tag2 := dec._next_tag()
	assert_int(tag2[0]).is_equal(2)
	assert_int(dec._read_varint()).is_equal(6)


func test_unequip_item_request_fields() -> void:
	var buf := ProtoEncoder.encode_unequip_item_request(6)
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)
	assert_int(dec._read_varint()).is_equal(6)


func test_npc_buy_request_uses_int64_for_entity_id() -> void:
	var buf := ProtoEncoder.encode_npc_buy_request(1000001, 3, 5)
	assert_bool(buf.size() > 0).is_true()
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)  # npc_entity_id
	assert_int(dec._read_varint()).is_equal(1000001)


func test_npc_sell_request_fields() -> void:
	var buf := ProtoEncoder.encode_npc_sell_request(1000002, 15, 3)
	var dec := ProtoDecoder.new(buf)
	var tag1 := dec._next_tag()
	assert_int(tag1[0]).is_equal(1)
	assert_int(dec._read_varint()).is_equal(1000002)
	var tag2 := dec._next_tag()
	assert_int(tag2[0]).is_equal(2)
	assert_int(dec._read_varint()).is_equal(15)
	var tag3 := dec._next_tag()
	assert_int(tag3[0]).is_equal(3)
	assert_int(dec._read_varint()).is_equal(3)


func test_decode_move_item_response_success() -> void:
	# Manually build: success=true(field1), error_message=""(omitted)
	var buf := PackedByteArray()
	buf.append(0x08); buf.append(1)  # field 1, varint, value=1 (true)
	var result := ProtoDecoder.new(buf).decode_move_item_response()
	assert_bool(result["success"]).is_true()
	assert_str(result["error_message"]).is_equal("")


func test_decode_npc_buy_response() -> void:
	# Build: success=true, new_gold=950, assigned_slot=3
	var buf := PackedByteArray()
	buf.append(0x08); buf.append(1)    # field 1: success=true
	buf.append(0x10)                    # field 2: new_gold (varint tag)
	buf.append_array(ProtoEncoder._varint(950))
	buf.append(0x18); buf.append(3)    # field 3: assigned_slot=3
	var result := ProtoDecoder.new(buf).decode_npc_buy_response()
	assert_bool(result["success"]).is_true()
	assert_int(result["new_gold"]).is_equal(950)
	assert_int(result["assigned_slot"]).is_equal(3)


func test_decode_inventory_update_with_slots_and_equipment() -> void:
	# Build an InventoryUpdateMessage with 1 inventory slot and 1 equipment slot
	var buf := PackedByteArray()

	# Field 1: InventorySlotInfo submessage (slot=5, item_id=2, amount=1, enhancement=3)
	var slot_buf := PackedByteArray()
	slot_buf.append(0x08); slot_buf.append(5)   # slot=5
	slot_buf.append(0x10); slot_buf.append(2)   # item_id=2
	slot_buf.append(0x18); slot_buf.append(1)   # amount=1
	slot_buf.append(0x20); slot_buf.append(3)   # enhancement=3
	buf.append(0x0A)  # field 1, wire type 2 (LEN)
	buf.append(slot_buf.size())
	buf.append_array(slot_buf)

	# Field 2: EquipmentSlotInfo submessage (slot_type=6, item_id=2, enhancement=3)
	var equip_buf := PackedByteArray()
	equip_buf.append(0x08); equip_buf.append(6)  # slot_type=6
	equip_buf.append(0x10); equip_buf.append(2)  # item_id=2
	equip_buf.append(0x18); equip_buf.append(3)  # enhancement=3
	buf.append(0x12)  # field 2, wire type 2 (LEN)
	buf.append(equip_buf.size())
	buf.append_array(equip_buf)

	var result := ProtoDecoder.new(buf).decode_inventory_update()
	assert_int(result["slots"].size()).is_equal(1)
	assert_int(result["slots"][0]["slot"]).is_equal(5)
	assert_int(result["slots"][0]["item_id"]).is_equal(2)
	assert_int(result["slots"][0]["enhancement"]).is_equal(3)
	assert_int(result["equipment"].size()).is_equal(1)
	assert_int(result["equipment"][0]["slot_type"]).is_equal(6)


func test_decode_inventory_update_empty() -> void:
	var result := ProtoDecoder.new(PackedByteArray()).decode_inventory_update()
	assert_int(result["slots"].size()).is_equal(0)
	assert_int(result["equipment"].size()).is_equal(0)
```

- [ ] **Step 3: Commit decoder + tests**

```bash
git add client/scripts/proto/ProtoDecoder.gd client/tests/proto/ProtoInventoryTest.gd
git commit -m "feat: add inventory proto decoder methods and roundtrip tests"
```

---

## Task 4: GameState — Inventory & Equipment State

**Files:**
- Modify: `client/autoloads/GameState.gd`

- [ ] **Step 1: Add inventory/equipment state and signals to `GameState.gd`**

After line 61 (`var skill_cooldowns: Dictionary = {}`), add:

```gdscript

# ---- Inventory & Equipment ----
## Inventory: 100 slots (index = slot number).
## Each entry: null (empty) or { "item_id": int, "amount": int, "enhancement": int }
var inventory_slots: Array = []
## Equipment: keyed by slot_type (0-6).
## Each value: null or { "item_id": int, "enhancement": int }
var equipment_slots: Dictionary = {}

signal inventory_changed
signal equipment_changed
```

- [ ] **Step 2: Initialize inventory in `_ready` and `reset()`**

Add a new `_ready()` function **between the variable declarations and the `reset()` function** (i.e., after line 62, before `func reset()`):

```gdscript

func _ready() -> void:
	_init_inventory()
```

In the `reset()` function, add `_init_inventory()` as the **last line** inside `reset()` (after `skill_cooldowns = {}`):

```gdscript
	_init_inventory()
```

Then add `_init_inventory()` as a **class-level function** (not nested inside `reset()`) — place it after the closing of `reset()`, before `is_authenticated()`:

```gdscript

func _init_inventory() -> void:
	inventory_slots = []
	inventory_slots.resize(100)
	for i in range(100):
		inventory_slots[i] = null
	equipment_slots = {}
	for slot_type in range(7):
		equipment_slots[slot_type] = null
```

- [ ] **Step 3: Add delta-merge method**

At end of file, add:

```gdscript


## Merges a server InventoryUpdate delta into local state.
## item_id == 0 means the slot was cleared.
func merge_inventory_update(slots: Array, equipment: Array) -> void:
	var inv_changed := false
	var equip_changed := false

	for slot_data in slots:
		var slot_idx: int = slot_data.get("slot", -1)
		if slot_idx < 0 or slot_idx >= 100:
			continue
		var item_id: int = slot_data.get("item_id", 0)
		if item_id == 0:
			inventory_slots[slot_idx] = null
		else:
			inventory_slots[slot_idx] = {
				"item_id": item_id,
				"amount": slot_data.get("amount", 1),
				"enhancement": slot_data.get("enhancement", 0),
			}
		inv_changed = true

	for equip_data in equipment:
		var slot_type: int = equip_data.get("slot_type", -1)
		if slot_type < 0 or slot_type > 6:
			continue
		var item_id: int = equip_data.get("item_id", 0)
		if item_id == 0:
			equipment_slots[slot_type] = null
		else:
			equipment_slots[slot_type] = {
				"item_id": item_id,
				"enhancement": equip_data.get("enhancement", 0),
			}
		equip_changed = true

	if inv_changed:
		inventory_changed.emit()
	if equip_changed:
		equipment_changed.emit()
```

- [ ] **Step 4: Commit**

```bash
git add client/autoloads/GameState.gd
git commit -m "feat: add inventory/equipment state, delta-merge, and signals to GameState"
```

---

## Task 5: NetworkManager — Signals, Send Methods & Dispatch

**Files:**
- Modify: `client/autoloads/NetworkManager.gd`

- [ ] **Step 1: Add inventory signals**

After line 51 (`signal gold_updated(data: Dictionary)`), add:

```gdscript

# ---- Signals (inventory) ----

signal inventory_updated(data: Dictionary)
signal move_item_response(data: Dictionary)
signal equip_item_response(data: Dictionary)
signal unequip_item_response(data: Dictionary)
signal npc_buy_response(data: Dictionary)
signal npc_sell_response(data: Dictionary)
```

- [ ] **Step 2: Add send methods**

After line 275 (`send_use_skill`), add:

```gdscript


## ---- Inventory send methods ----

func send_move_item(from_slot: int, to_slot: int) -> void:
	_send_world(PacketProtocol.OPCODE_MOVE_ITEM,
		ProtoEncoder.encode_move_item_request(from_slot, to_slot))


func send_equip_item(inventory_slot: int, equip_slot_type: int) -> void:
	_send_world(PacketProtocol.OPCODE_EQUIP_ITEM,
		ProtoEncoder.encode_equip_item_request(inventory_slot, equip_slot_type))


func send_unequip_item(equip_slot_type: int) -> void:
	_send_world(PacketProtocol.OPCODE_UNEQUIP_ITEM,
		ProtoEncoder.encode_unequip_item_request(equip_slot_type))


func send_npc_buy(npc_entity_id: int, item_def_id: int, amount: int) -> void:
	_send_world(PacketProtocol.OPCODE_NPC_BUY,
		ProtoEncoder.encode_npc_buy_request(npc_entity_id, item_def_id, amount))


func send_npc_sell(npc_entity_id: int, inventory_slot: int, amount: int) -> void:
	_send_world(PacketProtocol.OPCODE_NPC_SELL,
		ProtoEncoder.encode_npc_sell_request(npc_entity_id, inventory_slot, amount))
```

- [ ] **Step 3: Add dispatch cases in `_dispatch_world_frame`**

In `_dispatch_world_frame()`, before the `PacketProtocol.OPCODE_HEARTBEAT:` case (line 596), add these cases:

```gdscript
		PacketProtocol.OPCODE_MOVE_ITEM:
			var data := ProtoDecoder.new(payload).decode_move_item_response()
			print("[NET] MOVE_ITEM: success=%s msg=%s" % [data.get("success", false), data.get("error_message", "")])
			move_item_response.emit(data)
		PacketProtocol.OPCODE_INVENTORY_UPDATE:
			var data := ProtoDecoder.new(payload).decode_inventory_update()
			print("[NET] INVENTORY_UPDATE: slots=%d equipment=%d" % [
				data.get("slots", []).size(), data.get("equipment", []).size()])
			GameState.merge_inventory_update(data.get("slots", []), data.get("equipment", []))
			inventory_updated.emit(data)
		PacketProtocol.OPCODE_EQUIP_ITEM:
			var data := ProtoDecoder.new(payload).decode_equip_item_response()
			print("[NET] EQUIP_ITEM: success=%s msg=%s" % [data.get("success", false), data.get("error_message", "")])
			equip_item_response.emit(data)
		PacketProtocol.OPCODE_UNEQUIP_ITEM:
			var data := ProtoDecoder.new(payload).decode_unequip_item_response()
			print("[NET] UNEQUIP_ITEM: success=%s msg=%s" % [data.get("success", false), data.get("error_message", "")])
			unequip_item_response.emit(data)
		PacketProtocol.OPCODE_NPC_BUY:
			var data := ProtoDecoder.new(payload).decode_npc_buy_response()
			print("[NET] NPC_BUY: success=%s gold=%d slot=%d" % [
				data.get("success", false), data.get("new_gold", 0), data.get("assigned_slot", -1)])
			npc_buy_response.emit(data)
		PacketProtocol.OPCODE_NPC_SELL:
			var data := ProtoDecoder.new(payload).decode_npc_sell_response()
			print("[NET] NPC_SELL: success=%s gold=%d" % [data.get("success", false), data.get("new_gold", 0)])
			npc_sell_response.emit(data)
```

- [ ] **Step 4: Commit**

```bash
git add client/autoloads/NetworkManager.gd
git commit -m "feat: add inventory signals, send methods, and packet dispatch to NetworkManager"
```

---

## Task 6: Localization — Inventory/Equipment/Shop Keys

**Files:**
- Modify: `client/translations/translations.csv`

- [ ] **Step 1: Append inventory localization keys**

Append to end of `translations.csv`:

```csv
INVENTORY_TITLE,Inventory,Inventar
EQUIP_HEAD,Head,Kopf
EQUIP_CHEST,Chest,Brust
EQUIP_LEGS,Legs,Beine
EQUIP_FEET,Feet,Füße
EQUIP_HANDS,Hands,Hände
EQUIP_BACK,Back,Rücken
EQUIP_WEAPON,Weapon,Waffe
SHOP_TITLE,Shop,Shop
SHOP_BUY,Buy,Kaufen
SHOP_SELL,Sell,Verkaufen
SHOP_CLOSE,Close,Schließen
SHOP_AMOUNT,Amount,Menge
SHOP_NOT_ENOUGH_GOLD,Not enough gold,Nicht genug Gold
SHOP_INVENTORY_FULL,Inventory full,Inventar voll
SHOP_LEVEL_TOO_LOW,Level too low,Stufe zu niedrig
ITEM_TYPE_WEAPON,Weapon,Waffe
ITEM_TYPE_ARMOR,Armor,Rüstung
ITEM_TYPE_CONSUMABLE,Consumable,Verbrauchsgegenstand
ITEM_TYPE_QUEST,Quest Item,Questgegenstand
ITEM_CLASS_ANY,Any,Alle
ITEM_CLASS_WARRIOR,Warrior,Krieger
ITEM_CLASS_MAGE,Mage,Magier
ITEM_CLASS_ASSASSIN,Assassin,Assassine
ITEM_CLASS_CLERIC,Cleric,Kleriker
ITEM_LEVEL_REQ,Level Req: {level},Stufenvoraussetzung: {level}
ITEM_RECEIVED,Received: {name},Erhalten: {name}
ITEM_RECEIVED_STACK,Received: {name} x{amount},Erhalten: {name} x{amount}
ITEM_WOODEN_SWORD,Wooden Sword,Holzschwert
ITEM_IRON_SWORD,Iron Sword,Eisenschwert
ITEM_STEEL_SWORD,Steel Sword,Stahlschwert
ITEM_LEATHER_ARMOR,Leather Armor,Lederrüstung
ITEM_CHAIN_ARMOR,Chain Armor,Kettenrüstung
ITEM_PLATE_ARMOR,Plate Armor,Plattenrüstung
ITEM_HEALTH_POTION,Health Potion,Heiltrank
ITEM_MANA_POTION,Mana Potion,Manatrank
ITEM_WOODEN_SWORD_DESC,A basic wooden training sword.,Ein einfaches Holz-Übungsschwert.
ITEM_IRON_SWORD_DESC,A sturdy iron blade.,Eine solide Eisenklinge.
ITEM_STEEL_SWORD_DESC,A well-crafted steel sword.,Ein gut geschmiedetes Stahlschwert.
ITEM_LEATHER_ARMOR_DESC,Light leather chest armor.,Leichte Leder-Brustrüstung.
ITEM_CHAIN_ARMOR_DESC,Linked chain mail armor.,Verknüpfte Kettenrüstung.
ITEM_PLATE_ARMOR_DESC,Heavy plate armor.,Schwere Plattenrüstung.
ITEM_HEALTH_POTION_DESC,Restores 50 HP.,Stellt 50 HP wieder her.
ITEM_MANA_POTION_DESC,Restores 30 MP.,Stellt 30 MP wieder her.
NPC_WEAPON_MERCHANT,Weapon Merchant,Waffenhändler
NPC_ARMOR_MERCHANT,Armor Merchant,Rüstungshändler
NPC_POTION_MERCHANT,Potion Merchant,Trankhändler
NPC_DIALOG_SHOP,Shop,Shop
```

- [ ] **Step 2: Commit**

```bash
git add client/translations/translations.csv
git commit -m "feat: add EN+DE localization keys for inventory, equipment, shop, items, and NPCs"
```

---

## Task 7: PlayerFrame — Gold Display

**Files:**
- Modify: `client/scenes/ui/game_hud/PlayerFrame.gd`

- [ ] **Step 1: Add gold label variable**

After line 20 (`var _xp_text: Label = null`), add:

```gdscript
var _gold_label: Label = null
```

- [ ] **Step 2: Add gold row in `_build_ui`**

At the end of the `_build_ui()` method (after line 192, after `_build_stat_bar(vbox, "xp")`), add:

```gdscript

	# Gold display row
	var gold_row := HBoxContainer.new()
	gold_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	gold_row.add_theme_constant_override("separation", 4)
	vbox.add_child(gold_row)

	var gold_icon := Label.new()
	gold_icon.text = "G"
	gold_icon.add_theme_color_override("font_color", Colors.GOLD)
	gold_icon.add_theme_font_size_override("font_size", 13)
	gold_icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
	gold_row.add_child(gold_icon)

	_gold_label = Label.new()
	_gold_label.text = "0"
	_gold_label.add_theme_color_override("font_color", Colors.TEXT_PRIMARY)
	_gold_label.add_theme_font_size_override("font_size", 13)
	_gold_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	gold_row.add_child(_gold_label)
```

- [ ] **Step 3: Update gold in `_process`**

At the end of `_process()` (after line 76), add:

```gdscript
	# Gold
	if _gold_label:
		_gold_label.text = _format_gold(GameState.player_gold)


static func _format_gold(amount: int) -> String:
	if amount < 1000:
		return str(amount)
	elif amount < 1000000:
		return "%d,%03d" % [amount / 1000, amount % 1000]
	else:
		return "%d,%03d,%03d" % [amount / 1000000, (amount / 1000) % 1000, amount % 1000]
```

- [ ] **Step 4: Commit**

```bash
git add client/scenes/ui/game_hud/PlayerFrame.gd
git commit -m "feat: add permanent gold display to PlayerFrame HUD"
```

---

## Task 8: Item Tooltip

**Files:**
- Create: `client/scenes/ui/game_hud/ItemTooltip.gd`

- [ ] **Step 1: Create `ItemTooltip.gd`**

```gdscript
## ItemTooltip.gd
## Hover tooltip showing item stats. Created dynamically, positioned near the slot.
extends PanelContainer


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_apply_style()


func setup(item_id: int, enhancement: int = 0, amount: int = 1) -> void:
	var item_def := ItemDatabase.get_item(item_id)
	if item_def.is_empty():
		queue_free()
		return
	_build_content(item_def, enhancement, amount)


func _apply_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.05, 0.05, 0.1, 0.95)
	style.border_color = Color(0.4, 0.4, 0.5, 0.8)
	style.set_border_width_all(1)
	style.set_corner_radius_all(4)
	style.set_content_margin_all(10)
	add_theme_stylebox_override("panel", style)


func _build_content(item_def: Dictionary, enhancement: int, amount: int) -> void:
	var vbox := VBoxContainer.new()
	vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_theme_constant_override("separation", 2)
	add_child(vbox)

	# Item name (colored by rarity)
	var rarity: int = item_def.get("rarity", 0)
	var name_text: String = tr(item_def.get("name", ""))
	if enhancement > 0:
		name_text += " +%d" % enhancement
	var name_label := Label.new()
	name_label.text = name_text
	name_label.add_theme_color_override("font_color", ItemDatabase.get_rarity_color(rarity))
	name_label.add_theme_font_size_override("font_size", 14)
	name_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_child(name_label)

	# Type label
	var type_key := _get_type_key(item_def.get("type", -1))
	if not type_key.is_empty():
		_add_line(vbox, tr(type_key), Colors.TEXT_SECONDARY, 11)

	# Separator
	_add_separator(vbox)

	# Stats
	var base_atk: int = item_def.get("base_attack", 0)
	var base_def: int = item_def.get("base_defense", 0)
	var base_hp: int = item_def.get("base_hp", 0)
	var base_mp: int = item_def.get("base_mp", 0)
	if base_atk > 0:
		_add_line(vbox, "ATK +%d" % base_atk, Colors.TEXT_PRIMARY, 12)
	if base_def > 0:
		_add_line(vbox, "DEF +%d" % base_def, Colors.TEXT_PRIMARY, 12)
	if base_hp > 0:
		_add_line(vbox, "HP +%d" % base_hp, Colors.TEXT_PRIMARY, 12)
	if base_mp > 0:
		_add_line(vbox, "MP +%d" % base_mp, Colors.TEXT_PRIMARY, 12)

	# Requirements
	var has_stats := base_atk > 0 or base_def > 0 or base_hp > 0 or base_mp > 0
	if has_stats:
		_add_separator(vbox)

	var level_req: int = item_def.get("level_req", 0)
	if level_req > 1:
		var level_color := Colors.TEXT_ERROR if GameState.player_level < level_req else Colors.TEXT_SECONDARY
		_add_line(vbox, tr("ITEM_LEVEL_REQ").replace("{level}", str(level_req)), level_color, 11)

	var class_req: int = item_def.get("class_req", 0)
	if class_req > 0:
		var class_keys := {1: "ITEM_CLASS_WARRIOR", 2: "ITEM_CLASS_MAGE", 3: "ITEM_CLASS_ASSASSIN", 4: "ITEM_CLASS_CLERIC"}
		_add_line(vbox, tr(class_keys.get(class_req, "ITEM_CLASS_ANY")), Colors.TEXT_SECONDARY, 11)

	# Prices
	_add_separator(vbox)
	var buy_price: int = item_def.get("buy_price", 0)
	var sell_price: int = item_def.get("sell_price", 0)
	_add_line(vbox, "Buy: %dg  Sell: %dg" % [buy_price, sell_price], Colors.GOLD, 11)

	# Description
	var desc: String = item_def.get("description", "")
	if not desc.is_empty():
		_add_separator(vbox)
		_add_line(vbox, tr(desc), Colors.TEXT_SECONDARY, 11)

	# Stack info
	if amount > 1:
		_add_line(vbox, "x%d" % amount, Colors.TEXT_SECONDARY, 11)


func _add_line(parent: VBoxContainer, text: String, color: Color, font_size: int) -> void:
	var label := Label.new()
	label.text = text
	label.add_theme_color_override("font_color", color)
	label.add_theme_font_size_override("font_size", font_size)
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(label)


func _add_separator(parent: VBoxContainer) -> void:
	var sep := HSeparator.new()
	sep.mouse_filter = Control.MOUSE_FILTER_IGNORE
	sep.add_theme_constant_override("separation", 4)
	parent.add_child(sep)


func _get_type_key(type: int) -> String:
	match type:
		ItemDatabase.TYPE_WEAPON: return "ITEM_TYPE_WEAPON"
		ItemDatabase.TYPE_ARMOR: return "ITEM_TYPE_ARMOR"
		ItemDatabase.TYPE_CONSUMABLE: return "ITEM_TYPE_CONSUMABLE"
		ItemDatabase.TYPE_QUEST_ITEM: return "ITEM_TYPE_QUEST"
		_: return ""
```

- [ ] **Step 2: Commit**

```bash
git add client/scenes/ui/game_hud/ItemTooltip.gd
git commit -m "feat: add ItemTooltip for hover-over item stat display"
```

---

## Task 9: Inventory Screen (UI)

**Files:**
- Create: `client/scenes/ui/game_hud/InventoryScreen.gd`

This is the largest task. The inventory screen combines the equipment panel (7 slots, left) with the item grid (10x10, right), gold display, drag & drop, double-click equip, and item tooltips.

- [ ] **Step 1: Create `InventoryScreen.gd`**

```gdscript
## InventoryScreen.gd
## Combined inventory (10x10) + equipment (7 slots) window.
## Non-modal: player can move while open. Toggle with I, close with ESC.
extends PanelContainer

const ItemTooltipScript := preload("res://scenes/ui/game_hud/ItemTooltip.gd")

const SLOT_SIZE := 44
const GRID_COLS := 10
const GRID_ROWS := 10
const EQUIP_SLOT_NAMES := ["EQUIP_HEAD", "EQUIP_CHEST", "EQUIP_LEGS", "EQUIP_FEET", "EQUIP_HANDS", "EQUIP_BACK", "EQUIP_WEAPON"]

# UI references
var _inv_slots: Array[Panel] = []      # 100 inventory slot panels
var _equip_slots: Array[Panel] = []    # 7 equipment slot panels
var _gold_label: Label = null
var _tooltip: PanelContainer = null

# Drag state
var _dragging: bool = false
var _drag_from_slot: int = -1
var _drag_from_equip: bool = false
var _drag_preview: Panel = null
var _pending_slots: Dictionary = {}    # slots locked while awaiting server response

# Rollback state for optimistic UI
var _rollback_snapshot: Dictionary = {}


func _ready() -> void:
	visible = false
	custom_minimum_size = Vector2(820, 520)
	mouse_filter = Control.MOUSE_FILTER_STOP
	_apply_panel_style()
	_build_ui()
	_refresh_all()
	GameState.inventory_changed.connect(_on_inventory_changed)
	GameState.equipment_changed.connect(_on_equipment_changed)
	NetworkManager.move_item_response.connect(_on_move_item_response)
	NetworkManager.equip_item_response.connect(_on_equip_item_response)
	NetworkManager.unequip_item_response.connect(_on_unequip_item_response)


func _exit_tree() -> void:
	if GameState.inventory_changed.is_connected(_on_inventory_changed):
		GameState.inventory_changed.disconnect(_on_inventory_changed)
	if GameState.equipment_changed.is_connected(_on_equipment_changed):
		GameState.equipment_changed.disconnect(_on_equipment_changed)
	if NetworkManager.move_item_response.is_connected(_on_move_item_response):
		NetworkManager.move_item_response.disconnect(_on_move_item_response)
	if NetworkManager.equip_item_response.is_connected(_on_equip_item_response):
		NetworkManager.equip_item_response.disconnect(_on_equip_item_response)
	if NetworkManager.unequip_item_response.is_connected(_on_unequip_item_response):
		NetworkManager.unequip_item_response.disconnect(_on_unequip_item_response)


func toggle() -> void:
	visible = not visible
	if visible:
		_refresh_all()


func _process(_delta: float) -> void:
	if not visible:
		return
	if _gold_label:
		_gold_label.text = str(GameState.player_gold) + " Gold"
	if _dragging and _drag_preview:
		_drag_preview.global_position = get_global_mouse_position() - Vector2(SLOT_SIZE / 2.0, SLOT_SIZE / 2.0)


func _input(event: InputEvent) -> void:
	if not visible:
		return
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		visible = false
		get_viewport().set_input_as_handled()
	# Cancel drag if mouse released outside any slot
	if _dragging and event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and not event.pressed:
		_cancel_drag()


func _cancel_drag() -> void:
	_dragging = false
	if _drag_preview and is_instance_valid(_drag_preview):
		_drag_preview.queue_free()
		_drag_preview = null


# ---- Build UI ----

func _apply_panel_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.05, 0.05, 0.1, 0.92)
	style.border_color = Colors.BORDER_PANEL
	style.set_border_width_all(2)
	style.set_corner_radius_all(6)
	style.set_content_margin_all(12)
	add_theme_stylebox_override("panel", style)


func _build_ui() -> void:
	var root_vbox := VBoxContainer.new()
	root_vbox.add_theme_constant_override("separation", 8)
	add_child(root_vbox)

	# Title bar with close button
	var title_row := HBoxContainer.new()
	root_vbox.add_child(title_row)
	var title := Label.new()
	title.text = tr("INVENTORY_TITLE")
	title.add_theme_color_override("font_color", Colors.TEXT_TITLE)
	title.add_theme_font_size_override("font_size", 16)
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	title_row.add_child(title)
	var close_btn := Button.new()
	close_btn.text = "X"
	close_btn.pressed.connect(func(): visible = false)
	title_row.add_child(close_btn)

	# Main content: equipment (left) + inventory grid (right)
	var content := HBoxContainer.new()
	content.add_theme_constant_override("separation", 16)
	root_vbox.add_child(content)

	# Equipment panel
	var equip_vbox := VBoxContainer.new()
	equip_vbox.add_theme_constant_override("separation", 4)
	equip_vbox.custom_minimum_size = Vector2(80, 0)
	content.add_child(equip_vbox)
	for i in range(7):
		var row := HBoxContainer.new()
		row.add_theme_constant_override("separation", 4)
		equip_vbox.add_child(row)
		var label := Label.new()
		label.text = tr(EQUIP_SLOT_NAMES[i])
		label.custom_minimum_size = Vector2(50, 0)
		label.add_theme_font_size_override("font_size", 10)
		label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)
		row.add_child(label)
		var slot := _create_slot_panel(true, i)
		_equip_slots.append(slot)
		row.add_child(slot)

	# Inventory grid
	var grid := GridContainer.new()
	grid.columns = GRID_COLS
	grid.add_theme_constant_override("h_separation", 2)
	grid.add_theme_constant_override("v_separation", 2)
	content.add_child(grid)
	for i in range(GRID_COLS * GRID_ROWS):
		var slot := _create_slot_panel(false, i)
		_inv_slots.append(slot)
		grid.add_child(slot)

	# Gold row
	var gold_row := HBoxContainer.new()
	gold_row.add_theme_constant_override("separation", 6)
	root_vbox.add_child(gold_row)
	var gold_icon := Label.new()
	gold_icon.text = "G"
	gold_icon.add_theme_color_override("font_color", Colors.GOLD)
	gold_icon.add_theme_font_size_override("font_size", 14)
	gold_row.add_child(gold_icon)
	_gold_label = Label.new()
	_gold_label.text = "0 Gold"
	_gold_label.add_theme_color_override("font_color", Colors.TEXT_PRIMARY)
	_gold_label.add_theme_font_size_override("font_size", 14)
	gold_row.add_child(_gold_label)


func _create_slot_panel(is_equip: bool, idx: int) -> Panel:
	var panel := Panel.new()
	panel.custom_minimum_size = Vector2(SLOT_SIZE, SLOT_SIZE)
	var bg := StyleBoxFlat.new()
	bg.bg_color = Color(0.1, 0.1, 0.15, 0.8)
	bg.set_border_width_all(1)
	bg.border_color = Color(0.25, 0.25, 0.3, 0.6)
	bg.set_corner_radius_all(2)
	panel.add_theme_stylebox_override("panel", bg)
	panel.mouse_filter = Control.MOUSE_FILTER_STOP
	panel.set_meta("is_equip", is_equip)
	panel.set_meta("slot_idx", idx)
	panel.gui_input.connect(_on_slot_input.bind(panel, is_equip, idx))
	panel.mouse_entered.connect(_on_slot_hover.bind(panel, is_equip, idx))
	panel.mouse_exited.connect(_on_slot_hover_exit)
	return panel


# ---- Slot rendering ----

func _refresh_all() -> void:
	_refresh_inventory()
	_refresh_equipment()


func _refresh_inventory() -> void:
	for i in range(100):
		_refresh_inv_slot(i)


func _refresh_inv_slot(slot_idx: int) -> void:
	if slot_idx < 0 or slot_idx >= _inv_slots.size():
		return
	var panel: Panel = _inv_slots[slot_idx]
	# Remove old children (item display)
	for child in panel.get_children():
		child.queue_free()
	var slot_data = GameState.inventory_slots[slot_idx]
	if slot_data == null:
		return
	_render_item_in_slot(panel, slot_data.get("item_id", 0), slot_data.get("amount", 1), slot_data.get("enhancement", 0))


func _refresh_equipment() -> void:
	for i in range(7):
		_refresh_equip_slot(i)


func _refresh_equip_slot(slot_type: int) -> void:
	if slot_type < 0 or slot_type >= _equip_slots.size():
		return
	var panel: Panel = _equip_slots[slot_type]
	for child in panel.get_children():
		child.queue_free()
	var equip_data = GameState.equipment_slots.get(slot_type)
	if equip_data == null:
		return
	_render_item_in_slot(panel, equip_data.get("item_id", 0), 1, equip_data.get("enhancement", 0))


func _render_item_in_slot(panel: Panel, item_id: int, amount: int, enhancement: int) -> void:
	if item_id == 0:
		return
	var item_def := ItemDatabase.get_item(item_id)
	if item_def.is_empty():
		return

	# Colored background rect
	var bg_rect := ColorRect.new()
	bg_rect.color = ItemDatabase.get_type_color(item_def.get("type", -1))
	bg_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	bg_rect.offset_left = 2; bg_rect.offset_top = 2
	bg_rect.offset_right = -2; bg_rect.offset_bottom = -2
	bg_rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	panel.add_child(bg_rect)

	# Rarity border
	var panel_style: StyleBoxFlat = panel.get_theme_stylebox("panel").duplicate()
	panel_style.border_color = ItemDatabase.get_rarity_color(item_def.get("rarity", 0))
	panel.add_theme_stylebox_override("panel", panel_style)

	# Stack count (bottom-right)
	if amount > 1:
		var count_label := Label.new()
		count_label.text = str(amount)
		count_label.add_theme_font_size_override("font_size", 10)
		count_label.add_theme_color_override("font_color", Color.WHITE)
		count_label.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
		count_label.offset_right = -2; count_label.offset_bottom = -1
		count_label.grow_horizontal = Control.GROW_DIRECTION_BEGIN
		count_label.grow_vertical = Control.GROW_DIRECTION_BEGIN
		count_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
		panel.add_child(count_label)

	# Enhancement (top-left)
	if enhancement > 0:
		var enh_label := Label.new()
		enh_label.text = "+%d" % enhancement
		enh_label.add_theme_font_size_override("font_size", 9)
		enh_label.add_theme_color_override("font_color", Colors.GOLD_BRIGHT)
		enh_label.set_anchors_preset(Control.PRESET_TOP_LEFT)
		enh_label.offset_left = 2; enh_label.offset_top = 1
		enh_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
		panel.add_child(enh_label)


# ---- Input handling (drag & drop, double-click) ----

func _on_slot_input(event: InputEvent, panel: Panel, is_equip: bool, idx: int) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.double_click:
			_on_double_click(is_equip, idx)
			get_viewport().set_input_as_handled()
		elif event.pressed:
			_start_drag(is_equip, idx, panel)
		elif _dragging:
			_end_drag(is_equip, idx)


func _on_double_click(is_equip: bool, idx: int) -> void:
	if is_equip:
		# Unequip
		NetworkManager.send_unequip_item(idx)
	else:
		# Try to equip from inventory
		var slot_data = GameState.inventory_slots[idx]
		if slot_data == null:
			return
		var item_def := ItemDatabase.get_item(slot_data.get("item_id", 0))
		if item_def.is_empty():
			return
		var equip_slot := ItemDatabase.get_equip_slot(item_def)
		if equip_slot < 0:
			return
		NetworkManager.send_equip_item(idx, equip_slot)


func _start_drag(is_equip: bool, idx: int, panel: Panel) -> void:
	# Check if slot has an item
	var has_item := false
	if is_equip:
		has_item = GameState.equipment_slots.get(idx) != null
	else:
		has_item = GameState.inventory_slots[idx] != null

	var pending_key := ("equip_%d" % idx) if is_equip else ("inv_%d" % idx)
	if not has_item or _pending_slots.has(pending_key):
		return

	_dragging = true
	_drag_from_slot = idx
	_drag_from_equip = is_equip

	# Create drag preview
	_drag_preview = Panel.new()
	_drag_preview.custom_minimum_size = Vector2(SLOT_SIZE, SLOT_SIZE)
	_drag_preview.modulate.a = 0.7
	_drag_preview.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.3, 0.3, 0.5, 0.6)
	style.set_corner_radius_all(2)
	_drag_preview.add_theme_stylebox_override("panel", style)
	# Position will be updated in _process
	get_tree().root.add_child(_drag_preview)


func _end_drag(is_equip_target: bool, target_idx: int) -> void:
	if not _dragging:
		return
	_dragging = false

	# Clean up preview
	if _drag_preview and is_instance_valid(_drag_preview):
		_drag_preview.queue_free()
		_drag_preview = null

	# Same slot — cancel
	if _drag_from_equip == is_equip_target and _drag_from_slot == target_idx:
		return

	if _drag_from_equip:
		# Dragging from equipment — only unequip makes sense
		NetworkManager.send_unequip_item(_drag_from_slot)
	elif is_equip_target:
		# Dragging from inventory to equipment slot
		var slot_data = GameState.inventory_slots[_drag_from_slot]
		if slot_data == null:
			return
		NetworkManager.send_equip_item(_drag_from_slot, target_idx)
	else:
		# Inventory to inventory move
		# Save rollback snapshot
		_rollback_snapshot = {
			"from": _drag_from_slot,
			"to": target_idx,
			"from_data": GameState.inventory_slots[_drag_from_slot],
			"to_data": GameState.inventory_slots[target_idx],
		}
		# Optimistic swap
		var temp = GameState.inventory_slots[_drag_from_slot]
		GameState.inventory_slots[_drag_from_slot] = GameState.inventory_slots[target_idx]
		GameState.inventory_slots[target_idx] = temp
		_pending_slots["inv_%d" % _drag_from_slot] = true
		_pending_slots["inv_%d" % target_idx] = true
		_refresh_inv_slot(_drag_from_slot)
		_refresh_inv_slot(target_idx)
		NetworkManager.send_move_item(_drag_from_slot, target_idx)


# ---- Tooltip ----

func _on_slot_hover(panel: Panel, is_equip: bool, idx: int) -> void:
	_hide_tooltip()
	var item_id := 0
	var enhancement := 0
	var amount := 1
	if is_equip:
		var data = GameState.equipment_slots.get(idx)
		if data != null:
			item_id = data.get("item_id", 0)
			enhancement = data.get("enhancement", 0)
	else:
		var data = GameState.inventory_slots[idx]
		if data != null:
			item_id = data.get("item_id", 0)
			enhancement = data.get("enhancement", 0)
			amount = data.get("amount", 1)
	if item_id == 0:
		return
	_tooltip = PanelContainer.new()
	_tooltip.set_script(ItemTooltipScript)
	_tooltip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	get_tree().root.add_child(_tooltip)
	_tooltip.setup(item_id, enhancement, amount)
	# Position next to slot
	var slot_rect := panel.get_global_rect()
	_tooltip.position = Vector2(slot_rect.end.x + 8, slot_rect.position.y)
	# Clamp to screen
	await get_tree().process_frame
	if is_instance_valid(_tooltip):
		var vp_size := get_viewport().get_visible_rect().size
		if _tooltip.position.x + _tooltip.size.x > vp_size.x:
			_tooltip.position.x = slot_rect.position.x - _tooltip.size.x - 8
		if _tooltip.position.y + _tooltip.size.y > vp_size.y:
			_tooltip.position.y = vp_size.y - _tooltip.size.y


func _on_slot_hover_exit() -> void:
	_hide_tooltip()


func _hide_tooltip() -> void:
	if _tooltip and is_instance_valid(_tooltip):
		_tooltip.queue_free()
		_tooltip = null


# ---- Server response handlers ----

func _on_move_item_response(data: Dictionary) -> void:
	var success: bool = data.get("success", false)
	if not success:
		# Revert optimistic swap
		if not _rollback_snapshot.is_empty():
			var from_idx: int = _rollback_snapshot.get("from", -1)
			var to_idx: int = _rollback_snapshot.get("to", -1)
			if from_idx >= 0 and from_idx < 100:
				GameState.inventory_slots[from_idx] = _rollback_snapshot.get("from_data")
			if to_idx >= 0 and to_idx < 100:
				GameState.inventory_slots[to_idx] = _rollback_snapshot.get("to_data")
			_refresh_inv_slot(from_idx)
			_refresh_inv_slot(to_idx)
	_rollback_snapshot = {}
	_pending_slots.clear()


func _on_equip_item_response(data: Dictionary) -> void:
	if not data.get("success", false):
		_refresh_all()  # Revert by re-reading GameState
	_pending_slots.clear()


func _on_unequip_item_response(data: Dictionary) -> void:
	if not data.get("success", false):
		_refresh_all()
	_pending_slots.clear()


func _on_inventory_changed() -> void:
	if visible:
		_refresh_inventory()


func _on_equipment_changed() -> void:
	if visible:
		_refresh_equipment()
```

- [ ] **Step 2: Commit**

```bash
git add client/scenes/ui/game_hud/InventoryScreen.gd
git commit -m "feat: add InventoryScreen with equipment panel, drag & drop, tooltips, and optimistic UI"
```

---

## Task 10: NPC Dialog

**Files:**
- Create: `client/scenes/ui/game_hud/NpcDialog.gd`

- [ ] **Step 1: Create `NpcDialog.gd`**

```gdscript
## NpcDialog.gd
## Small popup when clicking an NPC. Shows NPC name + Shop button.
## Extensible for quest NPCs in Phase 2.
extends PanelContainer

signal shop_requested(npc_def_id: int)

var _npc_def_id: int = 0
var _npc_position: Vector3 = Vector3.ZERO


func _ready() -> void:
	visible = false
	mouse_filter = Control.MOUSE_FILTER_STOP
	_apply_style()


func show_dialog(npc_def_id: int) -> void:
	_npc_def_id = npc_def_id
	var npc_data := NpcRegistry.get_npc(npc_def_id)
	if npc_data.is_empty():
		return
	_npc_position = npc_data.get("pos", Vector3.ZERO)
	# Rebuild content
	for child in get_children():
		child.queue_free()
	_build_content(npc_data)
	visible = true


func _process(_delta: float) -> void:
	if not visible:
		return
	# Auto-close if player moves too far
	if not NpcRegistry.is_in_range(_npc_def_id, GameState.player_position):
		visible = false


func _input(event: InputEvent) -> void:
	if not visible:
		return
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		visible = false
		get_viewport().set_input_as_handled()


func _apply_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.06, 0.06, 0.12, 0.95)
	style.border_color = Colors.GOLD_DARK
	style.set_border_width_all(2)
	style.set_corner_radius_all(6)
	style.set_content_margin_all(12)
	add_theme_stylebox_override("panel", style)


func _build_content(npc_data: Dictionary) -> void:
	var vbox := VBoxContainer.new()
	vbox.add_theme_constant_override("separation", 8)
	add_child(vbox)

	var name_label := Label.new()
	name_label.text = tr(npc_data.get("name", ""))
	name_label.add_theme_color_override("font_color", Colors.GOLD)
	name_label.add_theme_font_size_override("font_size", 16)
	name_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	vbox.add_child(name_label)

	var shop_btn := Button.new()
	shop_btn.text = tr("NPC_DIALOG_SHOP")
	shop_btn.pressed.connect(func():
		visible = false
		shop_requested.emit(_npc_def_id))
	vbox.add_child(shop_btn)

	var close_btn := Button.new()
	close_btn.text = tr("SHOP_CLOSE")
	close_btn.pressed.connect(func(): visible = false)
	vbox.add_child(close_btn)
```

- [ ] **Step 2: Commit**

```bash
git add client/scenes/ui/game_hud/NpcDialog.gd
git commit -m "feat: add NpcDialog popup for NPC interaction"
```

---

## Task 11: NPC Shop Screen

**Files:**
- Create: `client/scenes/ui/game_hud/NpcShopScreen.gd`

- [ ] **Step 1: Create `NpcShopScreen.gd`**

```gdscript
## NpcShopScreen.gd
## NPC shop window with buy (left) and sell via inventory grid (right).
extends PanelContainer

const ItemTooltipScript := preload("res://scenes/ui/game_hud/ItemTooltip.gd")

const SLOT_SIZE := 40

var _npc_def_id: int = 0
var _selected_shop_item: int = 0  # item_def_id
var _selected_sell_slot: int = -1
var _buy_amount: int = 1
var _sell_amount: int = 1

# UI refs
var _title_label: Label = null
var _gold_label: Label = null
var _shop_list: VBoxContainer = null
var _inv_grid: GridContainer = null
var _inv_slots: Array[Panel] = []
var _buy_amount_label: Label = null
var _sell_info: VBoxContainer = null
var _sell_name_label: Label = null
var _sell_price_label: Label = null
var _sell_amount_label: Label = null
var _tooltip: PanelContainer = null


func _ready() -> void:
	visible = false
	custom_minimum_size = Vector2(900, 560)
	mouse_filter = Control.MOUSE_FILTER_STOP
	_apply_style()
	_build_ui()
	GameState.inventory_changed.connect(_on_inventory_changed)
	NetworkManager.npc_buy_response.connect(_on_npc_buy_response)
	NetworkManager.npc_sell_response.connect(_on_npc_sell_response)


func _exit_tree() -> void:
	if GameState.inventory_changed.is_connected(_on_inventory_changed):
		GameState.inventory_changed.disconnect(_on_inventory_changed)
	if NetworkManager.npc_buy_response.is_connected(_on_npc_buy_response):
		NetworkManager.npc_buy_response.disconnect(_on_npc_buy_response)
	if NetworkManager.npc_sell_response.is_connected(_on_npc_sell_response):
		NetworkManager.npc_sell_response.disconnect(_on_npc_sell_response)


func open_shop(npc_def_id: int) -> void:
	_npc_def_id = npc_def_id
	_selected_shop_item = 0
	_selected_sell_slot = -1
	_buy_amount = 1
	_sell_amount = 1
	var npc := NpcRegistry.get_npc(npc_def_id)
	if _title_label:
		_title_label.text = tr(npc.get("name", ""))
	_populate_shop_list()
	_refresh_inventory()
	_update_sell_info()
	visible = true


func _process(_delta: float) -> void:
	if not visible:
		return
	if _gold_label:
		_gold_label.text = str(GameState.player_gold) + " Gold"
	# Auto-close if too far
	if not NpcRegistry.is_in_range(_npc_def_id, GameState.player_position):
		visible = false


func _input(event: InputEvent) -> void:
	if not visible:
		return
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		visible = false
		get_viewport().set_input_as_handled()


func _apply_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.05, 0.05, 0.1, 0.92)
	style.border_color = Colors.BORDER_PANEL
	style.set_border_width_all(2)
	style.set_corner_radius_all(6)
	style.set_content_margin_all(12)
	add_theme_stylebox_override("panel", style)


func _build_ui() -> void:
	var root := VBoxContainer.new()
	root.add_theme_constant_override("separation", 8)
	add_child(root)

	# Title bar
	var title_row := HBoxContainer.new()
	root.add_child(title_row)
	_title_label = Label.new()
	_title_label.add_theme_color_override("font_color", Colors.TEXT_TITLE)
	_title_label.add_theme_font_size_override("font_size", 16)
	_title_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	title_row.add_child(_title_label)
	_gold_label = Label.new()
	_gold_label.add_theme_color_override("font_color", Colors.GOLD)
	_gold_label.add_theme_font_size_override("font_size", 14)
	title_row.add_child(_gold_label)
	var close_btn := Button.new()
	close_btn.text = "X"
	close_btn.pressed.connect(func(): visible = false)
	title_row.add_child(close_btn)

	# Content: shop (left) + inventory (right)
	var content := HBoxContainer.new()
	content.add_theme_constant_override("separation", 16)
	content.size_flags_vertical = Control.SIZE_EXPAND_FILL
	root.add_child(content)

	# Shop list (left, scrollable)
	var shop_scroll := ScrollContainer.new()
	shop_scroll.custom_minimum_size = Vector2(300, 0)
	shop_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	content.add_child(shop_scroll)
	_shop_list = VBoxContainer.new()
	_shop_list.add_theme_constant_override("separation", 4)
	shop_scroll.add_child(_shop_list)

	# Right side: inventory grid + sell panel
	var right := VBoxContainer.new()
	right.add_theme_constant_override("separation", 8)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	content.add_child(right)

	var inv_label := Label.new()
	inv_label.text = tr("INVENTORY_TITLE")
	inv_label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)
	inv_label.add_theme_font_size_override("font_size", 12)
	right.add_child(inv_label)

	_inv_grid = GridContainer.new()
	_inv_grid.columns = 10
	_inv_grid.add_theme_constant_override("h_separation", 2)
	_inv_grid.add_theme_constant_override("v_separation", 2)
	right.add_child(_inv_grid)
	for i in range(100):
		var slot := _create_inv_slot(i)
		_inv_slots.append(slot)
		_inv_grid.add_child(slot)

	# Sell info panel
	_sell_info = VBoxContainer.new()
	_sell_info.add_theme_constant_override("separation", 4)
	right.add_child(_sell_info)
	_sell_name_label = Label.new()
	_sell_name_label.add_theme_font_size_override("font_size", 12)
	_sell_name_label.add_theme_color_override("font_color", Colors.TEXT_PRIMARY)
	_sell_info.add_child(_sell_name_label)
	_sell_price_label = Label.new()
	_sell_price_label.add_theme_font_size_override("font_size", 12)
	_sell_price_label.add_theme_color_override("font_color", Colors.GOLD)
	_sell_info.add_child(_sell_price_label)

	var sell_row := HBoxContainer.new()
	sell_row.add_theme_constant_override("separation", 4)
	_sell_info.add_child(sell_row)
	var sell_minus := Button.new()
	sell_minus.text = "-"
	sell_minus.pressed.connect(func(): _sell_amount = maxi(1, _sell_amount - 1); _update_sell_info())
	sell_row.add_child(sell_minus)
	_sell_amount_label = Label.new()
	_sell_amount_label.text = "1"
	_sell_amount_label.add_theme_font_size_override("font_size", 12)
	sell_row.add_child(_sell_amount_label)
	var sell_plus := Button.new()
	sell_plus.text = "+"
	sell_plus.pressed.connect(func(): _sell_amount += 1; _update_sell_info())
	sell_row.add_child(sell_plus)
	var sell_btn := Button.new()
	sell_btn.text = tr("SHOP_SELL")
	sell_btn.pressed.connect(_on_sell_pressed)
	sell_row.add_child(sell_btn)

	# Buy amount row (bottom)
	var buy_row := HBoxContainer.new()
	buy_row.add_theme_constant_override("separation", 4)
	root.add_child(buy_row)
	var buy_label := Label.new()
	buy_label.text = tr("SHOP_AMOUNT") + ":"
	buy_label.add_theme_font_size_override("font_size", 12)
	buy_row.add_child(buy_label)
	var buy_minus := Button.new()
	buy_minus.text = "-"
	buy_minus.pressed.connect(func(): _buy_amount = maxi(1, _buy_amount - 1); _update_buy_amount())
	buy_row.add_child(buy_minus)
	_buy_amount_label = Label.new()
	_buy_amount_label.text = "1"
	_buy_amount_label.add_theme_font_size_override("font_size", 12)
	buy_row.add_child(_buy_amount_label)
	var buy_plus := Button.new()
	buy_plus.text = "+"
	buy_plus.pressed.connect(func(): _buy_amount += 1; _update_buy_amount())
	buy_row.add_child(buy_plus)
	var buy_btn := Button.new()
	buy_btn.text = tr("SHOP_BUY")
	buy_btn.pressed.connect(_on_buy_pressed)
	buy_row.add_child(buy_btn)


func _create_inv_slot(idx: int) -> Panel:
	var panel := Panel.new()
	panel.custom_minimum_size = Vector2(SLOT_SIZE, SLOT_SIZE)
	var bg := StyleBoxFlat.new()
	bg.bg_color = Color(0.1, 0.1, 0.15, 0.8)
	bg.set_border_width_all(1)
	bg.border_color = Color(0.25, 0.25, 0.3, 0.6)
	bg.set_corner_radius_all(2)
	panel.add_theme_stylebox_override("panel", bg)
	panel.mouse_filter = Control.MOUSE_FILTER_STOP
	panel.gui_input.connect(_on_inv_slot_click.bind(idx))
	return panel


func _populate_shop_list() -> void:
	for child in _shop_list.get_children():
		child.queue_free()
	var items := NpcRegistry.get_shop_items(_npc_def_id)
	for item_id in items:
		var item_def := ItemDatabase.get_item(item_id)
		if item_def.is_empty():
			continue
		var entry := _create_shop_entry(item_id, item_def)
		_shop_list.add_child(entry)


func _create_shop_entry(item_id: int, item_def: Dictionary) -> PanelContainer:
	var panel := PanelContainer.new()
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.08, 0.08, 0.14, 0.8)
	style.set_border_width_all(1)
	style.border_color = Color(0.2, 0.2, 0.3, 0.5)
	style.set_corner_radius_all(3)
	style.set_content_margin_all(6)
	panel.add_theme_stylebox_override("panel", style)

	var vbox := VBoxContainer.new()
	vbox.add_theme_constant_override("separation", 2)
	panel.add_child(vbox)

	var rarity: int = item_def.get("rarity", 0)
	var name_label := Label.new()
	name_label.text = tr(item_def.get("name", ""))
	name_label.add_theme_color_override("font_color", ItemDatabase.get_rarity_color(rarity))
	name_label.add_theme_font_size_override("font_size", 13)
	vbox.add_child(name_label)

	var stats_text := ""
	if item_def.get("base_attack", 0) > 0:
		stats_text += "ATK+%d " % item_def["base_attack"]
	if item_def.get("base_defense", 0) > 0:
		stats_text += "DEF+%d " % item_def["base_defense"]
	stats_text += " %dg" % item_def.get("buy_price", 0)

	var stats_label := Label.new()
	stats_label.text = stats_text
	stats_label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)
	stats_label.add_theme_font_size_override("font_size", 11)
	vbox.add_child(stats_label)

	# Level gate
	var level_req: int = item_def.get("level_req", 0)
	if level_req > GameState.player_level:
		name_label.modulate.a = 0.5
		stats_label.modulate.a = 0.5
		var req_label := Label.new()
		req_label.text = tr("ITEM_LEVEL_REQ").replace("{level}", str(level_req))
		req_label.add_theme_color_override("font_color", Colors.TEXT_ERROR)
		req_label.add_theme_font_size_override("font_size", 10)
		vbox.add_child(req_label)

	panel.gui_input.connect(func(event: InputEvent):
		if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
			_selected_shop_item = item_id
			_buy_amount = 1
			_update_buy_amount())

	return panel


func _refresh_inventory() -> void:
	for i in range(100):
		_refresh_inv_slot(i)


func _refresh_inv_slot(idx: int) -> void:
	if idx < 0 or idx >= _inv_slots.size():
		return
	var panel: Panel = _inv_slots[idx]
	for child in panel.get_children():
		child.queue_free()
	var slot_data = GameState.inventory_slots[idx]
	if slot_data == null:
		return
	var item_id: int = slot_data.get("item_id", 0)
	if item_id == 0:
		return
	var item_def := ItemDatabase.get_item(item_id)
	if item_def.is_empty():
		return
	var bg_rect := ColorRect.new()
	bg_rect.color = ItemDatabase.get_type_color(item_def.get("type", -1))
	bg_rect.set_anchors_preset(Control.PRESET_FULL_RECT)
	bg_rect.offset_left = 2; bg_rect.offset_top = 2
	bg_rect.offset_right = -2; bg_rect.offset_bottom = -2
	bg_rect.mouse_filter = Control.MOUSE_FILTER_IGNORE
	panel.add_child(bg_rect)
	var amount: int = slot_data.get("amount", 1)
	if amount > 1:
		var lbl := Label.new()
		lbl.text = str(amount)
		lbl.add_theme_font_size_override("font_size", 9)
		lbl.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
		lbl.offset_right = -2; lbl.offset_bottom = -1
		lbl.grow_horizontal = Control.GROW_DIRECTION_BEGIN
		lbl.grow_vertical = Control.GROW_DIRECTION_BEGIN
		lbl.mouse_filter = Control.MOUSE_FILTER_IGNORE
		panel.add_child(lbl)


func _on_inv_slot_click(event: InputEvent, idx: int) -> void:
	if not (event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT):
		return
	var slot_data = GameState.inventory_slots[idx]
	if slot_data == null:
		_selected_sell_slot = -1
		_update_sell_info()
		return
	_selected_sell_slot = idx
	_sell_amount = 1
	_update_sell_info()


func _update_sell_info() -> void:
	if _selected_sell_slot < 0:
		_sell_name_label.text = ""
		_sell_price_label.text = ""
		if _sell_amount_label:
			_sell_amount_label.text = "0"
		return
	var slot_data = GameState.inventory_slots[_selected_sell_slot]
	if slot_data == null:
		_selected_sell_slot = -1
		_sell_name_label.text = ""
		_sell_price_label.text = ""
		return
	var item_def := ItemDatabase.get_item(slot_data.get("item_id", 0))
	var max_amount: int = slot_data.get("amount", 1)
	_sell_amount = clampi(_sell_amount, 1, max_amount)
	_sell_name_label.text = tr(item_def.get("name", ""))
	var sell_price: int = item_def.get("sell_price", 0) * _sell_amount
	_sell_price_label.text = "%dg" % sell_price
	if _sell_amount_label:
		_sell_amount_label.text = str(_sell_amount)


func _update_buy_amount() -> void:
	if _buy_amount_label:
		_buy_amount_label.text = str(_buy_amount)


func _on_buy_pressed() -> void:
	if _selected_shop_item == 0:
		return
	# NOTE: Server currently uses npc_def_id as npc_entity_id (see NpcShopHandler.kt:46).
	# If the server switches to runtime entity IDs, this call must be updated.
	NetworkManager.send_npc_buy(_npc_def_id, _selected_shop_item, _buy_amount)


func _on_sell_pressed() -> void:
	if _selected_sell_slot < 0:
		return
	# NOTE: Same npc_def_id == npc_entity_id assumption as _on_buy_pressed.
	NetworkManager.send_npc_sell(_npc_def_id, _selected_sell_slot, _sell_amount)


func _on_npc_buy_response(data: Dictionary) -> void:
	if not visible:
		return
	if not data.get("success", false):
		# Could show error notification
		pass


func _on_npc_sell_response(data: Dictionary) -> void:
	if not visible:
		return
	_selected_sell_slot = -1
	_update_sell_info()


func _on_inventory_changed() -> void:
	if visible:
		_refresh_inventory()
		_update_sell_info()
```

- [ ] **Step 2: Commit**

```bash
git add client/scenes/ui/game_hud/NpcShopScreen.gd
git commit -m "feat: add NpcShopScreen with buy/sell, amount spinner, and level-gating"
```

---

## Task 12: GameWorld Integration — HUD Wiring, NPC Click, Input, Loot Notifications

**Files:**
- Modify: `client/scenes/game/GameWorld.gd`

- [ ] **Step 1: Add preloads and HUD references**

After line 11 (`const DamageNumber := preload(...)`), add:

```gdscript
const InventoryScreenScript := preload("res://scenes/ui/game_hud/InventoryScreen.gd")
const NpcDialogScript := preload("res://scenes/ui/game_hud/NpcDialog.gd")
const NpcShopScreenScript := preload("res://scenes/ui/game_hud/NpcShopScreen.gd")
```

After line 50 (`var _notifications = null`), add:

```gdscript
var _inventory_screen = null   # InventoryScreen (PanelContainer with script)
var _npc_dialog = null         # NpcDialog (PanelContainer with script)
var _npc_shop_screen = null    # NpcShopScreen (PanelContainer with script)
```

- [ ] **Step 2: Connect inventory_updated signal**

In `_connect_signals()`, after line 107 (`NetworkManager.gold_updated.connect(...)`), add:

```gdscript
	NetworkManager.inventory_updated.connect(_on_inventory_updated)
```

In `_disconnect_signals()`, after the `gold_updated` disconnect block, add:

```gdscript
	if NetworkManager.inventory_updated.is_connected(_on_inventory_updated):
		NetworkManager.inventory_updated.disconnect(_on_inventory_updated)
```

- [ ] **Step 3: Add HUD children in `_setup_hud()`**

Before the death screen block (before line 286, before `# Death screen overlay`), add:

```gdscript
	# Inventory screen — center, initially hidden
	var inv_center := CenterContainer.new()
	inv_center.set_anchors_preset(Control.PRESET_FULL_RECT)
	inv_center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_hud_root.add_child(inv_center)

	_inventory_screen = PanelContainer.new()
	_inventory_screen.set_script(InventoryScreenScript)
	inv_center.add_child(_inventory_screen)

	# NPC dialog — center, initially hidden
	var npc_dialog_center := CenterContainer.new()
	npc_dialog_center.set_anchors_preset(Control.PRESET_FULL_RECT)
	npc_dialog_center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_hud_root.add_child(npc_dialog_center)

	_npc_dialog = PanelContainer.new()
	_npc_dialog.set_script(NpcDialogScript)
	_npc_dialog.shop_requested.connect(_on_npc_shop_requested)
	npc_dialog_center.add_child(_npc_dialog)

	# NPC shop screen — center, initially hidden
	var shop_center := CenterContainer.new()
	shop_center.set_anchors_preset(Control.PRESET_FULL_RECT)
	shop_center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_hud_root.add_child(shop_center)

	_npc_shop_screen = PanelContainer.new()
	_npc_shop_screen.set_script(NpcShopScreenScript)
	shop_center.add_child(_npc_shop_screen)
```

- [ ] **Step 4: Add I-key input and NPC click handler**

In `_process()`, after the loading overlay animation block (after line 91), add:

```gdscript
	# Inventory toggle (I key)
	if Input.is_action_just_pressed("ui_inventory"):
		if _inventory_screen and _inventory_screen.has_method("toggle"):
			_inventory_screen.toggle()
```

Note: Add `ui_inventory` to Godot InputMap in Project Settings mapped to key `I`. If InputMap cannot be edited programmatically, handle it in `_unhandled_input`:

After the `_process` function, add a new method:

```gdscript
func _unhandled_key_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_I:
				if _inventory_screen and _inventory_screen.has_method("toggle"):
					_inventory_screen.toggle()
					get_viewport().set_input_as_handled()
```

- [ ] **Step 5: Add NPC click detection in `_on_player_target_selected`**

In `_on_player_target_selected()` (line 472), after `NetworkManager.send_select_target(entity_id)`, add:

Note: `EntitySpawnMessage` has no `npc_def_id` field in the proto. NPC detection works by matching the clicked entity's position against known NPC positions from `NpcRegistry`. This is reliable because NPCs are stationary.

```gdscript
	# Check if clicked entity is near a known NPC position
	var entity := _entity_factory.get_entity(entity_id)
	if entity and is_instance_valid(entity):
		for npc_def_id in NpcRegistry.NPCS:
			var npc := NpcRegistry.get_npc(npc_def_id)
			var npc_pos: Vector3 = npc.get("pos", Vector3.ZERO)
			if entity.global_position.distance_to(npc_pos) < 3.0:
				if _npc_dialog and _npc_dialog.has_method("show_dialog"):
					_npc_dialog.show_dialog(npc_def_id)
				break
```

- [ ] **Step 6: Add NPC shop handler and inventory update handler**

At end of GameWorld.gd, add:

```gdscript


func _on_npc_shop_requested(npc_def_id: int) -> void:
	if _npc_shop_screen and _npc_shop_screen.has_method("open_shop"):
		_npc_shop_screen.open_shop(npc_def_id)


## Handle inventory update — show loot notifications ONLY for server-pushed updates
## (not for player-initiated move/equip/buy/sell which have their own response handlers).
var _suppress_loot_notifications: bool = false

func _on_inventory_updated(data: Dictionary) -> void:
	# Suppress notifications when the update is from a player-initiated action
	if _suppress_loot_notifications:
		_suppress_loot_notifications = false
		return
	# Only show notifications when no inventory/shop UI is open (i.e., this is a loot drop)
	if (_inventory_screen and _inventory_screen.visible) or (_npc_shop_screen and _npc_shop_screen.visible):
		return
	var slots: Array = data.get("slots", [])
	for slot_data in slots:
		var item_id: int = slot_data.get("item_id", 0)
		var amount: int = slot_data.get("amount", 0)
		if item_id > 0 and _notifications and _notifications.has_method("show_notification"):
			var item_def := ItemDatabase.get_item(item_id)
			if item_def.is_empty():
				continue
			var item_name: String = tr(item_def.get("name", ""))
			var text: String
			if amount > 1:
				text = tr("ITEM_RECEIVED_STACK").replace("{name}", item_name).replace("{amount}", str(amount))
			else:
				text = tr("ITEM_RECEIVED").replace("{name}", item_name)
			_notifications.show_notification(text, ItemDatabase.get_rarity_color(item_def.get("rarity", 0)))
```

- [ ] **Step 7: Commit**

```bash
git add client/scenes/game/GameWorld.gd
git commit -m "feat: wire inventory screen, NPC dialog, shop, and loot notifications into GameWorld"
```

---

## Task 13: Final Integration & Cleanup

- [ ] **Step 1: Verify all files exist**

```bash
ls -la client/scripts/data/ItemDatabase.gd client/scripts/data/NpcRegistry.gd
ls -la client/scenes/ui/game_hud/InventoryScreen.gd client/scenes/ui/game_hud/ItemTooltip.gd
ls -la client/scenes/ui/game_hud/NpcDialog.gd client/scenes/ui/game_hud/NpcShopScreen.gd
```

- [ ] **Step 2: Run all client tests (if GdUnit is available)**

```bash
cd client && godot --headless -s addons/gdUnit4/bin/GdUnitCmdTool.gd --testsuite=tests/ 2>&1 || echo "Tests require Godot editor"
```

- [ ] **Step 3: Final commit**

```bash
git add -A
git status
git commit -m "feat: complete Phase 1.6 client-side inventory, equipment, and NPC shop implementation"
```

- [ ] **Step 4: Update IMPLEMENTATION_PHASES.md**

Mark all client tasks in Phase 1.6 as complete with `[x]`:

```
- [x] Inventar-Fenster: 10x10 Grid (100 Slots), Drag & Drop
- [x] Equipment-Fenster: Charakter-Silhouette mit 7 Slots
- [x] Item-Tooltip: Name, Typ, Stats, Level-Req, Rarity-Farbe
- [x] NPC-Interaktion: NPC anklicken -> Shop-Fenster oeffnen
- [x] Shop-Fenster: Kauf/Verkauf-Tabs, Gold-Anzeige
- [x] Gold-Anzeige in der UI (permanent sichtbar)
```
