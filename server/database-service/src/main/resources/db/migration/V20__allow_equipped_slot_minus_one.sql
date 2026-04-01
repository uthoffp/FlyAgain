-- Allow slot = -1 for items that are equipped (not in the bag grid).
-- The equip flow sets slot = -1 when an item moves from bag to equipment.
ALTER TABLE inventory DROP CONSTRAINT chk_slot;
ALTER TABLE inventory ADD CONSTRAINT chk_slot CHECK (slot BETWEEN -1 AND 99);
