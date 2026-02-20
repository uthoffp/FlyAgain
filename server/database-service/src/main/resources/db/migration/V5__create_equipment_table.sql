CREATE TABLE equipment (
    character_id    BIGINT NOT NULL REFERENCES characters(id),
    slot_type       SMALLINT NOT NULL,
    inventory_id    BIGINT NOT NULL REFERENCES inventory(id),
    PRIMARY KEY (character_id, slot_type),

    CONSTRAINT chk_equipment_slot_type CHECK (slot_type BETWEEN 0 AND 6)
);
