INSERT INTO workspace_profile_skill (profile_version_id, skill_id)
SELECT :profileVersionId, id FROM skill WHERE canonical_name = :skill
ON CONFLICT DO NOTHING
