INSERT INTO sector_catalog (
    canonical_name, category, taxonomy_source, created_by_workspace_id
) VALUES (
    :name, 'WORKSPACE_PRIVATE', 'WORKSPACE', :workspaceId
)
ON CONFLICT (created_by_workspace_id, lower(canonical_name))
WHERE created_by_workspace_id IS NOT NULL
DO UPDATE SET canonical_name = EXCLUDED.canonical_name
RETURNING id, canonical_name
