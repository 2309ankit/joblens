UPDATE workspace_profile_version
SET target_roles = :targetRoles,
    target_domains = :targetDomains,
    primary_location = :primaryLocation
WHERE id = :profileVersionId
  AND workspace_id = :workspaceId
  AND status = 'DRAFT'
