-- ============================================================
-- Add monster spawns to Aerheim City (map_id=1) for testing.
-- Player spawn is at (500, 0, 500). Place monsters very close
-- so they are immediately visible on login.
-- ============================================================

-- Slimes (Lv2, passive) — directly around the city spawn point
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 1, 510.0, 0.0, 510.0, 8.0, 3, 30000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 1, 490.0, 0.0, 515.0, 8.0, 3, 30000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 1, 520.0, 0.0, 490.0, 8.0, 2, 30000);

-- Forest Mushrooms (Lv4) — slightly further out
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 1, 540.0, 0.0, 530.0, 10.0, 2, 35000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 1, 460.0, 0.0, 530.0, 10.0, 2, 35000);
