INSERT INTO workspace_profile_skill (profile_version_id, skill_id, status, importance)
SELECT :draftProfileVersionId, skill_id, status, importance
FROM workspace_profile_skill
WHERE profile_version_id = :sourceProfileVersionId
