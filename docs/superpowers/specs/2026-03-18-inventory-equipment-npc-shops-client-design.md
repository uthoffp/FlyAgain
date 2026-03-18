# Phase 1.6 Client-Side: Inventory, Equipment & NPC-Shops

> Design spec for the Godot 4 (GDScript) client implementation.
> Server-side handlers, proto definitions, and DB migrations are already complete.

---

## 1. Scope

Implement the client-side inventory, equipment, and NPC-shop systems for Phase 1.6. The server is fully implemented and tested (MoveItemHandler, EquipItemHandler, NpcShopHandler, InventoryGrpcService, DB migrations V3-V15). This spec covers only the Godot client work.

### Out of Scope
- Server changes (already complete)
- Ground loot pickup (MVP sends loot directly to inventory)
- Consumable usage (Phase 2)
- Item crafting, trading, enhancement (Phase 2+)

---

## 2. Data Model & State

### 2.1 GameState Extensions

```gdscript
# Inventory: 100 slots (index = slot number)
# Each entry: null (empty) or { item_id: int, amount: int, enhancement: int }
var inventory_slots: Array = []  # initialized to 100x null

# Equipment: 7 slots keyed by slot_type
# 0=head, 1=chest, 2=legs, 3=feet, 4=hands, 5=back, 6=weapon
# Each entry: null or { item_id: int, enhancement: int }
var equipment_slots: Dictionary = {}

# Signals
signal inventory_changed
signal equipment_changed
```

- `reset()` clears both to empty state
- Delta-merge: on `InventoryUpdate` from server, only overwrite the slots included in the message (item_id=0 means slot cleared)

### 2.2 Item Definitions (Hardcoded)

New file: `client/scripts/data/ItemDatabase.gd`

Hardcoded dictionary matching DB seed V11 (8 items). Fields per item:
- `name: String` (EN — localization keys for UI display)
- `type: int` (0=weapon, 1=armor, 2=quest, 3=consumable)
- `subtype: int`
- `level_req: int`
- `class_req: int` (0=any, 1=warrior, 2=mage, 3=assassin, 4=cleric)
- `rarity: int` (0=common, 1=uncommon, 2=rare, 3=epic)
- `base_attack, base_defense, base_hp, base_mp: int`
- `buy_price, sell_price: int`
- `stackable: bool`, `max_stack: int`
- `description: String` (localization key)

Provides `get_item(item_id) -> Dictionary` lookup.

> **Sync note:** This file mirrors DB migration V11. Any server-side item changes require a manual client update. A future phase may introduce dynamic item definition loading.

### 2.3 NPC Registry (Hardcoded)

New file: `client/scripts/data/NpcRegistry.gd`

Hardcoded data for 3 Aerheim NPCs matching DB seed V15:
- Weapon Merchant (npc_def_id=1, pos 505/0/495, sells items 1-3)
- Armor Merchant (npc_def_id=2, pos 510/0/495, sells items 4-6)
- Potion Merchant (npc_def_id=3, pos 515/0/495, sells items 7-8)

Fields: `name_key: String` (EN+DE), `shop_items: Array[int]` (item_def_ids).

**NPC ID Resolution:** The server's `NpcShopHandler` uses NPC definition IDs (not runtime entity IDs) in buy/sell requests. When an NPC entity spawns via `EntitySpawnMessage`, the client identifies it as an NPC by `entity_type`. The NPC registry maps NPC definition IDs to their data. The `EntitySpawnMessage` for NPCs includes the NPC definition ID, which the client stores per-entity to use in shop requests.

Provides `get_npc(npc_def_id) -> Dictionary` and `get_shop_items(npc_def_id) -> Array`.

---

## 3. Network Layer

### 3.1 Proto Encoder (5 new methods)

| Method | Fields | Proto Field Numbers |
|--------|--------|-------------------|
| `encode_move_item_request(from_slot, to_slot)` | from_slot:1, to_slot:2 | int32, int32 |
| `encode_equip_item_request(inventory_slot, equip_slot_type)` | inventory_slot:1, equip_slot_type:2 | int32, int32 |
| `encode_unequip_item_request(equip_slot_type)` | equip_slot_type:1 | int32 |
| `encode_npc_buy_request(npc_entity_id, item_def_id, amount)` | npc_entity_id:1, item_def_id:2, amount:3 | **int64**, int32, int32 |
| `encode_npc_sell_request(npc_entity_id, inventory_slot, amount)` | npc_entity_id:1, inventory_slot:2, amount:3 | **int64**, int32, int32 |

> **Note:** `npc_entity_id` is `int64` in the proto definition, matching entity IDs across the codebase. The encoder must use `_int64_field()` for field 1 in both NPC requests.

### 3.2 Proto Decoder (6 new methods)

| Method | Returns |
|--------|---------|
| `decode_move_item_response()` | `{ success: bool, error_message: String }` |
| `decode_inventory_update()` | `{ slots: [{ slot, item_id, amount, enhancement }], equipment: [{ slot_type, item_id, enhancement }] }` |
| `decode_equip_item_response()` | `{ success: bool, error_message: String }` |
| `decode_unequip_item_response()` | `{ success: bool, error_message: String }` |
| `decode_npc_buy_response()` | `{ success: bool, new_gold: int, assigned_slot: int, error_message: String }` |
| `decode_npc_sell_response()` | `{ success: bool, new_gold: int, error_message: String }` |

### 3.3 NetworkManager Extensions

**New signals:**
- `inventory_updated(slots: Array, equipment: Array)`
- `move_item_response(success: bool, error_message: String)`
- `equip_item_response(success: bool, error_message: String)`
- `unequip_item_response(success: bool, error_message: String)`
- `npc_buy_response(success: bool, new_gold: int, assigned_slot: int, error_message: String)`
- `npc_sell_response(success: bool, new_gold: int, error_message: String)`

**New send methods** (1:1 to encoders): `send_move_item()`, `send_equip_item()`, `send_unequip_item()`, `send_npc_buy()`, `send_npc_sell()`

**Packet dispatch** in `_dispatch_world_frame()`: 6 new match-cases for opcodes 0x0401-0x0406 (0x0407 already handled).

> **Critical:** `INVENTORY_UPDATE` (0x0402) is the most important opcode to dispatch. It is the primary vehicle for all inventory state synchronization — the server sends it after every successful move/equip/unequip/buy/sell/loot operation. Without dispatching 0x0402, none of the delta-merge logic will function.

**State update logic:** When `inventory_updated` fires, GameState delta-merges the changed slots and emits `inventory_changed` / `equipment_changed`.

### 3.4 Initial Inventory Load on World Entry

When the player enters the world (after `EnterWorldResponse`), the server sends an `InventoryUpdate` (0x0402) with the full inventory snapshot (all occupied slots + all equipped items) plus a `GoldUpdate` (0x0407) with the player's gold balance.

- `GameState.inventory_slots` is initialized to 100x `null` in `reset()` before zone entry
- The first `InventoryUpdate` after zone entry populates all occupied slots
- If the player opens inventory before the snapshot arrives, the UI shows empty slots (graceful)
- `GameState.player_gold` is set via the initial `GoldUpdate`

### 3.5 PacketProtocol

The `opcode_name()` function already includes all inventory opcodes — no changes needed.

---

## 4. UI Architecture

### 4.1 Inventory Screen (`InventoryScreen.gd` + `.tscn`)

**Opening:** Input action `I` toggles the screen. `ESC` closes it. Non-modal — player can move while open.

**Layout (~800x600px PanelContainer, centered):**
```
┌─────────────────────────────────────────────┐
│  [X]              INVENTORY                 │
├──────────────┬──────────────────────────────┤
│              │                              │
│   [Head]     │   ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐ │
│   [Chest]    │   │01│02│03│04│05│06│07│08│09│10│ │
│   [Hands]    │   ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤ │
│   [Legs]     │   │11│12│13│..│  │  │  │  │  │  │ │
│   [Feet]     │   ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤ │
│   [Back]     │   │  │  │  │  │  │  │  │  │  │  │ │
│   [Weapon]   │   │  ...  10x10 Grid  ...     │ │
│              │   │  │  │  │  │  │  │  │  │  │  │ │
│              │   └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘ │
│              │                                    │
├──────────────┴──────────────────────────────┤
│  💰 12,345 Gold                             │
└─────────────────────────────────────────────┘
```

**Equipment Panel (left):** 7 slots arranged vertically with labels. Each slot shows equipped item or empty placeholder.

**Inventory Grid (right):** 10x10 `GridContainer`, 100 `Panel` children. Each slot:
- Empty: dark background (`Colors.BG_DARK`)
- Filled: colored rectangle by item type (weapon=red, armor=blue, consumable=green)
- Rarity border: Common=white, Uncommon=green, Rare=blue, Epic=purple
- Stack count: small label bottom-right (only if amount > 1)
- Enhancement: "+X" label top-left (only if > 0)

**Gold display:** Bottom bar with gold icon + formatted amount.

### 4.2 Drag & Drop

- **Start:** Left-click hold on filled inventory slot → create semi-transparent drag preview following cursor
- **Drop on inventory slot:** `send_move_item(from, to)` — optimistic: visually swap immediately, revert on server error
- **Drop on equipment slot:** `send_equip_item(inventory_slot, equip_slot_type)` — only if item type matches slot
- **Drop outside/invalid:** cancel, item snaps back
- **Rollback state:** Before any optimistic swap, save a snapshot of the affected slots (`{ from_slot: data, to_slot: data }`). On server error, restore from snapshot.
- **Server error:** restore snapshot → UI revert → show error via NotificationStack
- **Pending state:** While awaiting server response, block further drag operations on the affected slots to prevent race conditions

### 4.3 Double-Click Equip/Unequip

- **Double-click on equipable inventory item:** auto-determine correct equipment slot from item type, call `send_equip_item()`
- **Double-click on equipment slot:** call `send_unequip_item(slot_type)`
- Uses same optimistic UI + rollback pattern as drag & drop

**Auto-equip slot mapping** (item subtype → equipment slot):
- `type=0` (weapon): always → slot 6 (weapon)
- `type=1` (armor): subtype determines slot: `0=head, 1=chest, 2=legs, 3=feet, 4=hands, 5=back`
- `type=3` (consumable): not equipable — double-click does nothing (consumable usage is Phase 2)
- `type=2` (quest): not equipable

### 4.4 Item Tooltip (`ItemTooltip.gd` + `.tscn`)

Hover over any filled slot (inventory or equipment) shows tooltip panel adjacent to slot.

Content:
```
[Item Name]          (colored by rarity)
[Type: Weapon/Armor/Consumable]
─────────────────
ATK +12              (only if > 0)
DEF +8               (only if > 0)
HP +50               (only if > 0)
MP +30               (only if > 0)
─────────────────
Level Req: 5
Class: Warrior       (or "Any")
─────────────────
Buy: 100g  Sell: 50g
─────────────────
[Description text]
```

Tooltip positions itself to avoid screen edges. Disappears on mouse-out.

### 4.5 NPC Dialog (`NpcDialog.gd` + `.tscn`)

- **Trigger:** Left-click on NPC entity in game world (detected by `entity_type` filtering — only entities marked as NPC trigger the dialog, not players or monsters)
- **Appearance:** Small popup near NPC (or screen center): NPC name + "Shop" button
- **Proximity:** Only opens if player is within 10 units of NPC. Auto-closes if player moves away.
- **Extensible:** Button list can grow for quest NPCs in Phase 2

### 4.6 NPC Shop Screen (`NpcShopScreen.gd` + `.tscn`)

**Layout (~900x600px PanelContainer, centered):**
```
┌─────────────────────────────────────────────────┐
│  [X]     WEAPON MERCHANT        💰 12,345 Gold  │
├────────────────────┬────────────────────────────┤
│  SHOP              │  YOUR INVENTORY            │
│                    │                            │
│  ┌──────────────┐  │  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐│
│  │ Wooden Sword │  │  │  │  │  │  │  │  │  │  │  │  ││
│  │ ATK+5  10g   │  │  ├──┼──┼──┼──┼──┼──┼──┼──┼──┼──┤│
│  │ [Buy]        │  │  │  │  │  │  │  │  │  │  │  │  ││
│  ├──────────────┤  │  │  ...  10x10 Grid  ...    ││
│  │ Iron Sword   │  │  │  │  │  │  │  │  │  │  │  │  ││
│  │ ATK+12 100g  │  │  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘│
│  │ [Buy]        │  │                            │
│  ├──────────────┤  │  Selected: Iron Sword      │
│  │ Steel Sword  │  │  Sell Price: 50g           │
│  │ ATK+22 500g  │  │  Amount: [1] [-][+]        │
│  │ [Buy]        │  │  [Sell]                    │
│  └──────────────┘  │                            │
├────────────────────┴────────────────────────────┤
│  Buy Amount: [1] [-][+]     [Buy Selected]      │
└─────────────────────────────────────────────────┘
```

- **Shop list (left):** Scrollable list of NPC's items. Each entry: item name, key stats, price, buy button.
- **Player inventory (right):** Same 10x10 grid. Click on item → shows sell info below.
- **Buy:** Select shop item → set amount (spinner, 1-max_stack for stackables) → "Buy" → `send_npc_buy()`
- **Sell:** Click inventory item → shows sell price → set amount → "Sell" → `send_npc_sell()`
- **Level-gated items:** Items above player level shown grayed out with level requirement visible
- **Gold:** Updated in real-time via GoldUpdate responses
- **Proximity:** Auto-closes if player moves > 10 units from NPC

### 4.7 Gold in HUD

Add permanent gold display to `PlayerFrame.gd` — small gold icon + formatted number, updating via existing `gold_updated` signal.

---

## 5. Localization

All user-facing strings use localization keys with EN + DE translations:

- Slot labels: "Head", "Chest", etc. / "Kopf", "Brust", etc.
- Button labels: "Buy", "Sell", "Shop", "Close" / "Kaufen", "Verkaufen", "Shop", "Schließen"
- Item type labels: "Weapon", "Armor", "Consumable" / "Waffe", "Rüstung", "Verbrauchsgegenstand"
- Class names: "Warrior", "Mage", "Assassin", "Cleric" / "Krieger", "Magier", "Assassine", "Kleriker"
- Error messages: "Not enough gold", "Inventory full", etc. / "Nicht genug Gold", "Inventar voll", etc.
- Item names and descriptions: EN + DE per item

Uses the same localization pattern as existing UI (InputValidator.gd, existing screens).

---

## 6. Input Handling

| Action | Key/Mouse | Context |
|--------|-----------|---------|
| Toggle inventory | `I` | Game world, closes if open |
| Close any window | `ESC` | Closes topmost window first (shop > inventory > dialog) |
| Drag item | Left-click hold | On filled inventory slot |
| Drop item | Release left-click | On target slot |
| Equip item | Double-click | On equipable inventory item |
| Unequip item | Double-click | On equipment slot |
| NPC interact | Left-click | On NPC entity in world |
| Buy item | Left-click | On "Buy" button in shop |
| Sell item | Left-click | On "Sell" button in shop |

Inventory keybind does NOT interfere with movement (WASD continues to work while inventory is open).

---

## 7. Data Flow

### Move Item
```
Player drags item A→B → optimistic swap in UI
→ NetworkManager.send_move_item(A, B)
→ Server validates → MoveItemResponse(success)
  → success: InventoryUpdate delta → GameState merge → UI confirm
  → fail: GameState revert → UI revert → NotificationStack error
```

### Equip Item
```
Player double-clicks item → optimistic move to equipment slot
→ NetworkManager.send_equip_item(slot, equip_type)
→ Server validates (level, class, type) → EquipItemResponse
  → success: InventoryUpdate delta → GameState merge → UI confirm
  → fail: revert → error notification
```

### NPC Buy
```
Player clicks Buy → NetworkManager.send_npc_buy(npc_id, item_id, amount)
→ Server validates (proximity, gold, level) → NpcBuyResponse
  → success: InventoryUpdate + GoldUpdate → GameState merge → UI refresh
  → fail: error notification
```

### NPC Sell
```
Player clicks Sell → NetworkManager.send_npc_sell(npc_id, slot, amount)
→ Server validates (proximity, item exists, not quest) → NpcSellResponse
  → success: InventoryUpdate + GoldUpdate → GameState merge → UI refresh
  → fail: error notification
```

### Loot from Monster Kill
```
Server: monster dies → LootSystem rolls → addItem → InventoryUpdate + GoldUpdate
→ Client: GameState merge → NotificationStack shows loot notification
→ If InventoryScreen open: UI auto-refreshes via inventory_changed signal
```

**Loot notification format:** "Received: {item_name}" or "Received: {item_name} x{amount}" for stacks. Uses the existing NotificationStack with item rarity color.

---

## 8. File Changes Summary

### New Files
| File | Description |
|------|-------------|
| `client/scripts/data/ItemDatabase.gd` | Hardcoded item definitions (8 items) |
| `client/scripts/data/NpcRegistry.gd` | Hardcoded NPC + shop data (3 NPCs) |
| `client/scenes/ui/game_hud/InventoryScreen.gd` + `.tscn` | Combined inventory + equipment window |
| `client/scenes/ui/game_hud/ItemTooltip.gd` + `.tscn` | Hover tooltip for items |
| `client/scenes/ui/game_hud/NpcDialog.gd` + `.tscn` | NPC interaction popup |
| `client/scenes/ui/game_hud/NpcShopScreen.gd` + `.tscn` | Shop buy/sell window |

### Modified Files
| File | Changes |
|------|---------|
| `client/autoloads/GameState.gd` | Add inventory_slots, equipment_slots, signals, delta-merge logic |
| `client/autoloads/NetworkManager.gd` | Add 6 signals, 5 send methods, 6 dispatch cases |
| `client/scripts/proto/ProtoEncoder.gd` | Add 5 encode methods |
| `client/scripts/proto/ProtoDecoder.gd` | Add 6 decode methods |
| `client/scenes/ui/game_hud/PlayerFrame.gd` + `.tscn` | Add permanent gold display |
| `client/scenes/game/GameWorld.gd` (or equivalent) | Add I-key input, NPC click detection |
