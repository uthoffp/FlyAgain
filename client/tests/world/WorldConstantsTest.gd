## WorldConstantsTest.gd
## Tests for WorldConstants: zone IDs, spawn positions, speeds, and helper methods.
class_name WorldConstantsTest
extends GdUnitTestSuite


# ---- Movement speed constants ----

func test_ground_speed() -> void:
	assert_float(WorldConstants.GROUND_MOVE_SPEED).is_equal(5.0)


func test_fly_speed_greater_than_ground() -> void:
	assert_float(WorldConstants.FLY_MOVE_SPEED).is_greater(WorldConstants.GROUND_MOVE_SPEED)


# ---- World boundary constants ----

func test_boundary_min_less_than_max() -> void:
	assert_float(WorldConstants.WORLD_BOUNDARY_MIN).is_less(WorldConstants.WORLD_BOUNDARY_MAX)


func test_y_min_less_than_y_max() -> void:
	assert_float(WorldConstants.MIN_Y_POSITION).is_less(WorldConstants.MAX_Y_POSITION)


# ---- Zone IDs are distinct ----

func test_zone_ids_are_unique() -> void:
	var ids := [
		WorldConstants.ZONE_AERHEIM,
		WorldConstants.ZONE_GREEN_PLAINS,
		WorldConstants.ZONE_DARK_FOREST,
	]
	# No duplicates
	for i in range(ids.size()):
		for j in range(i + 1, ids.size()):
			assert_int(ids[i]).is_not_equal(ids[j])


# ---- Zone spawns ----

func test_all_zones_have_spawn_positions() -> void:
	assert_bool(WorldConstants.ZONE_SPAWNS.has(WorldConstants.ZONE_AERHEIM)).is_true()
	assert_bool(WorldConstants.ZONE_SPAWNS.has(WorldConstants.ZONE_GREEN_PLAINS)).is_true()
	assert_bool(WorldConstants.ZONE_SPAWNS.has(WorldConstants.ZONE_DARK_FOREST)).is_true()


func test_spawn_positions_within_boundaries() -> void:
	for zone_id in WorldConstants.ZONE_SPAWNS:
		var pos: Vector3 = WorldConstants.ZONE_SPAWNS[zone_id]
		assert_float(pos.x).is_greater_equal(WorldConstants.WORLD_BOUNDARY_MIN)
		assert_float(pos.x).is_less_equal(WorldConstants.WORLD_BOUNDARY_MAX)
		assert_float(pos.z).is_greater_equal(WorldConstants.WORLD_BOUNDARY_MIN)
		assert_float(pos.z).is_less_equal(WorldConstants.WORLD_BOUNDARY_MAX)


func test_default_spawn_matches_aerheim() -> void:
	assert_float(WorldConstants.DEFAULT_SPAWN.x).is_equal(WorldConstants.ZONE_SPAWNS[WorldConstants.ZONE_AERHEIM].x)
	assert_float(WorldConstants.DEFAULT_SPAWN.y).is_equal(WorldConstants.ZONE_SPAWNS[WorldConstants.ZONE_AERHEIM].y)
	assert_float(WorldConstants.DEFAULT_SPAWN.z).is_equal(WorldConstants.ZONE_SPAWNS[WorldConstants.ZONE_AERHEIM].z)


# ---- Zone name keys ----

func test_all_zones_have_name_keys() -> void:
	assert_bool(WorldConstants.ZONE_NAME_KEYS.has(WorldConstants.ZONE_AERHEIM)).is_true()
	assert_bool(WorldConstants.ZONE_NAME_KEYS.has(WorldConstants.ZONE_GREEN_PLAINS)).is_true()
	assert_bool(WorldConstants.ZONE_NAME_KEYS.has(WorldConstants.ZONE_DARK_FOREST)).is_true()


func test_zone_name_keys_are_non_empty() -> void:
	for zone_id in WorldConstants.ZONE_NAME_KEYS:
		var key: String = WorldConstants.ZONE_NAME_KEYS[zone_id]
		assert_str(key).is_not_empty()


# ---- get_zone_name() ----

func test_get_zone_name_known_zone_returns_non_empty() -> void:
	var name := WorldConstants.get_zone_name(WorldConstants.ZONE_AERHEIM)
	assert_str(name).is_not_empty()


func test_get_zone_name_unknown_zone_returns_fallback() -> void:
	var name := WorldConstants.get_zone_name(9999)
	assert_str(name).is_not_empty()


# ---- Entity type constants are distinct ----

func test_entity_types_are_unique() -> void:
	var types := [
		WorldConstants.ENTITY_TYPE_PLAYER,
		WorldConstants.ENTITY_TYPE_MONSTER,
		WorldConstants.ENTITY_TYPE_NPC,
		WorldConstants.ENTITY_TYPE_LOOT,
	]
	for i in range(types.size()):
		for j in range(i + 1, types.size()):
			assert_int(types[i]).is_not_equal(types[j])


# ---- Character class constants are distinct ----

func test_class_constants_are_unique() -> void:
	var classes := [
		WorldConstants.CLASS_WARRIOR,
		WorldConstants.CLASS_MAGE,
		WorldConstants.CLASS_ASSASSIN,
		WorldConstants.CLASS_CLERIC,
	]
	for i in range(classes.size()):
		for j in range(i + 1, classes.size()):
			assert_int(classes[i]).is_not_equal(classes[j])


# ---- Network timing ----

func test_server_tick_rate_positive() -> void:
	assert_int(WorldConstants.SERVER_TICK_RATE).is_greater(0)


func test_tick_ms_matches_rate() -> void:
	assert_int(WorldConstants.SERVER_TICK_MS).is_equal(1000 / WorldConstants.SERVER_TICK_RATE)
