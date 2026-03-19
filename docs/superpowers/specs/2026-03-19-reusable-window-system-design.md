# Reusable Window System Design

## Overview

A global, reusable window management system for the FlyAgain Godot 4 client. All HUD panels are wrapped in a `GameWindow` node that provides dragging, resizing, minimizing, closing, and z-order management. Features are configurable per window via exported properties, allowing the same system to wrap both free-floating content windows (Inventory, Shop) and fixed HUD elements (PlayerFrame, SkillBar) with features disabled.

## Components

### 1. GameWindow (Control)

A wrapper node that sits between the HUD root and the actual panel content. Each panel is added as a child of GameWindow's internal `ContentContainer`.

#### Exported Properties

```gdscript
@export var window_id: String = ""
@export var window_title: String = ""
@export var draggable: bool = true
@export var resizable: bool = true
@export var minimizable: bool = true
@export var closable: bool = true
@export var min_size: Vector2 = Vector2(200, 150)
@export var max_size: Vector2 = Vector2(800, 600)
@export var default_position: Vector2 = Vector2.ZERO
@export var default_size: Vector2 = Vector2(400, 300)
```

#### Node Hierarchy

```
GameWindow (Control)
  └── VBoxContainer
      ├── TitleBar (HBoxContainer)
      │   ├── DragHandle (Control) — mouse drag moves GameWindow.position
      │   ├── TitleLabel (Label) — displays window_title
      │   ├── MinimizeButton (Button) — optional, controlled by minimizable
      │   └── CloseButton (Button) — optional, controlled by closable
      └── ContentContainer (PanelContainer) — child panel goes here
  └── ResizeHandles — 8 invisible Control nodes at edges/corners
```

#### Behavior

- **Dragging:** `_gui_input` on TitleBar detects `InputEventMouseButton` + `InputEventMouseMotion`. Moves `GameWindow.position` while dragging. Only active when `draggable = true`.
- **Resizing:** `_gui_input` on each ResizeHandle. Changes `GameWindow.size`, clamped between `min_size` and `max_size`. Handles at all 4 edges and 4 corners (8 total). Appropriate cursor shapes on hover. Only active when `resizable = true`.
- **Minimize:** Hides the window, notifies WindowManager to add a Taskbar entry. Only available when `minimizable = true`.
- **Close:** Sets `visible = false`, emits `window_closed`. Only available when `closable = true`.
- **Focus:** Any mouse click within the window emits `window_focused`, triggering z-order update via WindowManager.
- **Content API:** `set_content(node: Control)` adds the node as a child of ContentContainer. `get_content() -> Control` returns it.

#### Signals

```gdscript
signal window_closed(window_id: String)
signal window_minimized(window_id: String)
signal window_focused(window_id: String)
signal window_restored(window_id: String)
```

#### Lifecycle

- `_ready()`: Builds internal hierarchy, applies theme styling, registers with `WindowManager`, loads persisted position/size if available.
- `_exit_tree()`: Unregisters from `WindowManager`.

#### Styling

- TitleBar uses `Colors.gd` constants for consistency with existing theme.
- Panel background and border via `ThemeFactory` or local `StyleBoxFlat`.
- Minimize/Close buttons use small icon-style buttons consistent with the game's visual style.
- When `draggable`, `resizable`, `minimizable`, and `closable` are all `false`, the TitleBar is hidden entirely — the window becomes a transparent wrapper with no visual overhead.

---

### 2. WindowManager (Autoload)

Global singleton registered as an autoload. Manages all GameWindow instances.

#### Responsibilities

- **Registry:** Dictionary of `window_id → GameWindow` references.
- **Z-Order:** Tracks focus order. `bring_to_front()` updates z-index of all registered windows.
- **Taskbar State:** Maintains list of minimized window IDs, emits signal on change.
- **Persistenz:** Saves/loads window layouts via `ConfigFile` to `user://window_layout.cfg`.
- **Boundary Clamping:** Ensures windows cannot be dragged entirely off-screen (at least 50px of titlebar must remain visible).

#### API

```gdscript
# Registry
func register_window(window: GameWindow) -> void
func unregister_window(window: GameWindow) -> void
func get_window(window_id: String) -> GameWindow

# Visibility
func open_window(window_id: String) -> void
func close_window(window_id: String) -> void
func toggle_window(window_id: String) -> void
func minimize_window(window_id: String) -> void
func restore_window(window_id: String) -> void

# Z-Order
func bring_to_front(window_id: String) -> void

# Persistence
func save_layout() -> void
func load_layout() -> void
func reset_layout() -> void

# Taskbar
func get_minimized_windows() -> Array[String]
func get_taskbar_position() -> String   # "top", "bottom", "left", "right"
func set_taskbar_position(pos: String) -> void
```

#### Signals

```gdscript
signal window_registered(window_id: String)
signal window_unregistered(window_id: String)
signal minimized_list_changed
signal taskbar_position_changed(position: String)
```

#### Persistence Format

File: `user://window_layout.cfg`

```ini
[inventory]
position=Vector2(100, 200)
size=Vector2(450, 400)
minimized=false

[shop]
position=Vector2(500, 100)
size=Vector2(400, 350)
minimized=true

[taskbar]
position="bottom"
```

Auto-save: Layout is saved on every move/resize/minimize/close, debounced with a 1-second timer to avoid excessive disk writes.

---

### 3. Taskbar (PanelContainer)

A dynamic UI bar showing buttons for minimized windows.

#### Structure

```
Taskbar (PanelContainer)
  └── BoxContainer (HBox for top/bottom, VBox for left/right)
      ├── TaskbarButton (Button) — one per minimized window
      ├── TaskbarButton (Button)
      └── ...
```

#### Behavior

- Listens to `WindowManager.minimized_list_changed` — rebuilds button list dynamically.
- Listens to `WindowManager.taskbar_position_changed` — switches anchor preset and box orientation.
- Each button displays the `window_title` of the minimized window.
- Click on button calls `WindowManager.restore_window(window_id)`.
- Auto-hides when no windows are minimized.
- Styling consistent with Colors.gd and ThemeFactory.

#### Positioning

| Setting | Anchor Preset | Container | Placement |
|---------|---------------|-----------|-----------|
| `"bottom"` | `PRESET_BOTTOM_WIDE` | HBoxContainer | Above SkillBar |
| `"top"` | `PRESET_TOP_WIDE` | HBoxContainer | Below PlayerFrame area |
| `"left"` | `PRESET_LEFT_WIDE` | VBoxContainer | Left edge |
| `"right"` | `PRESET_RIGHT_WIDE` | VBoxContainer | Right edge |

Default: `"bottom"`.

The Taskbar itself is **not draggable**. Position is changed only via `WindowManager.set_taskbar_position()`.

---

### 4. Integration Strategy

#### GameWorld._setup_hud() Changes

Existing panels are wrapped in GameWindow instances instead of CenterContainer/MarginContainer.

**Before (current):**
```gdscript
var inv_center := CenterContainer.new()
inv_center.set_anchors_preset(Control.PRESET_CENTER)
_hud_root.add_child(inv_center)
_inventory_screen = PanelContainer.new()
_inventory_screen.set_script(InventoryScreenScript)
inv_center.add_child(_inventory_screen)
```

**After (new):**
```gdscript
var inv_window := GameWindow.new()
inv_window.window_id = "inventory"
inv_window.window_title = tr("WINDOW_INVENTORY")
inv_window.default_position = Vector2(100, 100)
inv_window.default_size = Vector2(450, 500)
inv_window.min_size = Vector2(350, 400)
_hud_root.add_child(inv_window)

_inventory_screen = PanelContainer.new()
_inventory_screen.set_script(InventoryScreenScript)
inv_window.set_content(_inventory_screen)
```

#### Panel Script Changes

**None.** Existing panel scripts (InventoryScreen.gd, NpcShopScreen.gd, etc.) remain unchanged. They are unaware of the window system — they just render their content inside whatever parent they're given.

#### Fixed HUD Elements

Panels like PlayerFrame and SkillBar use GameWindow with features disabled:

```gdscript
var player_window := GameWindow.new()
player_window.window_id = "player_frame"
player_window.window_title = tr("WINDOW_PLAYER")
player_window.draggable = false
player_window.resizable = false
player_window.minimizable = false
player_window.closable = false
_hud_root.add_child(player_window)
```

When all features are disabled, the TitleBar is hidden — zero visual overhead.

#### Input Handling Changes

- Hotkeys (I for inventory, etc.) in `GameWorld._unhandled_key_input()` change from `_inventory_screen.toggle()` to `WindowManager.toggle_window("inventory")`.
- ESC handling remains in panels but GameWindow also handles it for consistency.

#### Taskbar Placement

The Taskbar is created once in `_setup_hud()` and added to `_hud_root`. It auto-manages its position based on the WindowManager setting.

---

### 5. Localization

New translation keys added to `translations.en.translation` and `translations.de.translation`:

| Key | EN | DE |
|-----|----|----|
| `WINDOW_INVENTORY` | Inventory | Inventar |
| `WINDOW_SHOP` | Shop | Laden |
| `WINDOW_NPC_DIALOG` | Dialog | Dialog |
| `WINDOW_PLAYER` | Player | Spieler |
| `WINDOW_TARGET` | Target | Ziel |
| `WINDOW_SKILLS` | Skills | Fähigkeiten |
| `WINDOW_MINIMIZE` | Minimize | Minimieren |
| `WINDOW_CLOSE` | Close | Schließen |
| `WINDOW_RESTORE` | Restore | Wiederherstellen |

---

### 6. File Structure

New files:
```
client/scenes/ui/window_system/GameWindow.gd       # Wrapper Control
client/autoloads/WindowManager.gd                   # Autoload singleton
client/scenes/ui/window_system/Taskbar.gd           # Taskbar UI
```

Modified files:
```
client/scenes/game/GameWorld.gd                     # _setup_hud() refactored
client/translations/translations.en.translation     # New keys
client/translations/translations.de.translation     # New keys
project.godot                                       # WindowManager autoload registration
```

---

### 7. Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Window approach | Wrapper node | Panels stay unchanged, clean separation of concerns |
| Minimize style | Taskbar | Professional, standard UX pattern |
| Taskbar position | Configurable (4 sides) | User preference, default bottom |
| Snapping | None | Keep initial implementation simple, add later if needed |
| Resize | Free at all edges/corners | Standard expectation, with min/max bounds |
| Z-Order | Click-to-front | Intuitive, no pinning needed |
| Persistence | Local ConfigFile | Simple, no server traffic, multi-device later if needed |
| Fixed HUD elements | Same system, features disabled | Uniform architecture, easy to enable features later |
