SELECT role.canonical_name, role.category, suggestion.evidence_source,
       suggestion.evidence, suggestion.confidence
FROM workspace_profile_role_suggestion suggestion
JOIN role_catalog role ON role.id = suggestion.role_id
JOIN workspace_profile_version profile ON profile.id = suggestion.profile_version_id
WHERE suggestion.profile_version_id = :profileVersionId
  AND profile.workspace_id = :workspaceId
ORDER BY suggestion.priority, role.canonical_name
