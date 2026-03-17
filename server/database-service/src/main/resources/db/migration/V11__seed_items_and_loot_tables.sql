-- ============================================================
-- Phase 1.5: Seed item definitions and loot tables for
-- Green Plains monsters
-- ============================================================

-- =========================
-- Item Definitions
-- =========================

-- Weapons (type=0)
-- id=1: Wooden Sword
INSERT INTO item_definitions (name, type, level_req, class_req, rarity, base_attack, buy_price, sell_price, description)
VALUES ('Wooden Sword', 0, 1, 0, 0, 5, 10, 5, 'A simple wooden training sword. Better than bare fists.');

-- id=2: Iron Sword
INSERT INTO item_definitions (name, type, level_req, class_req, rarity, base_attack, buy_price, sell_price, description)
VALUES ('Iron Sword', 0, 5, 0, 1, 12, 100, 50, 'A sturdy iron blade forged by a village blacksmith.');

-- id=3: Steel Sword
INSERT INTO item_definitions (name, type, level_req, class_req, rarity, base_attack, buy_price, sell_price, description)
VALUES ('Steel Sword', 0, 10, 0, 2, 22, 500, 250, 'A finely tempered steel sword with a razor-sharp edge.');

-- Armor (type=1)
-- id=4: Leather Armor
INSERT INTO item_definitions (name, type, level_req, class_req, rarity, base_defense, buy_price, sell_price, description)
VALUES ('Leather Armor', 1, 1, NULL, 0, 3, 15, 7, 'Basic leather armor offering minimal protection.');

-- id=5: Chain Armor
INSERT INTO item_definitions (name, type, level_req, class_req, rarity, base_defense, buy_price, sell_price, description)
VALUES ('Chain Armor', 1, 5, NULL, 1, 8, 120, 60, 'Interlocking metal rings provide solid defense.');

-- id=6: Plate Armor
INSERT INTO item_definitions (name, type, level_req, class_req, rarity, base_defense, buy_price, sell_price, description)
VALUES ('Plate Armor', 1, 10, NULL, 2, 15, 600, 300, 'Heavy plate armor that can withstand powerful blows.');

-- Consumables (type=3)
-- id=7: Health Potion
INSERT INTO item_definitions (name, type, level_req, class_req, rarity, base_hp, buy_price, sell_price, stackable, max_stack, description)
VALUES ('Health Potion', 3, 1, NULL, 0, 50, 5, 2, TRUE, 20, 'A small red potion that restores 50 HP.');

-- id=8: Mana Potion
INSERT INTO item_definitions (name, type, level_req, class_req, rarity, base_mp, buy_price, sell_price, stackable, max_stack, description)
VALUES ('Mana Potion', 3, 1, NULL, 0, 30, 8, 3, TRUE, 20, 'A small blue potion that restores 30 MP.');


-- =========================
-- Loot Tables
-- =========================
-- Monster IDs from V10: 1=Slime, 2=Forest Mushroom, 3=Wild Boar, 4=Forest Wolf, 5=Stone Golem
-- Item IDs from above: 1=Wooden Sword, 2=Iron Sword, 3=Steel Sword,
--   4=Leather Armor, 5=Chain Armor, 6=Plate Armor, 7=Health Potion, 8=Mana Potion

-- Slime (monster_id=1)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (1, 7, 0.30, 1, 2);  -- Health Potion 30%, 1-2

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (1, 4, 0.05, 1, 1);  -- Leather Armor 5%, 1

-- Forest Mushroom (monster_id=2)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (2, 8, 0.25, 1, 2);  -- Mana Potion 25%, 1-2

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (2, 7, 0.20, 1, 1);  -- Health Potion 20%, 1

-- Wild Boar (monster_id=3)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (3, 1, 0.15, 1, 1);  -- Wooden Sword 15%, 1

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (3, 4, 0.10, 1, 1);  -- Leather Armor 10%, 1

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (3, 7, 0.25, 1, 2);  -- Health Potion 25%, 1-2

-- Forest Wolf (monster_id=4)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (4, 2, 0.10, 1, 1);  -- Iron Sword 10%, 1

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (4, 5, 0.08, 1, 1);  -- Chain Armor 8%, 1

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (4, 7, 0.30, 1, 3);  -- Health Potion 30%, 1-3

-- Stone Golem (monster_id=5)
INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (5, 3, 0.15, 1, 1);  -- Steel Sword 15%, 1

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (5, 6, 0.10, 1, 1);  -- Plate Armor 10%, 1

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (5, 2, 0.20, 1, 1);  -- Iron Sword 20%, 1

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (5, 7, 0.40, 2, 5);  -- Health Potion 40%, 2-5

INSERT INTO loot_table (monster_id, item_id, drop_chance, min_amount, max_amount)
VALUES (5, 8, 0.40, 2, 3);  -- Mana Potion 40%, 2-3
