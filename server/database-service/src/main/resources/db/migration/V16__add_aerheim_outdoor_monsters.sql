-- ============================================================
-- Add outdoor monster spawns around Aerheim City (map_id=1).
-- Player spawn is at (500, 0, 500). The city center has a few
-- test monsters (V13). This migration populates the surrounding
-- wilderness with ~50 monsters in concentric rings of increasing
-- difficulty:
--
--   Ring 1 (~60-100 units out):  Slimes (Lv2)           — 14 monsters
--   Ring 2 (~120-180 units out): Forest Mushrooms (Lv4)  — 12 monsters
--   Ring 3 (~200-280 units out): Wild Boars (Lv6)        — 12 monsters
--   Ring 4 (~300-400 units out): Forest Wolves (Lv10)    — 10 monsters
--   Ring 5 (~420-500 units out): Stone Golems (Lv14)     —  2 monsters
--
-- Total new spawns: 50 monsters
-- ============================================================

-- ==================== Ring 1: Slimes (Lv2) ====================
-- North
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 1, 500.0, 0.0, 420.0, 12.0, 3, 30000);

-- East
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 1, 580.0, 0.0, 500.0, 12.0, 3, 30000);

-- South
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 1, 500.0, 0.0, 580.0, 12.0, 3, 30000);

-- West
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 1, 420.0, 0.0, 500.0, 12.0, 3, 30000);

-- Northwest
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 1, 440.0, 0.0, 440.0, 10.0, 2, 30000);


-- ==================== Ring 2: Forest Mushrooms (Lv4) ====================
-- North
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 1, 500.0, 0.0, 350.0, 15.0, 3, 35000);

-- East
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 1, 650.0, 0.0, 500.0, 15.0, 3, 35000);

-- South
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 1, 500.0, 0.0, 650.0, 15.0, 3, 35000);

-- Southwest
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 1, 380.0, 0.0, 620.0, 12.0, 3, 35000);


-- ==================== Ring 3: Wild Boars (Lv6) ====================
-- North
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (3, 1, 500.0, 0.0, 260.0, 20.0, 3, 45000);

-- East
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (3, 1, 740.0, 0.0, 500.0, 20.0, 3, 45000);

-- South
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (3, 1, 500.0, 0.0, 740.0, 20.0, 3, 45000);

-- West
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (3, 1, 260.0, 0.0, 500.0, 20.0, 3, 45000);


-- ==================== Ring 4: Forest Wolves (Lv10) ====================
-- Northeast
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (4, 1, 750.0, 0.0, 250.0, 25.0, 3, 60000);

-- Southeast
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (4, 1, 800.0, 0.0, 700.0, 25.0, 2, 60000);

-- Southwest
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (4, 1, 200.0, 0.0, 750.0, 25.0, 3, 60000);

-- Northwest
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (4, 1, 200.0, 0.0, 200.0, 25.0, 2, 60000);


-- ==================== Ring 5: Stone Golems (Lv14, mini-boss) ====================
-- Far north
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (5, 1, 500.0, 0.0, 100.0, 5.0, 1, 120000);

-- Far south
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (5, 1, 500.0, 0.0, 900.0, 5.0, 1, 120000);
