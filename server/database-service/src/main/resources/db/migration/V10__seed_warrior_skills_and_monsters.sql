-- ============================================================
-- Phase 1.5: Seed data for Warrior skills and Green Plains monsters
-- ============================================================

-- Warrior Skills (class_req = 0)
-- Strike: Basic melee attack, available at level 1
INSERT INTO skill_definitions (name, class_req, level_req, max_level, mp_cost, cooldown_ms, base_damage, damage_per_level, range_units, description)
VALUES ('Strike', 0, 1, 5, 0, 1500, 10, 3, 2.0, 'A focused melee strike dealing 120% weapon damage.');

-- Shield Bash: Stronger hit with a stun effect (no stun in MVP, just damage)
INSERT INTO skill_definitions (name, class_req, level_req, max_level, mp_cost, cooldown_ms, base_damage, damage_per_level, range_units, description)
VALUES ('Shield Bash', 0, 3, 5, 10, 5000, 20, 4, 2.0, 'Bash the target with your shield, dealing heavy damage.');

-- Whirlwind: AoE attack (single-target in MVP, AoE in later phase)
INSERT INTO skill_definitions (name, class_req, level_req, max_level, mp_cost, cooldown_ms, base_damage, damage_per_level, range_units, description)
VALUES ('Whirlwind', 0, 5, 5, 20, 8000, 30, 5, 3.0, 'Spin in a fury, striking all nearby enemies.');

-- War Cry: Self-buff (damage-only in MVP, buff system in later phase)
INSERT INTO skill_definitions (name, class_req, level_req, max_level, mp_cost, cooldown_ms, base_damage, damage_per_level, range_units, description)
VALUES ('War Cry', 0, 8, 5, 15, 30000, 0, 0, 0.0, 'Let out a war cry, increasing your attack power for 30 seconds.');


-- Green Plains Monsters
-- Slime (Lv1-3, passive — low aggro range to simulate passive behavior)
INSERT INTO monster_definitions (name, level, hp, attack, defense, xp_reward, aggro_range, attack_range, attack_speed_ms, move_speed)
VALUES ('Slime', 2, 50, 8, 3, 15, 5.0, 2.0, 2500, 2.0);

-- Forest Mushroom (Lv3-5, passive)
INSERT INTO monster_definitions (name, level, hp, attack, defense, xp_reward, aggro_range, attack_range, attack_speed_ms, move_speed)
VALUES ('Forest Mushroom', 4, 80, 12, 5, 30, 5.0, 2.0, 2200, 1.5);

-- Wild Boar (Lv5-8, aggressive)
INSERT INTO monster_definitions (name, level, hp, attack, defense, xp_reward, aggro_range, attack_range, attack_speed_ms, move_speed)
VALUES ('Wild Boar', 6, 150, 18, 8, 60, 12.0, 2.5, 2000, 3.5);

-- Forest Wolf (Lv8-12, aggressive)
INSERT INTO monster_definitions (name, level, hp, attack, defense, xp_reward, aggro_range, attack_range, attack_speed_ms, move_speed)
VALUES ('Forest Wolf', 10, 250, 25, 12, 120, 15.0, 2.5, 1800, 4.0);

-- Stone Golem (Lv12-15, aggressive, mini-boss)
INSERT INTO monster_definitions (name, level, hp, attack, defense, xp_reward, aggro_range, attack_range, attack_speed_ms, move_speed)
VALUES ('Stone Golem', 14, 600, 35, 20, 300, 10.0, 3.0, 3000, 2.0);


-- Monster Spawns for Green Plains (zone/map_id = 2)
-- Slimes near zone entrance
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 2, 480.0, 0.0, 480.0, 15.0, 4, 30000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (1, 2, 520.0, 0.0, 450.0, 12.0, 3, 30000);

-- Forest Mushrooms in mid-field
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 2, 550.0, 0.0, 500.0, 15.0, 3, 35000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (2, 2, 450.0, 0.0, 550.0, 12.0, 2, 35000);

-- Wild Boars in deeper areas
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (3, 2, 600.0, 0.0, 550.0, 20.0, 3, 45000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (3, 2, 400.0, 0.0, 600.0, 18.0, 2, 45000);

-- Forest Wolves in far areas
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (4, 2, 650.0, 0.0, 650.0, 25.0, 3, 60000);

INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (4, 2, 350.0, 0.0, 700.0, 20.0, 2, 60000);

-- Stone Golem (mini-boss, single spawn, longer respawn)
INSERT INTO monster_spawns (monster_id, map_id, pos_x, pos_y, pos_z, spawn_radius, spawn_count, respawn_ms)
VALUES (5, 2, 500.0, 0.0, 750.0, 5.0, 1, 120000);
