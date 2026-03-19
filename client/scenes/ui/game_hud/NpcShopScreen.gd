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
	WindowManager.open_window("npc_shop")


func _process(_delta: float) -> void:
	if not visible:
		return
	if _gold_label:
		_gold_label.text = str(GameState.player_gold) + " Gold"
	# Auto-close if too far
	if not NpcRegistry.is_in_range(_npc_def_id, GameState.player_position):
		WindowManager.close_window("npc_shop")


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

	# Title and gold display
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
