CREATE TABLE inventory (
    id              BIGSERIAL PRIMARY KEY,
    character_id    BIGINT NOT NULL REFERENCES characters(id),
    slot            SMALLINT NOT NULL,
    item_id         INT NOT NULL REFERENCES item_definitions(id),
    amount          SMALLINT NOT NULL DEFAULT 1,
    enhancement     SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (character_id, slot),

    CONSTRAINT chk_slot CHECK (slot BETWEEN 0 AND 99),
    CONSTRAINT chk_amount CHECK (amount >= 1),
    CONSTRAINT chk_enhancement CHECK (enhancement BETWEEN 0 AND 10)
);

CREATE INDEX idx_inventory_character ON inventory(character_id);
