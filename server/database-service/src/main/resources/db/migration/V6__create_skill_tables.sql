CREATE TABLE skill_definitions (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(64) NOT NULL,
    class_req       SMALLINT NOT NULL,
    level_req       SMALLINT NOT NULL,
    max_level       SMALLINT NOT NULL DEFAULT 5,
    mp_cost         SMALLINT NOT NULL,
    cooldown_ms     INT NOT NULL,
    base_damage     SMALLINT NOT NULL DEFAULT 0,
    damage_per_level SMALLINT NOT NULL DEFAULT 0,
    range_units     REAL NOT NULL DEFAULT 2.0,
    description     TEXT,

    CONSTRAINT chk_skill_class_req CHECK (class_req BETWEEN 0 AND 3),
    CONSTRAINT chk_skill_level_req CHECK (level_req >= 1),
    CONSTRAINT chk_skill_max_level CHECK (max_level >= 1),
    CONSTRAINT chk_skill_mp_cost CHECK (mp_cost >= 0),
    CONSTRAINT chk_skill_cooldown CHECK (cooldown_ms >= 0),
    CONSTRAINT chk_skill_range CHECK (range_units > 0)
);

CREATE TABLE character_skills (
    character_id    BIGINT NOT NULL REFERENCES characters(id),
    skill_id        INT NOT NULL REFERENCES skill_definitions(id),
    skill_level     SMALLINT NOT NULL DEFAULT 1,
    PRIMARY KEY (character_id, skill_id),

    CONSTRAINT chk_character_skill_level CHECK (skill_level >= 1)
);
