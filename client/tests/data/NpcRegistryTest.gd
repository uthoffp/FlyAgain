## NpcRegistryTest.gd
class_name NpcRegistryTest
extends GdUnitTestSuite


func test_get_npc_weapon_merchant() -> void:
	var npc := NpcRegistry.get_npc(1)
	assert_str(npc.get("name", "")).is_equal("NPC_WEAPON_MERCHANT")


func test_get_npc_unknown_returns_empty() -> void:
	var npc := NpcRegistry.get_npc(999)
	assert_bool(npc.is_empty()).is_true()


func test_get_shop_items() -> void:
	var items := NpcRegistry.get_shop_items(1)
	assert_int(items.size()).is_equal(3)
	assert_int(items[0]).is_equal(1)


func test_is_in_range_close() -> void:
	assert_bool(NpcRegistry.is_in_range(1, Vector3(487.0, 0.0, 514.0))).is_true()


func test_is_in_range_far() -> void:
	assert_bool(NpcRegistry.is_in_range(1, Vector3(600.0, 0.0, 600.0))).is_false()
