## GameWorld.gd
## Root scene for the 3D game world.
## Orchestrates: zone-specific terrain, local player, remote entities, zone data.
extends Node3D


const PlayerCharacterScene := preload("res://scenes/game/PlayerCharacter.tscn")
const FlyButtonScene := preload("res://scenes/ui/components/FlyButton.tscn")

const ZONE_TERRAINS: Dictionary = {
	WorldConstants.ZONE_AERHEIM: preload("res://scenes/game/terrain/AerheimTerrain.tscn"),
	WorldConstants.ZONE_GREEN_PLAINS: preload("res://scenes/game/terrain/GreenPlainsTerrain.tscn"),
	WorldConstants.ZONE_DARK_FOREST: preload("res://scenes/game/terrain/DarkForestTerrain.tscn"),
}
const FallbackTerrainScene := preload("res://scenes/game/terrain/BaseTerrain.tscn")

var _entity_factory: EntityFactory = EntityFactory.new()
var _player: CharacterBody3D = null
var _terrain: Node3D = null
var _logout_dialog: ConfirmationDialog = null

@onready var _entities_root: Node3D = $Entities


func _ready() -> void:
	_entity_factory.initialize(_entities_root)
	_connect_signals()
	_setup_hud()
	var initial_zone := GameState.current_zone_id
	if initial_zone == 0:
		initial_zone = WorldConstants.ZONE_AERHEIM
	_setup_terrain(initial_zone)
	_spawn_local_player()
	_send_enter_world()


func _exit_tree() -> void:
	_disconnect_signals()


func _connect_signals() -> void:
	NetworkManager.zone_data_received.connect(_on_zone_data)
	NetworkManager.entity_spawned.connect(_on_entity_spawned)
	NetworkManager.entity_despawned.connect(_on_entity_despawned)
	NetworkManager.entity_position_updated.connect(_on_entity_position_updated)
	NetworkManager.world_disconnected.connect(_on_world_disconnected)


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


# ---- HUD ----

func _setup_hud() -> void:
	var hud_layer := CanvasLayer.new()
	hud_layer.name = "HUD"
	hud_layer.layer = 10
	add_child(hud_layer)

	var hud_root := Control.new()
	hud_root.name = "HUDRoot"
	hud_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	hud_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	hud_root.theme = ThemeFactory.create_main_theme()
	hud_layer.add_child(hud_root)

	# Logout button — top-right corner
	var margin := MarginContainer.new()
	margin.set_anchors_preset(Control.PRESET_TOP_RIGHT)
	margin.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	margin.add_theme_constant_override("margin_top", 16)
	margin.add_theme_constant_override("margin_right", 16)
	margin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	hud_root.add_child(margin)

	var logout_btn: FlyButton = FlyButtonScene.instantiate()
	logout_btn.variant = FlyButton.Variant.SECONDARY
	logout_btn.label_text = "Abmelden"  # TODO: localize with tr()
	logout_btn.pressed.connect(_on_logout_pressed)
	margin.add_child(logout_btn)

	# Confirmation dialog
	_logout_dialog = ConfirmationDialog.new()
	_logout_dialog.title = "Abmelden"  # TODO: localize with tr()
	_logout_dialog.dialog_text = "Möchtest du dich wirklich abmelden?"  # TODO: localize with tr()
	_logout_dialog.ok_button_text = "Ja"  # TODO: localize with tr()
	_logout_dialog.cancel_button_text = "Abbrechen"  # TODO: localize with tr()
	_logout_dialog.confirmed.connect(_on_logout_confirmed)
	hud_root.add_child(_logout_dialog)


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

	# Clear existing remote entities
	_entity_factory.clear_all()

	# Spawn all entities from zone data (skip our own entity)
	var entities: Array = data.get("entities", [])
	for entity_data in entities:
		var eid: int = entity_data.get("entity_id", 0)
		if eid != GameState.my_entity_id:
			print("[WORLD] Spawning entity from zone data: id=%d name=%s" % [
				eid, entity_data.get("name", "")])
			_entity_factory.spawn_entity(entity_data)


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
	GameState.reset()
	UIManager.leave_game_world("login")


# ---- Player spawning ----

func _spawn_local_player() -> void:
	_player = PlayerCharacterScene.instantiate()
	# Use the spawn position from GameState if available, otherwise default
	var spawn_pos := GameState.player_position
	if spawn_pos == Vector3.ZERO:
		spawn_pos = WorldConstants.DEFAULT_SPAWN
	_player.position = spawn_pos
	add_child(_player)
