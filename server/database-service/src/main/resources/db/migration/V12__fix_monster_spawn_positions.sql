-- ============================================================
-- Fix monster spawn positions for Green Plains (map_id=2).
-- Original spawns were 400+ units from the player spawn at (200, 200),
-- far beyond the spatial grid's interest range (~100 units).
-- Relocate spawns to spread outward from the player spawn point.
-- ============================================================

-- Delete old Green Plains spawns and re-insert with corrected positions
DELETE FROM monster_spawns WHERE map_id = 2;

-- Slimes (Lv2, passive) — near spawn, easy first encounters
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 2, 230.0, 0.0, 230.0, 15.0, 4, 30000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 2, 170.0, 0.0, 240.0, 12.0, 3, 30000);

-- Forest Mushrooms (Lv4) — mid-range from spawn
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 2, 280.0, 0.0, 270.0, 15.0, 3, 35000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 2, 130.0, 0.0, 290.0, 12.0, 2, 35000);

-- Wild Boars (Lv6, aggressive) — deeper into the plains
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (3, 2, 320.0, 0.0, 310.0, 20.0, 3, 45000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (3, 2, 100.0, 0.0, 330.0, 18.0, 2, 45000);

-- Forest Wolves (Lv10, aggressive) — far from spawn
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (4, 2, 350.0, 0.0, 350.0, 25.0, 3, 60000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (4, 2, 70.0, 0.0, 360.0, 20.0, 2, 60000);

-- Stone Golem (Lv14, mini-boss) — edge of the plains
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (5, 2, 370.0, 0.0, 380.0, 5.0, 1, 120000);
