INSERT INTO candidate_skill (candidate_profile_id, skill_id, status, importance)
SELECT :candidateProfileId, skill_id, status, importance
FROM workspace_profile_skill
WHERE profile_version_id = :profileVersionId
