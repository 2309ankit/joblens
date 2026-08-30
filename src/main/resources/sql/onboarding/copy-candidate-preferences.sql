INSERT INTO candidate_preference (candidate_profile_id, preference_key, preference_value)
SELECT :candidateProfileId, preference_key, preference_value
FROM workspace_preference
WHERE workspace_id = :workspaceId
