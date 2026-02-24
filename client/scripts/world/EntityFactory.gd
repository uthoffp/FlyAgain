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
	if _entities.has(entity_id):
		return _entities[entity_id]

	var entity: Node3D = RemoteEntityScene.instantiate()
	entity.setup(data)
	_parent.add_child(entity)
	_entities[entity_id] = entity
	return entity


## Despawn a remote entity by ID.
func despawn_entity(entity_id: int) -> void:
	if not _entities.has(entity_id):
		return
	var entity: Node3D = _entities[entity_id]
	entity.queue_free()
	_entities.erase(entity_id)


## Update position for a remote entity from an EntityPositionUpdate dictionary.
func update_entity_position(data: Dictionary) -> void:
	var entity_id: int = data.get("entity_id", 0)
	if not _entities.has(entity_id):
		return
	var entity: Node3D = _entities[entity_id]
	var pos_dict: Dictionary = data.get("position", {})
	var pos := Vector3(
		pos_dict.get("x", 0.0),
		pos_dict.get("y", 0.0),
		pos_dict.get("z", 0.0))
	entity.push_position_update(
		pos,
		data.get("rotation", 0.0),
		data.get("is_moving", false),
		data.get("is_flying", false))


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
