-- Move equipped items out of the bag grid (slot 0-99) to slot -1.
-- Previously, equipping an item left it at its original inventory slot,
-- causing it to appear in both the bag and equipment UI.
UPDATE inventory SET slot = -1
WHERE id IN (SELECT inventory_id FROM equipment);
