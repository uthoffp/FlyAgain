CREATE TABLE loot_table (
    id              SERIAL PRIMARY KEY,
    monster_id      INT NOT NULL REFERENCES monster_definitions(id),
    item_id         INT NOT NULL REFERENCES item_definitions(id),
    drop_chance     REAL NOT NULL,
    min_amount      SMALLINT NOT NULL DEFAULT 1,
    max_amount      SMALLINT NOT NULL DEFAULT 1,

    CONSTRAINT chk_loot_drop_chance CHECK (drop_chance > 0 AND drop_chance <= 1.0),
    CONSTRAINT chk_loot_min_amount CHECK (min_amount >= 1),
    CONSTRAINT chk_loot_max_amount CHECK (max_amount >= min_amount)
);

CREATE INDEX idx_loot_monster ON loot_table(monster_id);
