CREATE TABLE monster_definitions (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(64) NOT NULL,
    level           SMALLINT NOT NULL,
    hp              INT NOT NULL,
    attack          SMALLINT NOT NULL,
    defense         SMALLINT NOT NULL,
    xp_reward       INT NOT NULL,
    aggro_range     REAL NOT NULL DEFAULT 10.0,
    attack_range    REAL NOT NULL DEFAULT 2.0,
    attack_speed_ms INT NOT NULL DEFAULT 2000,
    move_speed      REAL NOT NULL DEFAULT 3.0,

    CONSTRAINT chk_monster_level CHECK (level >= 1),
    CONSTRAINT chk_monster_hp CHECK (hp > 0),
    CONSTRAINT chk_monster_xp CHECK (xp_reward >= 0),
    CONSTRAINT chk_monster_aggro_range CHECK (aggro_range > 0),
    CONSTRAINT chk_monster_attack_range CHECK (attack_range > 0),
    CONSTRAINT chk_monster_attack_speed CHECK (attack_speed_ms > 0),
    CONSTRAINT chk_monster_move_speed CHECK (move_speed > 0)
);

CREATE TABLE monster_spawns (
    id              SERIAL PRIMARY KEY,
    monster_id      INT NOT NULL REFERENCES monster_definitions(id),
    map_id          SMALLINT NOT NULL,
    pos_x           REAL NOT NULL,
    pos_y           REAL NOT NULL,
    pos_z           REAL NOT NULL,
    spawn_radius    REAL NOT NULL DEFAULT 5.0,
    spawn_count     SMALLINT NOT NULL DEFAULT 1,
    respawn_ms      INT NOT NULL DEFAULT 30000,

    CONSTRAINT chk_spawn_radius CHECK (spawn_radius >= 0),
    CONSTRAINT chk_spawn_count CHECK (spawn_count >= 1),
    CONSTRAINT chk_respawn_ms CHECK (respawn_ms > 0)
);
