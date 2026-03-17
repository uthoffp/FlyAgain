## TargetFrame.gd
## Displays the selected target's name, level, and HP bar.
## Reads from GameState.selected_target_* every frame and auto-hides
## when no target is selected.
extends PanelContainer


var _name_label: Label = null
var _level_label: Label = null
var _hp_bar: ProgressBar = null
var _hp_text: Label = null


func _ready() -> void:
	custom_minimum_size = Vector2(280, 60)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false
	_apply_panel_style()
	_build_ui()


func _process(_delta: float) -> void:
	if GameState.selected_target_id == 0:
		visible = false
		return
	visible = true
	if _name_label:
		_name_label.text = GameState.selected_target_name
	if _level_label:
		_level_label.text = "Lv.%d" % GameState.selected_target_level
	if _hp_bar:
		_hp_bar.max_value = GameState.selected_target_max_hp
		_hp_bar.value = GameState.selected_target_hp
	if _hp_text:
		_hp_text.text = "%d / %d" % [GameState.selected_target_hp, GameState.selected_target_max_hp]


## Apply a dark translucent panel style with a subtle border.
func _apply_panel_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.05, 0.05, 0.1, 0.85)
	style.border_color = Color(0.3, 0.3, 0.4, 0.6)
	style.set_border_width_all(1)
	style.set_corner_radius_all(4)
	style.set_content_margin_all(8)
	add_theme_stylebox_override("panel", style)


## Build the UI hierarchy programmatically.
func _build_ui() -> void:
	var vbox := VBoxContainer.new()
	vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(vbox)

	# Top row: name + level
	var top_row := HBoxContainer.new()
	top_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_child(top_row)

	_name_label = Label.new()
	_name_label.text = ""
	_name_label.add_theme_color_override("font_color", Color(1.0, 0.35, 0.35))
	_name_label.add_theme_font_size_override("font_size", 15)
	_name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top_row.add_child(_name_label)

	_level_label = Label.new()
	_level_label.text = ""
	_level_label.add_theme_color_override("font_color", Color(0.65, 0.65, 0.7))
	_level_label.add_theme_font_size_override("font_size", 13)
	_level_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	_level_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top_row.add_child(_level_label)

	# HP bar with overlaid text
	var bar_container := Control.new()
	bar_container.custom_minimum_size = Vector2(0, 22)
	bar_container.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar_container.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_child(bar_container)

	_hp_bar = ProgressBar.new()
	_hp_bar.max_value = 100
	_hp_bar.value = 0
	_hp_bar.show_percentage = false
	_hp_bar.set_anchors_preset(Control.PRESET_FULL_RECT)
	_hp_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	# Style the HP bar with a red fill
	var bar_bg := StyleBoxFlat.new()
	bar_bg.bg_color = Color(0.15, 0.08, 0.08, 0.9)
	bar_bg.set_corner_radius_all(2)
	_hp_bar.add_theme_stylebox_override("background", bar_bg)
	var bar_fill := StyleBoxFlat.new()
	bar_fill.bg_color = Color(0.85, 0.15, 0.15, 0.95)
	bar_fill.set_corner_radius_all(2)
	_hp_bar.add_theme_stylebox_override("fill", bar_fill)
	bar_container.add_child(_hp_bar)

	_hp_text = Label.new()
	_hp_text.text = ""
	_hp_text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_hp_text.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_hp_text.set_anchors_preset(Control.PRESET_FULL_RECT)
	_hp_text.add_theme_font_size_override("font_size", 12)
	_hp_text.add_theme_color_override("font_color", Color(1.0, 1.0, 1.0, 0.9))
	_hp_text.mouse_filter = Control.MOUSE_FILTER_IGNORE
	bar_container.add_child(_hp_text)
