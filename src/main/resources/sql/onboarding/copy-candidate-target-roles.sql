INSERT INTO candidate_target_role (
    candidate_profile_id, role_id, priority, selection_source
)
SELECT :candidateProfileId, role_id, priority, selection_source
FROM workspace_profile_target_role
WHERE profile_version_id = :profileVersionId
