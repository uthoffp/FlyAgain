## SkillBar.gd
## Displays 4 skill slots at the bottom of the screen.
## Keys 1-4 trigger skills; cooldown overlays show remaining time.
## Reads from GameState for player level, MP, target, and cooldown tracking.
extends PanelContainer


## Warrior skill definitions (server-authoritative — these are display-only).
## IDs and values must match server seed data (V10 migration).
const SKILLS: Array = [
	{"id": 1, "name_key": "SKILL_STRIKE",      "mp_cost": 0,  "cooldown_ms": 1500,  "level_req": 1, "key": "1"},
	{"id": 2, "name_key": "SKILL_SHIELD_BASH",  "mp_cost": 10, "cooldown_ms": 5000,  "level_req": 3, "key": "2"},
	{"id": 3, "name_key": "SKILL_WHIRLWIND",    "mp_cost": 20, "cooldown_ms": 8000,  "level_req": 5, "key": "3"},
	{"id": 4, "name_key": "SKILL_WAR_CRY",      "mp_cost": 15, "cooldown_ms": 30000, "level_req": 8, "key": "4"},
]

const SLOT_SIZE := Vector2(80, 72)

var _slots: Array = []  # Array of Dictionaries with node references per slot
var _error_label: Label = null
var _error_tween: Tween = null


func _ready() -> void:
	custom_minimum_size = Vector2(SKILLS.size() * (SLOT_SIZE.x + 6) + 16, SLOT_SIZE.y + 16)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_apply_panel_style()
	_build_ui()
	NetworkManager.use_skill_response.connect(_on_use_skill_response)


func _exit_tree() -> void:
	if NetworkManager.use_skill_response.is_connected(_on_use_skill_response):
		NetworkManager.use_skill_response.disconnect(_on_use_skill_response)


func _process(_delta: float) -> void:
	var now := Time.get_ticks_msec()
	for i in range(_slots.size()):
		var slot: Dictionary = _slots[i]
		var skill: Dictionary = SKILLS[i]
		var skill_id: int = skill["id"]
		var level_req: int = skill["level_req"]
		var is_locked := GameState.player_level < level_req

		# Cooldown state
		var cd_end: float = GameState.skill_cooldowns.get(skill_id, 0)
		var cd_total: float = skill["cooldown_ms"]
		var cd_remaining := maxf(0.0, cd_end - now)
		var on_cooldown := cd_remaining > 0.0

		# Cooldown overlay (clock wipe from top)
		var overlay: ColorRect = slot["cooldown_overlay"]
		if on_cooldown:
			var ratio := cd_remaining / cd_total
			overlay.visible = true
			overlay.anchor_top = 1.0 - ratio
			var cd_text: Label = slot["cooldown_text"]
			cd_text.visible = true
			cd_text.text = "%.1fs" % (cd_remaining / 1000.0)
		else:
			overlay.visible = false
			slot["cooldown_text"].visible = false

		# Lock overlay for level-gated skills
		var lock_overlay: ColorRect = slot["lock_overlay"]
		var lock_label: Label = slot["lock_label"]
		lock_overlay.visible = is_locked
		lock_label.visible = is_locked
		if is_locked:
			lock_label.text = "Lv.%d" % level_req

		# MP cost color: red if insufficient
		var mp_label: Label = slot["mp_label"]
		if skill["mp_cost"] > 0:
			mp_label.visible = true
			mp_label.text = "%d MP" % skill["mp_cost"]
			if GameState.player_mp < skill["mp_cost"]:
				mp_label.add_theme_color_override("font_color", Colors.TEXT_ERROR)
			else:
				mp_label.add_theme_color_override("font_color", Colors.TEXT_INFO)
		else:
			mp_label.visible = false


func _unhandled_input(event: InputEvent) -> void:
	if GameState.is_dead:
		return
	if event is InputEventKey and event.pressed and not event.echo:
		var slot_index := -1
		match event.keycode:
			KEY_1: slot_index = 0
			KEY_2: slot_index = 1
			KEY_3: slot_index = 2
			KEY_4: slot_index = 3
		if slot_index >= 0:
			_try_use_skill(slot_index)
			get_viewport().set_input_as_handled()


func _try_use_skill(slot_index: int) -> void:
	if slot_index >= SKILLS.size():
		return
	var skill: Dictionary = SKILLS[slot_index]
	var skill_id: int = skill["id"]

	# Level requirement
	if GameState.player_level < skill["level_req"]:
		return

	# Cooldown check
	var now := Time.get_ticks_msec()
	var cd_end: float = GameState.skill_cooldowns.get(skill_id, 0)
	if now < cd_end:
		return

	# Target check
	if GameState.selected_target_id <= 0:
		_show_error(tr("SKILL_NO_TARGET"))
		return

	# MP check
	if GameState.player_mp < skill["mp_cost"]:
		_show_error(tr("SKILL_NOT_ENOUGH_MP"))
		return

	# Send to server and start optimistic cooldown
	NetworkManager.send_use_skill(skill_id, GameState.selected_target_id)
	GameState.skill_cooldowns[skill_id] = now + skill["cooldown_ms"]


func _on_use_skill_response(data: Dictionary) -> void:
	if not data.get("success", false):
		var skill_id: int = data.get("skill_id", 0)
		var error_msg: String = data.get("error_message", "")
		# Cancel optimistic cooldown on server rejection
		if skill_id > 0:
			GameState.skill_cooldowns.erase(skill_id)
		if not error_msg.is_empty():
			_show_error(error_msg)


func _show_error(msg: String) -> void:
	if _error_label == null:
		return
	_error_label.text = msg
	_error_label.modulate.a = 1.0
	_error_label.visible = true
	if _error_tween:
		_error_tween.kill()
	_error_tween = create_tween()
	_error_tween.tween_interval(1.5)
	_error_tween.tween_property(_error_label, "modulate:a", 0.0, 0.5)
	_error_tween.tween_callback(func(): _error_label.visible = false)


# ---- UI construction ----

func _apply_panel_style() -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.05, 0.05, 0.1, 0.85)
	style.border_color = Color(0.3, 0.3, 0.4, 0.6)
	style.set_border_width_all(1)
	style.set_corner_radius_all(4)
	style.set_content_margin_all(8)
	add_theme_stylebox_override("panel", style)


func _build_ui() -> void:
	var outer := VBoxContainer.new()
	outer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	outer.add_theme_constant_override("separation", 4)
	add_child(outer)

	# Error label above the skill bar
	_error_label = Label.new()
	_error_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_error_label.add_theme_font_size_override("font_size", 13)
	_error_label.add_theme_color_override("font_color", Colors.TEXT_ERROR)
	_error_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_error_label.visible = false
	outer.add_child(_error_label)

	var hbox := HBoxContainer.new()
	hbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	hbox.add_theme_constant_override("separation", 6)
	outer.add_child(hbox)

	for i in range(SKILLS.size()):
		var skill: Dictionary = SKILLS[i]
		var slot_data := _build_slot(hbox, skill, i)
		_slots.append(slot_data)


func _build_slot(parent: HBoxContainer, skill: Dictionary, _index: int) -> Dictionary:
	# Slot container (fixed size, clipping for overlays)
	var slot := PanelContainer.new()
	slot.custom_minimum_size = SLOT_SIZE
	slot.mouse_filter = Control.MOUSE_FILTER_IGNORE
	slot.clip_children = CanvasItem.CLIP_CHILDREN_AND_DRAW

	var slot_style := StyleBoxFlat.new()
	slot_style.bg_color = Color(0.08, 0.08, 0.14, 0.9)
	slot_style.border_color = Color(Colors.GOLD.r, Colors.GOLD.g, Colors.GOLD.b, 0.4)
	slot_style.set_border_width_all(1)
	slot_style.set_corner_radius_all(3)
	slot_style.set_content_margin_all(4)
	slot.add_theme_stylebox_override("panel", slot_style)
	parent.add_child(slot)

	# Content VBox
	var vbox := VBoxContainer.new()
	vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_theme_constant_override("separation", 1)
	slot.add_child(vbox)

	# Skill name
	var name_label := Label.new()
	name_label.text = tr(skill["name_key"])
	name_label.add_theme_font_size_override("font_size", 11)
	name_label.add_theme_color_override("font_color", Colors.TEXT_PRIMARY)
	name_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	name_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_child(name_label)

	# Keybind label
	var key_label := Label.new()
	key_label.text = "[%s]" % skill["key"]
	key_label.add_theme_font_size_override("font_size", 10)
	key_label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)
	key_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	key_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	vbox.add_child(key_label)

	# MP cost label
	var mp_label := Label.new()
	mp_label.add_theme_font_size_override("font_size", 10)
	mp_label.add_theme_color_override("font_color", Colors.TEXT_INFO)
	mp_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	mp_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	if skill["mp_cost"] > 0:
		mp_label.text = "%d MP" % skill["mp_cost"]
	else:
		mp_label.visible = false
	vbox.add_child(mp_label)

	# Cooldown overlay (semi-transparent, covers slot via anchors)
	var cooldown_overlay := ColorRect.new()
	cooldown_overlay.color = Color(0.0, 0.0, 0.0, 0.6)
	cooldown_overlay.set_anchors_preset(Control.PRESET_FULL_RECT)
	cooldown_overlay.anchor_top = 0.0
	cooldown_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	cooldown_overlay.visible = false
	slot.add_child(cooldown_overlay)

	# Cooldown time text (centered over overlay)
	var cooldown_text := Label.new()
	cooldown_text.set_anchors_preset(Control.PRESET_CENTER)
	cooldown_text.grow_horizontal = Control.GROW_DIRECTION_BOTH
	cooldown_text.grow_vertical = Control.GROW_DIRECTION_BOTH
	cooldown_text.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	cooldown_text.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	cooldown_text.add_theme_font_size_override("font_size", 14)
	cooldown_text.add_theme_color_override("font_color", Color(1.0, 1.0, 1.0, 0.9))
	cooldown_text.mouse_filter = Control.MOUSE_FILTER_IGNORE
	cooldown_text.visible = false
	slot.add_child(cooldown_text)

	# Lock overlay for level-gated skills
	var lock_overlay := ColorRect.new()
	lock_overlay.color = Color(0.0, 0.0, 0.0, 0.5)
	lock_overlay.set_anchors_preset(Control.PRESET_FULL_RECT)
	lock_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	lock_overlay.visible = false
	slot.add_child(lock_overlay)

	var lock_label := Label.new()
	lock_label.set_anchors_preset(Control.PRESET_CENTER)
	lock_label.grow_horizontal = Control.GROW_DIRECTION_BOTH
	lock_label.grow_vertical = Control.GROW_DIRECTION_BOTH
	lock_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	lock_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	lock_label.add_theme_font_size_override("font_size", 12)
	lock_label.add_theme_color_override("font_color", Colors.TEXT_SECONDARY)
	lock_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	lock_label.visible = false
	slot.add_child(lock_label)

	return {
		"slot": slot,
		"name_label": name_label,
		"mp_label": mp_label,
		"cooldown_overlay": cooldown_overlay,
		"cooldown_text": cooldown_text,
		"lock_overlay": lock_overlay,
		"lock_label": lock_label,
	}
