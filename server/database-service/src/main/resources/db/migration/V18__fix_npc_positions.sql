-- Fix NPC merchant positions to match their building locations in Aerheim.
-- Aerheim CENTER is (500, 0, 500). NPC markers are placed in front of shops.

-- Weapon Merchant (npc_id=1): in front of Shop_Weapons building
UPDATE npc_definitions SET pos_x = 487.0, pos_y = 0.0, pos_z = 514.0 WHERE id = 1;

-- Armor Merchant (npc_id=2): in front of Shop_Armor building
UPDATE npc_definitions SET pos_x = 487.0, pos_y = 0.0, pos_z = 520.0 WHERE id = 2;

-- Potion Merchant (npc_id=3): in front of Shop_Potions building
UPDATE npc_definitions SET pos_x = 487.0, pos_y = 0.0, pos_z = 526.0 WHERE id = 3;
