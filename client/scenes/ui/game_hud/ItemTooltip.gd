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
