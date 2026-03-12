-- Fix existing characters that were created with (0, 0, 0) position.
-- Move them to the Aerheim default spawn point (500, 0, 500).
UPDATE characters
SET pos_x = 500, pos_y = 0, pos_z = 500
WHERE pos_x = 0 AND pos_y = 0 AND pos_z = 0 AND is_deleted = FALSE;
