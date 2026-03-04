## EntityFactory.gd
## Manages the lifecycle of remote entity nodes in the scene tree.
## Centralized creation/destruction point for extensibility (combat, health bars, etc.).
class_name EntityFactory
extends RefCounted


const RemoteEntityScene := preload("res://scenes/game/RemoteEntity.tscn")

var _entities: Dictionary = {}  # entity_id: int -> RemoteEntity node
var _parent: Node3D = null


## Initialize with the parent node that will hold all entity nodes.
func initialize(parent: Node3D) -> void:
	_parent = parent


## Spawn a remote entity from an EntitySpawnMessage dictionary.
## Returns the spawned node, or the existing one if already spawned.
func spawn_entity(data: Dictionary) -> Node3D:
	var entity_id: int = data.get("entity_id", 0)
	if entity_id == 0:
		push_warning("[FACTORY] Invalid entity spawn: entity_id=0")
		return null
	if not data.has("position"):
		push_warning("[FACTORY] Invalid entity spawn: missing position for id=%d" % entity_id)
		return null

	if _entities.has(entity_id):
		var existing: Node3D = _entities[entity_id]
		if is_instance_valid(existing) and not existing.is_queued_for_deletion():
			print("[FACTORY] Entity %d already exists, skipping spawn" % entity_id)
			return existing
		# Old entity is being freed, allow respawn
		_entities.erase(entity_id)

	var entity: Node3D = RemoteEntityScene.instantiate()
	entity.setup(data)
	_parent.add_child(entity)
	_entities[entity_id] = entity
	var pos_dict: Dictionary = data.get("position", {})
	print("[FACTORY] Spawned entity %d at (%.1f, %.1f, %.1f) — total entities: %d" % [
		entity_id, pos_dict.get("x", 0.0), pos_dict.get("y", 0.0),
		pos_dict.get("z", 0.0), _entities.size()])
	return entity


## Despawn a remote entity by ID.
func despawn_entity(entity_id: int) -> void:
	if not _entities.has(entity_id):
		return
	var entity: Node3D = _entities[entity_id]
	_entities.erase(entity_id)
	if is_instance_valid(entity):
		entity.queue_free()


## Update position for a remote entity from an EntityPositionUpdate dictionary.
func update_entity_position(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if not _entities.has(entity_id):
		return
	var entity: Node3D = _entities[entity_id]
	if not is_instance_valid(entity):
		_entities.erase(entity_id)
		return
	var pos_dict: Dictionary = data.get("position", {})
	var pos := Vector3(
		pos_dict.get("x", 0.0),
		pos_dict.get("y", 0.0),
		pos_dict.get("z", 0.0))
	entity.push_position_update(
		pos,
		data.get("rotation", 0.0),
		data.get("is_moving", false),
		data.get("is_flying", false),
		data.get("jump_offset", 0.0))


## Clear all entities (zone change).
func clear_all() -> void:
	for entity in _entities.values():
		if is_instance_valid(entity):
			entity.queue_free()
	_entities.clear()


## Get an entity node by ID.
func get_entity(entity_id: int) -> Node3D:
	return _entities.get(entity_id)


## Returns the number of tracked entities.
func entity_count() -> int:
	return _entities.size()


## Check if an entity exists.
func has_entity(entity_id: int) -> bool:
	return _entities.has(entity_id)


## Returns nearby monsters sorted by distance from the given position.
## Each entry is a Dictionary with "entity_id" and "distance".
func get_nearby_monsters(player_pos: Vector3) -> Array:
	var monsters := []
	for eid in _entities:
		var entity: RemoteEntity = _entities[eid]
		if not is_instance_valid(entity):
			continue
		if entity.entity_type == WorldConstants.ENTITY_TYPE_MONSTER and entity.hp > 0:
			monsters.append({
				"entity_id": eid,
				"distance": player_pos.distance_to(entity.global_position)
			})
	monsters.sort_custom(func(a, b): return a["distance"] < b["distance"])
	return monsters
