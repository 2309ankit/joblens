INSERT INTO workspace_profile_target_role (
    profile_version_id, role_id, priority, selection_source
)
SELECT :draftProfileVersionId, role_id, priority, selection_source
FROM workspace_profile_target_role
WHERE profile_version_id = :sourceProfileVersionId
ON CONFLICT (profile_version_id, role_id)
DO UPDATE SET priority = EXCLUDED.priority, selection_source = EXCLUDED.selection_source
