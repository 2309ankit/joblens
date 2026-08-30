SELECT id, version, status, summary, target_roles, target_domains, primary_location
FROM workspace_profile_version
WHERE workspace_id = :workspaceId
ORDER BY version DESC
LIMIT 1
