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
	WindowManager.toggle_window("inventory")


func _process(_delta: float) -> void:
	if not is_visible_in_tree():
		return
	if _gold_label:
		_gold_label.text = str(GameState.player_gold) + " Gold"
	if _dragging and _drag_preview:
		_drag_preview.global_position = get_global_mouse_position() - Vector2(SLOT_SIZE / 2.0, SLOT_SIZE / 2.0)


func _unhandled_input(event: InputEvent) -> void:
	if not is_visible_in_tree():
		return
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


func _reset_slot_border(panel: Panel) -> void:
	var style: StyleBoxFlat = panel.get_theme_stylebox("panel").duplicate()
	style.border_color = Color(0.25, 0.25, 0.3, 0.6)
	style.set_border_width_all(1)
	panel.add_theme_stylebox_override("panel", style)


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
	# Reset border to default neutral color
	_reset_slot_border(panel)
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
	# Reset border to default neutral color
	_reset_slot_border(panel)
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
			get_viewport().set_input_as_handled()


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
	if is_visible_in_tree():
		_refresh_inventory()


func _on_equipment_changed() -> void:
	if is_visible_in_tree():
		_refresh_equipment()
