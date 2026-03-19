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
