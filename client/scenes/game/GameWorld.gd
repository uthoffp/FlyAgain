## GameWorld.gd
## Root scene for the 3D game world.
## Orchestrates: zone-specific terrain, local player, remote entities, zone data.
## Shows a loading overlay during zone transitions.
extends Node3D


const PlayerCharacterScene := preload("res://scenes/game/PlayerCharacter.tscn")
const ZonePortalScene := preload("res://scenes/game/ZonePortal.tscn")
const FlyButtonScene := preload("res://scenes/ui/components/FlyButton.tscn")
const DamageNumber := preload("res://scenes/game/DamageNumber.gd")
const InventoryScreenScript := preload("res://scenes/ui/game_hud/InventoryScreen.gd")
const NpcDialogScript := preload("res://scenes/ui/game_hud/NpcDialog.gd")
const NpcShopScreenScript := preload("res://scenes/ui/game_hud/NpcShopScreen.gd")
const GameWindowScript := preload("res://scenes/ui/window_system/GameWindow.gd")
const TaskbarScript := preload("res://scenes/ui/window_system/Taskbar.gd")

const ZONE_TERRAINS: Dictionary = {
	WorldConstants.ZONE_AERHEIM: preload("res://scenes/game/terrain/AerheimTerrain.tscn"),
	WorldConstants.ZONE_GREEN_PLAINS: preload("res://scenes/game/terrain/GreenPlainsTerrain.tscn"),
	WorldConstants.ZONE_DARK_FOREST: preload("res://scenes/game/terrain/DarkForestTerrain.tscn"),
}
const FallbackTerrainScene := preload("res://scenes/game/terrain/BaseTerrain.tscn")

## Zone portal definitions: { source_zone: [{ position, target_zone_id, color }] }
## Labels are resolved at runtime via WorldConstants.get_zone_name() for localization.
const ZONE_PORTALS: Dictionary = {
	WorldConstants.ZONE_AERHEIM: [
		{"position": Vector3(500.0, 0.0, 440.0), "target": WorldConstants.ZONE_GREEN_PLAINS,
		 "color": Color(0.3, 0.8, 0.3, 0.8)},
	],
	WorldConstants.ZONE_GREEN_PLAINS: [
		{"position": Vector3(200.0, 0.0, 140.0), "target": WorldConstants.ZONE_AERHEIM,
		 "color": Color(0.8, 0.7, 0.3, 0.8)},
		{"position": Vector3(260.0, 0.0, 200.0), "target": WorldConstants.ZONE_DARK_FOREST,
		 "color": Color(0.4, 0.2, 0.6, 0.8)},
	],
	WorldConstants.ZONE_DARK_FOREST: [
		{"position": Vector3(100.0, 0.0, 40.0), "target": WorldConstants.ZONE_GREEN_PLAINS,
		 "color": Color(0.3, 0.8, 0.3, 0.8)},
	],
}

var _entity_factory: EntityFactory = EntityFactory.new()
var _player: CharacterBody3D = null
var _terrain: Node3D = null
var _logout_dialog: ConfirmationDialog = null
var _portals: Array[Area3D] = []
var _is_zone_transitioning: bool = false
var _target_frame: PanelContainer = null

# HUD references for dynamic children
var _hud_root: Control = null
var _death_screen = null  # DeathScreen (ColorRect with script)
var _notifications = null  # NotificationStack (VBoxContainer with script)
var _inventory_screen = null   # InventoryScreen (PanelContainer with script)
var _npc_dialog = null         # NpcDialog (PanelContainer with script)
var _npc_shop_screen = null    # NpcShopScreen (PanelContainer with script)

# Loading overlay nodes
var _loading_layer: CanvasLayer = null
var _loading_bg: ColorRect = null
var _loading_label: Label = null
var _loading_dots: Label = null
var _dot_timer: float = 0.0
var _dot_index: int = 0
const _DOT_PATTERNS: Array = [".", "..", "...", ".."]
const _DOT_INTERVAL := 0.4

@onready var _entities_root: Node3D = $Entities


func _ready() -> void:
	_entity_factory.initialize(_entities_root)
	_connect_signals()
	_setup_hud()
	_setup_loading_overlay()
	var initial_zone := GameState.current_zone_id
	if initial_zone == 0:
		initial_zone = WorldConstants.ZONE_AERHEIM
	_setup_terrain(initial_zone)
	_spawn_portals(initial_zone)
	_spawn_local_player()
	_show_loading(tr("LOADING"))
	_send_enter_world()


func _exit_tree() -> void:
	_disconnect_signals()


func _process(delta: float) -> void:
	if _loading_layer and _loading_layer.visible:
		_dot_timer += delta
		if _dot_timer >= _DOT_INTERVAL:
			_dot_timer -= _DOT_INTERVAL
			_dot_index = (_dot_index + 1) % _DOT_PATTERNS.size()
			if _loading_dots:
				_loading_dots.text = _DOT_PATTERNS[_dot_index]


func _unhandled_key_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_I:
				WindowManager.toggle_window("inventory")
				get_viewport().set_input_as_handled()


func _connect_signals() -> void:
	NetworkManager.zone_data_received.connect(_on_zone_data)
	NetworkManager.entity_spawned.connect(_on_entity_spawned)
	NetworkManager.entity_despawned.connect(_on_entity_despawned)
	NetworkManager.entity_position_updated.connect(_on_entity_position_updated)
	NetworkManager.world_disconnected.connect(_on_world_disconnected)
	# Combat signals
	NetworkManager.select_target_response.connect(_on_select_target_response)
	NetworkManager.damage_event.connect(_on_damage_event)
	NetworkManager.entity_death.connect(_on_entity_death)
	NetworkManager.xp_gained.connect(_on_xp_gained)
	NetworkManager.auto_attack_response.connect(_on_auto_attack_response)
	NetworkManager.entity_stats_updated.connect(_on_entity_stats_updated)
	NetworkManager.gold_updated.connect(_on_gold_updated)
	NetworkManager.inventory_updated.connect(_on_inventory_updated)


func _disconnect_signals() -> void:
	if NetworkManager.zone_data_received.is_connected(_on_zone_data):
		NetworkManager.zone_data_received.disconnect(_on_zone_data)
	if NetworkManager.entity_spawned.is_connected(_on_entity_spawned):
		NetworkManager.entity_spawned.disconnect(_on_entity_spawned)
	if NetworkManager.entity_despawned.is_connected(_on_entity_despawned):
		NetworkManager.entity_despawned.disconnect(_on_entity_despawned)
	if NetworkManager.entity_position_updated.is_connected(_on_entity_position_updated):
		NetworkManager.entity_position_updated.disconnect(_on_entity_position_updated)
	if NetworkManager.world_disconnected.is_connected(_on_world_disconnected):
		NetworkManager.world_disconnected.disconnect(_on_world_disconnected)
	# Combat signals
	if NetworkManager.select_target_response.is_connected(_on_select_target_response):
		NetworkManager.select_target_response.disconnect(_on_select_target_response)
	if NetworkManager.damage_event.is_connected(_on_damage_event):
		NetworkManager.damage_event.disconnect(_on_damage_event)
	if NetworkManager.entity_death.is_connected(_on_entity_death):
		NetworkManager.entity_death.disconnect(_on_entity_death)
	if NetworkManager.xp_gained.is_connected(_on_xp_gained):
		NetworkManager.xp_gained.disconnect(_on_xp_gained)
	if NetworkManager.auto_attack_response.is_connected(_on_auto_attack_response):
		NetworkManager.auto_attack_response.disconnect(_on_auto_attack_response)
	if NetworkManager.entity_stats_updated.is_connected(_on_entity_stats_updated):
		NetworkManager.entity_stats_updated.disconnect(_on_entity_stats_updated)
	if NetworkManager.gold_updated.is_connected(_on_gold_updated):
		NetworkManager.gold_updated.disconnect(_on_gold_updated)
	if NetworkManager.inventory_updated.is_connected(_on_inventory_updated):
		NetworkManager.inventory_updated.disconnect(_on_inventory_updated)


# ---- Loading overlay ----

func _setup_loading_overlay() -> void:
	_loading_layer = CanvasLayer.new()
	_loading_layer.name = "LoadingOverlay"
	_loading_layer.layer = 20  # Above HUD
	_loading_layer.visible = false
	add_child(_loading_layer)

	_loading_bg = ColorRect.new()
	_loading_bg.color = Color(0.05, 0.05, 0.08, 0.95)
	_loading_bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	_loading_layer.add_child(_loading_bg)

	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	_loading_bg.add_child(center)

	var vbox := VBoxContainer.new()
	vbox.alignment = BoxContainer.ALIGNMENT_CENTER
	center.add_child(vbox)

	_loading_label = Label.new()
	_loading_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_loading_label.add_theme_font_size_override("font_size", 24)
	_loading_label.add_theme_color_override("font_color", Color(0.9, 0.85, 0.6))
	vbox.add_child(_loading_label)

	_loading_dots = Label.new()
	_loading_dots.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_loading_dots.add_theme_font_size_override("font_size", 20)
	_loading_dots.add_theme_color_override("font_color", Color(0.7, 0.65, 0.5))
	_loading_dots.text = "."
	vbox.add_child(_loading_dots)


func _show_loading(message: String) -> void:
	if _loading_label:
		_loading_label.text = message
	_dot_timer = 0.0
	_dot_index = 0
	if _loading_dots:
		_loading_dots.text = "."
	if _loading_layer:
		_loading_layer.visible = true


func _hide_loading() -> void:
	if _loading_layer:
		_loading_layer.visible = false


# ---- HUD ----

func _setup_hud() -> void:
	var hud_layer := CanvasLayer.new()
	hud_layer.name = "HUD"
	hud_layer.layer = 10
	add_child(hud_layer)

	_hud_root = Control.new()
	_hud_root.name = "HUDRoot"
	_hud_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	_hud_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_hud_root.theme = ThemeFactory.create_main_theme()
	hud_layer.add_child(_hud_root)

	# Logout button — top-right corner
	var margin := MarginContainer.new()
	margin.set_anchors_preset(Control.PRESET_TOP_RIGHT)
	margin.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	margin.add_theme_constant_override("margin_top", 16)
	margin.add_theme_constant_override("margin_right", 16)
	margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_hud_root.add_child(margin)

	var logout_btn: FlyButton = FlyButtonScene.instantiate()
	logout_btn.variant = FlyButton.Variant.SECONDARY
	logout_btn.label_text = tr("LOGOUT")
	logout_btn.pressed.connect(_on_logout_pressed)
	margin.add_child(logout_btn)

	# Confirmation dialog
	_logout_dialog = ConfirmationDialog.new()
	_logout_dialog.title = tr("LOGOUT")
	_logout_dialog.dialog_text = tr("LOGOUT_CONFIRM")
	_logout_dialog.ok_button_text = tr("LOGOUT_YES")
	_logout_dialog.cancel_button_text = tr("LOGOUT_CANCEL")
	_logout_dialog.confirmed.connect(_on_logout_confirmed)
	_hud_root.add_child(_logout_dialog)

	# Player frame — top-left (fixed HUD, all features disabled)
	var player_window := PanelContainer.new()
	player_window.set_script(GameWindowScript)
	player_window.call("setup", "player_frame", tr("WINDOW_PLAYER"), {
		"draggable": false, "resizable": false,
		"minimizable": false, "closable": false,
		"default_position": Vector2(10, 10),
		"default_size": Vector2(220, 120),
	})
	_hud_root.add_child(player_window)
	var PlayerFrameScript := preload("res://scenes/ui/game_hud/PlayerFrame.gd")
	var player_frame := PanelContainer.new()
	player_frame.set_script(PlayerFrameScript)
	player_window.call("set_content", player_frame)

	# Target frame — top-center (fixed HUD, all features disabled)
	var target_window := PanelContainer.new()
	target_window.set_script(GameWindowScript)
	target_window.call("setup", "target_frame", tr("WINDOW_TARGET"), {
		"draggable": false, "resizable": false,
		"minimizable": false, "closable": false,
		"default_position": Vector2(540, 10),
		"default_size": Vector2(200, 80),
	})
	_hud_root.add_child(target_window)
	var TargetFrameScript := preload("res://scenes/ui/game_hud/TargetFrame.gd")
	_target_frame = PanelContainer.new()
	_target_frame.set_script(TargetFrameScript)
	target_window.call("set_content", _target_frame)

	# Skill bar — bottom-center (fixed HUD, all features disabled)
	var skill_window := PanelContainer.new()
	skill_window.set_script(GameWindowScript)
	skill_window.call("setup", "skill_bar", tr("WINDOW_SKILLS"), {
		"draggable": false, "resizable": false,
		"minimizable": false, "closable": false,
		"default_position": Vector2(660, 1000),
		"default_size": Vector2(600, 64),
	})
	_hud_root.add_child(skill_window)
	var SkillBarScript := preload("res://scenes/ui/game_hud/SkillBar.gd")
	var skill_bar := PanelContainer.new()
	skill_bar.set_script(SkillBarScript)
	skill_window.call("set_content", skill_bar)

	# Notification stack — right side (fixed HUD, all features disabled)
	var notif_window := PanelContainer.new()
	notif_window.set_script(GameWindowScript)
	notif_window.call("setup", "notifications", "", {
		"draggable": false, "resizable": false,
		"minimizable": false, "closable": false,
		"default_position": Vector2(1680, 200),
		"default_size": Vector2(220, 400),
	})
	_hud_root.add_child(notif_window)
	var NotifScript := preload("res://scenes/ui/game_hud/NotificationStack.gd")
	_notifications = VBoxContainer.new()
	_notifications.set_script(NotifScript)
	notif_window.call("set_content", _notifications)

	# Inventory screen — draggable, resizable, minimizable, closable
	var inv_window := PanelContainer.new()
	inv_window.set_script(GameWindowScript)
	inv_window.call("setup", "inventory", tr("WINDOW_INVENTORY"), {
		"draggable": true, "resizable": true,
		"minimizable": true, "closable": true,
		"default_position": Vector2(100, 100),
		"default_size": Vector2(450, 500),
		"min_size": Vector2(350, 400),
		"max_size": Vector2(600, 700),
	})
	inv_window.visible = false
	_hud_root.add_child(inv_window)
	_inventory_screen = PanelContainer.new()
	_inventory_screen.set_script(InventoryScreenScript)
	inv_window.call("set_content", _inventory_screen)
	_inventory_screen.visibility_changed.connect(func():
		if _inventory_screen.visible and _inventory_screen.has_method("_refresh_all"):
			_inventory_screen._refresh_all()
	)

	# NPC dialog — draggable, closable, not resizable/minimizable
	var dialog_window := PanelContainer.new()
	dialog_window.set_script(GameWindowScript)
	dialog_window.call("setup", "npc_dialog", tr("WINDOW_NPC_DIALOG"), {
		"draggable": true, "resizable": false,
		"minimizable": false, "closable": true,
		"default_position": Vector2(400, 300),
		"default_size": Vector2(300, 200),
	})
	dialog_window.visible = false
	_hud_root.add_child(dialog_window)
	_npc_dialog = PanelContainer.new()
	_npc_dialog.set_script(NpcDialogScript)
	_npc_dialog.shop_requested.connect(_on_npc_shop_requested)
	dialog_window.call("set_content", _npc_dialog)

	# NPC shop screen — all features enabled
	var shop_window := PanelContainer.new()
	shop_window.set_script(GameWindowScript)
	shop_window.call("setup", "npc_shop", tr("WINDOW_SHOP"), {
		"draggable": true, "resizable": true,
		"minimizable": true, "closable": true,
		"default_position": Vector2(300, 80),
		"default_size": Vector2(500, 550),
		"min_size": Vector2(400, 450),
		"max_size": Vector2(700, 700),
	})
	shop_window.visible = false
	_hud_root.add_child(shop_window)
	_npc_shop_screen = PanelContainer.new()
	_npc_shop_screen.set_script(NpcShopScreenScript)
	shop_window.call("set_content", _npc_shop_screen)

	# Taskbar — dynamic buttons for minimized windows
	var taskbar := PanelContainer.new()
	taskbar.set_script(TaskbarScript)
	_hud_root.add_child(taskbar)

	# Death screen overlay (added last so it draws on top of other HUD elements)
	var death_window := PanelContainer.new()
	death_window.set_script(GameWindowScript)
	death_window.call("setup", "death_screen", "", {
		"draggable": false, "resizable": false,
		"minimizable": false, "closable": false,
	})
	_hud_root.add_child(death_window)
	var DeathScreenScript := preload("res://scenes/ui/game_hud/DeathScreen.gd")
	_death_screen = ColorRect.new()
	_death_screen.set_script(DeathScreenScript)
	_death_screen.respawn_requested.connect(_on_respawn_requested)
	death_window.call("set_content", _death_screen)


func _on_logout_pressed() -> void:
	_logout_dialog.popup_centered()


func _on_logout_confirmed() -> void:
	NetworkManager.send_logout()
	GameState.reset()
	UIManager.leave_game_world("login")


# ---- Terrain management ----

func _setup_terrain(zone_id: int) -> void:
	if _terrain and is_instance_valid(_terrain):
		remove_child(_terrain)
		_terrain.queue_free()
		_terrain = null

	var scene: PackedScene = ZONE_TERRAINS.get(zone_id, FallbackTerrainScene)
	_terrain = scene.instantiate()
	add_child(_terrain)
	# Keep terrain behind entities in the scene tree
	move_child(_terrain, 0)


# ---- Zone portals ----

func _spawn_portals(zone_id: int) -> void:
	_clear_portals()
	var portal_defs: Array = ZONE_PORTALS.get(zone_id, [])
	for def in portal_defs:
		var portal: Area3D = ZonePortalScene.instantiate()
		portal.target_zone_id = def["target"]
		portal.portal_label = WorldConstants.get_zone_name(def["target"])
		portal.portal_color = def.get("color", Color(0.3, 0.5, 1.0, 0.8))
		var pos: Vector3 = def["position"]
		if _terrain and _terrain.has_method("get_height_at"):
			pos.y = _terrain.get_height_at(pos.x, pos.z)
		portal.position = pos
		portal.portal_entered.connect(_on_portal_entered)
		add_child(portal)
		_portals.append(portal)


func _clear_portals() -> void:
	for portal in _portals:
		if is_instance_valid(portal):
			portal.queue_free()
	_portals.clear()


func _on_portal_entered(target_zone_id: int) -> void:
	if _is_zone_transitioning:
		return
	_is_zone_transitioning = true
	_show_loading(WorldConstants.get_zone_name(target_zone_id))
	NetworkManager.send_zone_change_request(target_zone_id)


func _send_enter_world() -> void:
	NetworkManager.send_enter_world(
		GameState.jwt,
		GameState.selected_character_id,
		GameState.session_id)


# ---- Signal handlers ----

func _on_zone_data(data: Dictionary) -> void:
	GameState.my_entity_id = data.get("my_entity_id", 0)
	GameState.current_zone_id = data.get("zone_id", 0)
	GameState.current_channel_id = data.get("channel_id", 0)
	GameState.current_zone_name = data.get("zone_name", "")
	print("[WORLD] Zone data: my_entity_id=%d zone=%d channel=%d entities=%d" % [
		GameState.my_entity_id, GameState.current_zone_id,
		GameState.current_channel_id, data.get("entities", []).size()])

	# Swap terrain for the new zone
	_setup_terrain(GameState.current_zone_id)

	# Swap portals for the new zone
	_spawn_portals(GameState.current_zone_id)

	# Clear target and existing remote entities
	_deselect_current_target()
	GameState.auto_attack_active = false
	if _player:
		_player.cancel_approach()
	_entity_factory.clear_all()

	# Reposition local player to server-authoritative position
	if _player and is_instance_valid(_player):
		var pos_data: Dictionary = data.get("player_position", {})
		var spawn_pos := Vector3(
			pos_data.get("x", 0.0),
			pos_data.get("y", 0.0),
			pos_data.get("z", 0.0))
		if spawn_pos == Vector3.ZERO:
			spawn_pos = WorldConstants.ZONE_SPAWNS.get(
				GameState.current_zone_id, WorldConstants.DEFAULT_SPAWN)
		if _terrain and _terrain.has_method("get_height_at"):
			spawn_pos.y = _terrain.get_height_at(spawn_pos.x, spawn_pos.z)
		_player.teleport_to(spawn_pos)
		GameState.player_position = spawn_pos

	# Spawn all entities from zone data (skip our own entity)
	var entities: Array = data.get("entities", [])
	for entity_data in entities:
		var eid: int = entity_data.get("entity_id", 0)
		if eid != GameState.my_entity_id:
			print("[WORLD] Spawning entity from zone data: id=%d name=%s" % [
				eid, entity_data.get("name", "")])
			_entity_factory.spawn_entity(entity_data)

	# Spawn client-side NPC entities for this zone
	_spawn_zone_npcs(GameState.current_zone_id)

	# Hide loading overlay after a brief delay to let the scene settle
	_is_zone_transitioning = false
	_hide_loading_deferred()


func _hide_loading_deferred() -> void:
	# Wait two frames for terrain and entities to render, then hide overlay
	await get_tree().process_frame
	await get_tree().process_frame
	_hide_loading()


func _on_entity_spawned(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if entity_id == GameState.my_entity_id:
		return  # Don't spawn ourselves as a remote entity
	print("[WORLD] Entity spawned: id=%d name=%s" % [entity_id, data.get("name", "")])
	_entity_factory.spawn_entity(data)


func _on_entity_despawned(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if entity_id == GameState.my_entity_id:
		return
	print("[WORLD] Entity despawned: id=%d" % entity_id)
	_entity_factory.despawn_entity(entity_id)


func _on_entity_position_updated(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if entity_id == GameState.my_entity_id:
		return  # Our own position is handled by prediction
	_entity_factory.update_entity_position(data)


func _on_world_disconnected() -> void:
	_entity_factory.clear_all()
	_clear_portals()
	GameState.reset()
	UIManager.leave_game_world("login")


# ---- Player spawning ----

func _spawn_local_player() -> void:
	_player = PlayerCharacterScene.instantiate()
	# Use the spawn position from GameState if available, otherwise default
	var spawn_pos := GameState.player_position
	if spawn_pos == Vector3.ZERO:
		spawn_pos = WorldConstants.DEFAULT_SPAWN
	if _terrain and _terrain.has_method("get_height_at"):
		spawn_pos.y = _terrain.get_height_at(spawn_pos.x, spawn_pos.z)
	_player.position = spawn_pos
	add_child(_player)
	# Connect player targeting signals
	_player.target_selected.connect(_on_player_target_selected)
	_player.target_cleared.connect(_on_player_target_cleared)
	_player.auto_attack_toggled.connect(_on_player_auto_attack_toggled)
	_player.approach_in_range.connect(_on_player_approach_in_range)
	_player.movement_started.connect(_on_player_movement_started)


# ---- NPC spawning ----

## Mapping from npc_def_id -> client-side entity_id (negative to avoid server ID conflicts)
var _npc_entity_ids: Dictionary = {}

func _spawn_zone_npcs(zone_id: int) -> void:
	# Clear previous NPC entity mappings
	for npc_eid in _npc_entity_ids.values():
		_entity_factory.despawn_entity(npc_eid)
	_npc_entity_ids.clear()

	var next_npc_eid := -1
	for npc_def_id in NpcRegistry.NPCS:
		var npc_data := NpcRegistry.get_npc(npc_def_id)
		if npc_data.is_empty():
			continue
		# Only spawn NPCs for the current zone (zone_id 0 = Aerheim = 1 internally)
		var npc_zone: int = npc_data.get("zone_id", -1)
		if npc_zone == 0 and zone_id != WorldConstants.ZONE_AERHEIM:
			continue
		if npc_zone > 0 and npc_zone != zone_id:
			continue

		var npc_pos: Vector3 = npc_data.get("pos", Vector3.ZERO)
		# Snap NPC Y to terrain height
		if _terrain and _terrain.has_method("get_height_at"):
			npc_pos.y = _terrain.get_height_at(npc_pos.x, npc_pos.z)

		var entity_data := {
			"entity_id": next_npc_eid,
			"entity_type": WorldConstants.ENTITY_TYPE_NPC,
			"name": tr(npc_data.get("name", "")),
			"level": 0,
			"hp": 1,
			"max_hp": 1,
			"position": {"x": npc_pos.x, "y": npc_pos.y, "z": npc_pos.z},
		}
		_entity_factory.spawn_entity(entity_data)
		_npc_entity_ids[npc_def_id] = next_npc_eid
		print("[WORLD] Spawned NPC '%s' (def_id=%d, eid=%d) at %s" % [
			entity_data["name"], npc_def_id, next_npc_eid, str(npc_pos)])
		next_npc_eid -= 1


# ---- Combat / Targeting ----

## Player requested a target selection (click on entity).
func _on_player_target_selected(entity_id: int) -> void:
	# Check if the clicked entity is an NPC (client-side, negative ID)
	for npc_def_id in _npc_entity_ids:
		if _npc_entity_ids[npc_def_id] == entity_id:
			# Show selection ring on the NPC
			_deselect_current_target()
			var npc_entity := _entity_factory.get_entity(entity_id)
			if npc_entity and is_instance_valid(npc_entity) and npc_entity.has_method("set_selected"):
				npc_entity.set_selected(true)
			GameState.selected_target_id = entity_id
			GameState.selected_target_name = npc_entity.entity_name if npc_entity else ""
			if NpcRegistry.is_in_range(npc_def_id, GameState.player_position):
				if _npc_dialog and _npc_dialog.has_method("show_dialog"):
					_npc_dialog.show_dialog(npc_def_id)
			return  # Don't send select_target to server for NPCs

	NetworkManager.send_select_target(entity_id)


## Player pressed Escape to clear their target.
func _on_player_target_cleared() -> void:
	NetworkManager.send_select_target(0)
	_deselect_current_target()
	if _player:
		_player.cancel_approach()


## Player toggled auto-attack (F key).
func _on_player_auto_attack_toggled(enable: bool, target_id: int) -> void:
	if target_id > 0:
		NetworkManager.send_toggle_auto_attack(enable, target_id)


## Player approached target and is now in attack range.
func _on_player_approach_in_range(entity_id: int) -> void:
	if entity_id > 0:
		print("[Combat] Sending auto-attack toggle (enable=true) for entity %d" % entity_id)
		NetworkManager.send_toggle_auto_attack(true, entity_id)


## Server confirmed target selection.
func _on_select_target_response(data: Dictionary) -> void:
	if not data.get("success", false):
		return
	# Deselect the old target visually
	_deselect_current_target()
	# Update GameState with the new target
	var target_id: int = data.get("target_entity_id", 0)
	GameState.selected_target_id = target_id
	GameState.selected_target_name = data.get("target_name", "")
	GameState.selected_target_level = data.get("target_level", 0)
	GameState.selected_target_hp = data.get("target_hp", 0)
	GameState.selected_target_max_hp = data.get("target_max_hp", 0)
	# Highlight the new target entity
	if target_id > 0:
		var entity := _entity_factory.get_entity(target_id)
		if entity and is_instance_valid(entity) and entity.has_method("set_selected"):
			entity.set_selected(true)


## A damage event occurred (us hitting something, or something hitting us).
func _on_damage_event(data: Dictionary) -> void:
	print("[Combat] Damage event: %s" % str(data))
	var target_id: int = data.get("target_entity_id", 0)
	var new_hp: int = data.get("target_current_hp", 0)
	# If we are the target, update our own HP
	if target_id == GameState.my_entity_id:
		GameState.player_hp = new_hp
	else:
		# Update the remote entity's HP in the scene
		var entity := _entity_factory.get_entity(target_id)
		if entity and is_instance_valid(entity) and entity.has_method("update_hp"):
			entity.update_hp(new_hp)
	# Update GameState target HP if this is our current target
	if target_id == GameState.selected_target_id:
		GameState.selected_target_hp = new_hp
	# Spawn floating damage number
	_spawn_damage_number(data)


## An entity died.
func _on_entity_death(data: Dictionary) -> void:
	var dead_id: int = data.get("entity_id", 0)

	# Check if WE died
	if dead_id == GameState.my_entity_id:
		GameState.is_dead = true
		GameState.auto_attack_active = false
		_deselect_current_target()
		if _player:
			_player.cancel_approach()
		if _death_screen and _death_screen.has_method("show_death"):
			_death_screen.show_death()
		return

	# If our current target died, deselect and stop auto-attack
	if dead_id == GameState.selected_target_id:
		_deselect_current_target()
		GameState.auto_attack_active = false
		if _player:
			_player.cancel_approach()
	# Untrack the entity so it won't receive further updates, then play
	# a death animation. The node frees itself after the animation.
	var entity := _entity_factory.untrack_entity(dead_id)
	if entity and is_instance_valid(entity) and entity.has_method("play_death_effect"):
		entity.play_death_effect()
	elif entity and is_instance_valid(entity):
		entity.queue_free()


## Respawn button pressed on the death screen.
func _on_respawn_requested() -> void:
	GameState.is_dead = false
	# Server already healed us to full HP/MP on death — apply predictively
	GameState.player_hp = GameState.player_max_hp
	GameState.player_mp = GameState.player_max_mp


## We gained XP (e.g. from killing a monster).
func _on_xp_gained(data: Dictionary) -> void:
	GameState.player_xp = data.get("total_xp", GameState.player_xp)
	GameState.player_xp_to_next_level = data.get("xp_to_next_level", GameState.player_xp_to_next_level)
	var new_level: int = data.get("current_level", 0)
	if new_level > 0:
		GameState.player_level = new_level

	# Level-Up effect
	var leveled_up: bool = data.get("leveled_up", false)
	if leveled_up:
		_show_level_up_effect()

	# XP notification
	var xp_amount: int = data.get("xp_gained", 0)
	if xp_amount > 0 and _notifications and _notifications.has_method("show_notification"):
		_notifications.show_notification(
			tr("XP_GAINED").replace("{amount}", str(xp_amount)), Colors.GOLD)


## Server confirmed auto-attack toggle.
func _on_auto_attack_response(data: Dictionary) -> void:
	GameState.auto_attack_active = data.get("auto_attacking", false)


## Server sent updated stats for an entity (could be us or our target).
func _on_entity_stats_updated(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if entity_id == GameState.my_entity_id:
		# Update our own stats
		GameState.player_hp = data.get("hp", GameState.player_hp)
		GameState.player_max_hp = data.get("max_hp", GameState.player_max_hp)
		GameState.player_mp = data.get("mp", GameState.player_mp)
		GameState.player_max_mp = data.get("max_mp", GameState.player_max_mp)
		GameState.player_str = data.get("str", GameState.player_str)
		GameState.player_sta = data.get("sta", GameState.player_sta)
		GameState.player_dex = data.get("dex", GameState.player_dex)
		GameState.player_int = data.get("int", GameState.player_int)
		GameState.player_level = data.get("level", GameState.player_level)


## Server sent a gold update (e.g. from killing a monster).
func _on_gold_updated(data: Dictionary) -> void:
	GameState.player_gold = data.get("total_gold", GameState.player_gold)

	var gold_amount: int = data.get("gold_gained", 0)
	if gold_amount > 0 and _notifications and _notifications.has_method("show_notification"):
		_notifications.show_notification(
			tr("GOLD_GAINED").replace("{amount}", str(gold_amount)), Colors.GOLD_BRIGHT)


## Deselect the currently selected target and clear GameState target fields.
func _deselect_current_target() -> void:
	if GameState.selected_target_id != 0:
		var old_entity := _entity_factory.get_entity(GameState.selected_target_id)
		if old_entity and is_instance_valid(old_entity) and old_entity.has_method("set_selected"):
			old_entity.set_selected(false)
	GameState.selected_target_id = 0
	GameState.selected_target_name = ""
	GameState.selected_target_level = 0
	GameState.selected_target_hp = 0
	GameState.selected_target_max_hp = 0


## Spawn a floating damage number above the damaged entity.
func _spawn_damage_number(data: Dictionary) -> void:
	var target_id: int = data.get("target_entity_id", 0)
	var damage: int = data.get("damage", 0)
	var is_critical: bool = data.get("is_critical", false)
	var attacker_id: int = data.get("attacker_entity_id", 0)

	# Determine spawn position: above the target entity or above the player
	var spawn_pos := Vector3.ZERO
	var is_self_damage := (target_id == GameState.my_entity_id)

	if is_self_damage and _player and is_instance_valid(_player):
		spawn_pos = _player.global_position + Vector3(0, 2.5, 0)
	else:
		var entity := _entity_factory.get_entity(target_id)
		if entity and is_instance_valid(entity):
			spawn_pos = entity.global_position + Vector3(0, 2.5, 0)
		else:
			return  # No entity to attach the number to

	var dmg_label := DamageNumber.new()
	dmg_label.setup(damage, is_critical, is_self_damage)
	get_tree().current_scene.add_child(dmg_label)
	dmg_label.global_position = spawn_pos


## Show a floating "Level Up!" effect at the center of the screen.
func _show_level_up_effect() -> void:
	if _hud_root == null:
		return
	var LevelUpScript := preload("res://scenes/ui/game_hud/LevelUpEffect.gd")
	var label := Label.new()
	label.set_script(LevelUpScript)
	label.custom_minimum_size = Vector2(250, 0)
	# Position at screen center
	var viewport_size := get_viewport().get_visible_rect().size
	label.position = Vector2(
		(viewport_size.x - 250.0) / 2.0,
		viewport_size.y / 2.0 - 40.0)
	_hud_root.add_child(label)


func _on_npc_shop_requested(npc_def_id: int) -> void:
	if _npc_shop_screen and _npc_shop_screen.has_method("open_shop"):
		_npc_shop_screen.open_shop(npc_def_id)


## Close NPC dialog and shop when the player starts moving.
func _on_player_movement_started() -> void:
	WindowManager.close_window("npc_dialog")
	WindowManager.close_window("npc_shop")


## Handle inventory update — show loot notifications ONLY for server-pushed updates
## (not for player-initiated move/equip/buy/sell which have their own response handlers).
var _suppress_loot_notifications: bool = false

func _on_inventory_updated(data: Dictionary) -> void:
	# Suppress notifications when the update is from a player-initiated action
	if _suppress_loot_notifications:
		_suppress_loot_notifications = false
		return
	# Only show notifications when no inventory/shop UI is open (i.e., this is a loot drop)
	if (_inventory_screen and _inventory_screen.is_visible_in_tree()) or (_npc_shop_screen and _npc_shop_screen.is_visible_in_tree()):
		return
	var slots: Array = data.get("slots", [])
	for slot_data in slots:
		var item_id: int = slot_data.get("item_id", 0)
		var amount: int = slot_data.get("amount", 0)
		if item_id > 0 and _notifications and _notifications.has_method("show_notification"):
			var item_def := ItemDatabase.get_item(item_id)
			if item_def.is_empty():
				continue
			var item_name: String = tr(item_def.get("name", ""))
			var text: String
			if amount > 1:
				text = tr("ITEM_RECEIVED_STACK").replace("{name}", item_name).replace("{amount}", str(amount))
			else:
				text = tr("ITEM_RECEIVED").replace("{name}", item_name)
			_notifications.show_notification(text, ItemDatabase.get_rarity_color(item_def.get("rarity", 0)))
