## DeathScreen.gd
## Full-screen dark overlay shown when the player dies.
## Contains a "You Died" title and a Respawn button.
## Blocks all input behind it while visible.
extends ColorRect


signal respawn_requested


var _title_label: Label = null
var _respawn_btn: Button = null


func _ready() -> void:
	# Full-screen dark overlay
	set_anchors_preset(Control.PRESET_FULL_RECT)
	color = Color(0.0, 0.0, 0.0, 0.0)
	mouse_filter = Control.MOUSE_FILTER_STOP
	visible = false
	_build_ui()


func show_death() -> void:
	visible = true
	mouse_filter = Control.MOUSE_FILTER_STOP
	# Fade in
	var tw := create_tween()
	tw.tween_property(self, "color", Color(0.0, 0.0, 0.0, 0.7), 0.5)
	if _title_label:
		_title_label.modulate.a = 0.0
		var label_tw := create_tween()
		label_tw.tween_interval(0.3)
		label_tw.tween_property(_title_label, "modulate:a", 1.0, 0.4)
	if _respawn_btn:
		_respawn_btn.modulate.a = 0.0
		var btn_tw := create_tween()
		btn_tw.tween_interval(0.6)
		btn_tw.tween_property(_respawn_btn, "modulate:a", 1.0, 0.3)


func hide_death() -> void:
	var tw := create_tween()
	tw.tween_property(self, "color", Color(0.0, 0.0, 0.0, 0.0), 0.3)
	tw.tween_callback(_on_fade_complete)


func _on_fade_complete() -> void:
	visible = false
	mouse_filter = Control.MOUSE_FILTER_IGNORE


func _on_respawn_pressed() -> void:
	if _respawn_btn:
		_respawn_btn.disabled = true
	respawn_requested.emit()
	hide_death()
	# Re-enable after fade
	await get_tree().create_timer(0.4).timeout
	if _respawn_btn:
		_respawn_btn.disabled = false


func _build_ui() -> void:
	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(center)

	var vbox := VBoxContainer.new()
	vbox.alignment = BoxContainer.ALIGNMENT_CENTER
	vbox.add_theme_constant_override("separation", 24)
	vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	center.add_child(vbox)

	# Death title
	_title_label = Label.new()
	_title_label.text = tr("DEATH_TITLE")
	_title_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_title_label.add_theme_font_size_override("font_size", 32)
	_title_label.add_theme_color_override("font_color", Colors.TEXT_ERROR)
	_title_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_child(_title_label)

	# Respawn button
	_respawn_btn = Button.new()
	_respawn_btn.text = tr("DEATH_RESPAWN")
	_respawn_btn.custom_minimum_size = Vector2(180, 44)
	_respawn_btn.pressed.connect(_on_respawn_pressed)

	var btn_normal := StyleBoxFlat.new()
	btn_normal.bg_color = Colors.BTN_NORMAL
	btn_normal.border_color = Colors.GOLD_DARK
	btn_normal.set_border_width_all(1)
	btn_normal.set_corner_radius_all(4)
	btn_normal.set_content_margin_all(8)
	_respawn_btn.add_theme_stylebox_override("normal", btn_normal)

	var btn_hover := StyleBoxFlat.new()
	btn_hover.bg_color = Colors.BTN_HOVER
	btn_hover.border_color = Colors.GOLD
	btn_hover.set_border_width_all(1)
	btn_hover.set_corner_radius_all(4)
	btn_hover.set_content_margin_all(8)
	_respawn_btn.add_theme_stylebox_override("hover", btn_hover)

	var btn_pressed := StyleBoxFlat.new()
	btn_pressed.bg_color = Colors.BTN_PRESSED
	btn_pressed.border_color = Colors.GOLD_DARK
	btn_pressed.set_border_width_all(1)
	btn_pressed.set_corner_radius_all(4)
	btn_pressed.set_content_margin_all(8)
	_respawn_btn.add_theme_stylebox_override("pressed", btn_pressed)

	_respawn_btn.add_theme_color_override("font_color", Colors.TEXT_PRIMARY)
	_respawn_btn.add_theme_color_override("font_hover_color", Colors.GOLD_BRIGHT)
	_respawn_btn.add_theme_font_size_override("font_size", 18)
	vbox.add_child(_respawn_btn)
