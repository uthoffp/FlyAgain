## EntityFactoryTest.gd
## Tests for EntityFactory: validation, container tracking, and lifecycle.
class_name EntityFactoryTest
extends GdUnitTestSuite


var _factory: EntityFactory


func before_test() -> void:
	_factory = auto_free(EntityFactory.new())


# ---- Initial state ----

func test_initial_entity_count_is_zero() -> void:
	assert_int(_factory.entity_count()).is_equal(0)


func test_has_entity_returns_false_initially() -> void:
	assert_bool(_factory.has_entity(1)).is_false()


func test_get_entity_returns_null_initially() -> void:
	assert_object(_factory.get_entity(1)).is_null()


# ---- spawn_entity validation: entity_id=0 ----

func test_spawn_entity_rejects_zero_id() -> void:
	var result := _factory.spawn_entity({"entity_id": 0, "position": {"x": 0.0, "y": 0.0, "z": 0.0}})
	assert_object(result).is_null()
	assert_int(_factory.entity_count()).is_equal(0)


func test_spawn_entity_rejects_missing_entity_id() -> void:
	var result := _factory.spawn_entity({"position": {"x": 0.0, "y": 0.0, "z": 0.0}})
	assert_object(result).is_null()
	assert_int(_factory.entity_count()).is_equal(0)


# ---- spawn_entity validation: missing position ----

func test_spawn_entity_rejects_missing_position() -> void:
	var result := _factory.spawn_entity({"entity_id": 42})
	assert_object(result).is_null()
	assert_int(_factory.entity_count()).is_equal(0)


# ---- clear_all ----

func test_clear_all_on_empty_does_nothing() -> void:
	_factory.clear_all()
	assert_int(_factory.entity_count()).is_equal(0)


# ---- despawn_entity ----

func test_despawn_nonexistent_entity_does_nothing() -> void:
	_factory.despawn_entity(999)
	assert_int(_factory.entity_count()).is_equal(0)


# ---- update_entity_position with missing entity ----

func test_update_position_nonexistent_entity_does_nothing() -> void:
	_factory.update_entity_position({
		"entity_id": 999,
		"position": {"x": 10.0, "y": 0.0, "z": 10.0},
		"rotation": 0.0,
		"is_moving": false,
		"is_flying": false,
	})
	# Should not crash or create entities
	assert_int(_factory.entity_count()).is_equal(0)


# ---- get_nearby_monsters with empty factory ----

func test_get_nearby_monsters_empty_returns_empty() -> void:
	var result := _factory.get_nearby_monsters(Vector3.ZERO)
	assert_array(result).is_empty()
