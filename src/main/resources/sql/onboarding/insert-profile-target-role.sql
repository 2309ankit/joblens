INSERT INTO workspace_profile_target_role (
    profile_version_id, role_id, priority, selection_source
)
VALUES (
    :profileVersionId,
    :roleId,
    :priority,
    CASE WHEN EXISTS (
        SELECT 1
        FROM workspace_profile_role_suggestion
        WHERE profile_version_id = :profileVersionId
          AND role_id = :roleId
    ) THEN 'RESUME_SUGGESTION' ELSE 'USER_ADDED' END
)
