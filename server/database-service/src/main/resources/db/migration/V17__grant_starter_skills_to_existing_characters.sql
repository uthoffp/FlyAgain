-- ============================================================
-- Grant starter skills to existing characters that have none.
-- New characters get starter skills automatically on creation;
-- this migration back-fills characters created before that logic.
-- ============================================================

INSERT INTO character_skills (character_id, skill_id, skill_level)
SELECT c.id, sd.id, 1
FROM characters c
JOIN skill_definitions sd ON sd.class_req = c.class AND sd.level_req = 1
WHERE c.is_deleted = FALSE
  AND NOT EXISTS (
    SELECT 1 FROM character_skills cs
    WHERE cs.character_id = c.id AND cs.skill_id = sd.id
  );
