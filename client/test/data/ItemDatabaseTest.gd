## ItemDatabaseTest.gd
class_name ItemDatabaseTest
extends GdUnitTestSuite


func test_get_item_returns_valid_data() -> void:
	var item := ItemDatabase.get_item(1)
	assert_str(item.get("name", "")).is_equal("ITEM_WOODEN_SWORD")
	assert_int(item.get("type", -1)).is_equal(ItemDatabase.TYPE_WEAPON)
	assert_int(item.get("base_attack", 0)).is_equal(5)


func test_get_item_unknown_returns_empty() -> void:
	var item := ItemDatabase.get_item(999)
	assert_bool(item.is_empty()).is_true()


func test_all_8_items_defined() -> void:
	for id in range(1, 9):
		var item := ItemDatabase.get_item(id)
		assert_bool(item.is_empty()).is_false()


func test_get_equip_slot_weapon() -> void:
	var sword := ItemDatabase.get_item(1)
	assert_int(ItemDatabase.get_equip_slot(sword)).is_equal(ItemDatabase.EQUIP_WEAPON)


func test_get_equip_slot_armor() -> void:
	var armor := ItemDatabase.get_item(4)  # Leather Armor, subtype=1 (chest)
	assert_int(ItemDatabase.get_equip_slot(armor)).is_equal(ItemDatabase.EQUIP_CHEST)


func test_get_equip_slot_consumable_not_equipable() -> void:
	var potion := ItemDatabase.get_item(7)
	assert_int(ItemDatabase.get_equip_slot(potion)).is_equal(-1)


func test_is_equipable() -> void:
	assert_bool(ItemDatabase.is_equipable(ItemDatabase.get_item(1))).is_true()
	assert_bool(ItemDatabase.is_equipable(ItemDatabase.get_item(7))).is_false()
