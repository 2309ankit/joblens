INSERT INTO role_alias (role_id, alias_name)
VALUES (:conceptId, :alias)
ON CONFLICT (role_id, lower(alias_name)) DO NOTHING
