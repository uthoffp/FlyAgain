## PlayerFrame.gd
## Displays the player's level, name, HP bar, MP bar, and XP % bar.
## Reads from GameState.player_* every frame. Positioned top-left.
extends PanelContainer


var _name_label: Label = null
var _level_label: Label = null
var _zone_label: Label = null
var _hp_bar: ProgressBar = null
var _hp_text: Label = null
var _mp_bar: ProgressBar = null
var _mp_text: Label = null
var _xp_bar: ProgressBar = null
var _xp_text: Label = null


func _ready() -> void:
	custom_minimum_size = Vector2(260, 0)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_apply_panel_style()
	_build_ui()


func _process(_delta: float) -> void:
	# Level + name
	if _level_label:
		_level_label.text = "Lv.%d" % GameState.player_level
	if _name_label:
		_name_label.text = _get_character_name()
	# Zone
	if _zone_label:
		_zone_label.text = WorldConstants.get_zone_name(GameState.current_zone_id)
	# HP
	if _hp_bar:
		_hp_bar.max_value = GameState.player_max_hp
		_hp_bar.value = GameState.player_hp
	if _hp_text:
		_hp_text.text = "%d / %d" % [GameState.player_hp, GameState.player_max_hp]
	# MP
	if _mp_bar:
		_mp_bar.max_value = GameState.player_max_mp
		_mp_bar.value = GameState.player_mp
	if _mp_text:
		_mp_text.text = "%d / %d" % [GameState.player_mp, GameState.player_max_mp]
	# XP
	if _xp_bar:
		_xp_bar.max_value = GameState.player_xp_to_next_level
		_xp_bar.value = GameState.player_xp
	if _xp_text:
		var xp_pct := 0.0
		if GameState.player_xp_to_next_level > 0:
			xp_pct = (float(GameState.player_xp) / float(GameState.player_xp_to_next_level)) * 100.0
		_xp_text.text = "%.1f%%" % xp_pct


func _get_character_name() -> String:
	for c in GameState.characters:
		if c.get("id", "") == GameState.selected_character_id:
			return c.get("name", "")
	return ""


func _apply_panel_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.05, 0.05, 0.1, 0.85)
	style.border_color = Color(0.3, 0.3, 0.4, 0.6)
	style.set_border_width_all(1)
	style.set_corner_radius_all(4)
	style.set_content_margin_all(8)
	add_theme_stylebox_override("panel", style)


func _build_ui() -> void:
	var vbox := VBoxContainer.new()
	vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_theme_constant_override("separation", 4)
	add_child(vbox)

	# Top row: level + character name
	var top_row := HBoxContainer.new()
	top_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_child(top_row)

	_level_label = Label.new()
	_level_label.text = ""
	_level_label.add_theme_color_override("font_color", Colors.GOLD)
	_level_label.add_theme_font_size_override("font_size", 15)
	_level_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top_row.add_child(_level_label)

	var spacer := Control.new()
	spacer.custom_minimum_size = Vector2(8, 0)
	spacer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top_row.add_child(spacer)

	_name_label = Label.new()
	_name_label.text = ""
	_name_label.add_theme_color_override("font_color", Colors.TEXT_PRIMARY)
	_name_label.add_theme_font_size_override("font_size", 15)
	_name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top_row.add_child(_name_label)

	# Zone name label
	_zone_label = Label.new()
	_zone_label.text = ""
	_zone_label.add_theme_color_override("font_color", Color(0.6, 0.65, 0.7, 0.9))
	_zone_label.add_theme_font_size_override("font_size", 11)
	_zone_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_child(_zone_label)

	# HP bar
	_build_stat_bar(vbox, "hp")
	# MP bar
	_build_stat_bar(vbox, "mp")
	# XP bar (thinner)
	_build_stat_bar(vbox, "xp")


func _build_stat_bar(parent: VBoxContainer, stat: String) -> void:
	var bar_height := 18 if stat == "xp" else 22
	var bar_container := Control.new()
	bar_container.custom_minimum_size = Vector2(0, bar_height)
	bar_container.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar_container.mouse_filter = Control.MOUSE_FILTER_IGNORE
	parent.add_child(bar_container)

	var bar := ProgressBar.new()
	bar.max_value = 100
	bar.value = 0
	bar.show_percentage = false
	bar.set_anchors_preset(Control.PRESET_FULL_RECT)
	bar.mouse_filter = Control.MOUSE_FILTER_IGNORE

	# Background style
	var bar_bg := StyleBoxFlat.new()
	bar_bg.set_corner_radius_all(2)

	# Fill style
	var bar_fill := StyleBoxFlat.new()
	bar_fill.set_corner_radius_all(2)

	match stat:
		"hp":
			bar_bg.bg_color = Color(0.08, 0.15, 0.08, 0.9)
			bar_fill.bg_color = Color(0.2, 0.7, 0.25, 0.95)
			_hp_bar = bar
		"mp":
			bar_bg.bg_color = Color(0.08, 0.08, 0.18, 0.9)
			bar_fill.bg_color = Color(0.2, 0.35, 0.85, 0.95)
			_mp_bar = bar
		"xp":
			bar_bg.bg_color = Color(0.12, 0.1, 0.05, 0.9)
			bar_fill.bg_color = Color(Colors.GOLD.r, Colors.GOLD.g, Colors.GOLD.b, 0.95)
			_xp_bar = bar

	bar.add_theme_stylebox_override("background", bar_bg)
	bar.add_theme_stylebox_override("fill", bar_fill)
	bar_container.add_child(bar)

	# Overlaid text label
	var text := Label.new()
	text.text = ""
	text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	text.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	text.set_anchors_preset(Control.PRESET_FULL_RECT)
	text.add_theme_font_size_override("font_size", 11 if stat == "xp" else 12)
	text.add_theme_color_override("font_color", Color(1.0, 1.0, 1.0, 0.9))
	text.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bar_container.add_child(text)

	match stat:
		"hp":
			_hp_text = text
		"mp":
			_mp_text = text
		"xp":
			_xp_text = text
