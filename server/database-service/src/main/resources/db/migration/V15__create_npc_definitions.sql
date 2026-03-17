-- ============================================================
-- Phase 1.6: NPC definitions and shop inventories
-- ============================================================

CREATE TABLE npc_definitions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    zone_id INT NOT NULL,
    pos_x REAL NOT NULL DEFAULT 0,
    pos_y REAL NOT NULL DEFAULT 0,
    pos_z REAL NOT NULL DEFAULT 0,
    npc_type INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_npc_type CHECK (npc_type BETWEEN 0 AND 2)
);

CREATE TABLE npc_shop_items (
    id SERIAL PRIMARY KEY,
    npc_id INT NOT NULL REFERENCES npc_definitions(id),
    item_def_id INT NOT NULL REFERENCES item_definitions(id),
    UNIQUE (npc_id, item_def_id)
);

CREATE INDEX idx_npc_shop_items_npc ON npc_shop_items(npc_id);

-- Weapon Merchant (Aerheim market, zone_id=0)
INSERT INTO npc_definitions (name, zone_id, pos_x, pos_y, pos_z, npc_type)
VALUES ('Weapon Merchant', 0, 505.0, 0.0, 495.0, 0);

-- Armor Merchant
INSERT INTO npc_definitions (name, zone_id, pos_x, pos_y, pos_z, npc_type)
VALUES ('Armor Merchant', 0, 510.0, 0.0, 495.0, 0);

-- Potion Merchant
INSERT INTO npc_definitions (name, zone_id, pos_x, pos_y, pos_z, npc_type)
VALUES ('Potion Merchant', 0, 515.0, 0.0, 495.0, 0);

-- Weapon Merchant (npc_id=1) sells swords
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (1, 1);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (1, 2);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (1, 3);

-- Armor Merchant (npc_id=2) sells armor
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (2, 4);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (2, 5);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (2, 6);

-- Potion Merchant (npc_id=3) sells potions
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (3, 7);
INSERT INTO npc_shop_items (npc_id, item_def_id) VALUES (3, 8);
