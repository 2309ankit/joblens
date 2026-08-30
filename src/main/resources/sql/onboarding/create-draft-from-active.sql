INSERT INTO workspace_profile_version (
    workspace_id, version, status, summary, target_roles, target_domains,
    primary_location, resume_id
)
SELECT active.workspace_id,
       (SELECT COALESCE(MAX(version), 0) + 1
        FROM workspace_profile_version
        WHERE workspace_id = :workspaceId),
       'DRAFT', active.summary, active.target_roles, active.target_domains,
       active.primary_location, active.resume_id
FROM workspace_profile_version active
WHERE active.id = :profileVersionId
  AND active.workspace_id = :workspaceId
  AND active.status = 'ACTIVE'
RETURNING id
