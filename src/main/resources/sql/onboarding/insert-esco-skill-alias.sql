INSERT INTO skill_alias (skill_id, alias_name)
VALUES (:conceptId, :alias)
ON CONFLICT (skill_id, lower(alias_name)) DO NOTHING
