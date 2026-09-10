INSERT INTO skill (
    canonical_name, category, taxonomy_source, taxonomy_version, external_uri
) VALUES (
    :name, 'ESCO_SKILL', 'ESCO', :version, :uri
)
ON CONFLICT (lower(canonical_name)) WHERE created_by_workspace_id IS NULL
DO UPDATE SET
    taxonomy_source = 'ESCO',
    taxonomy_version = :version,
    external_uri = COALESCE(skill.external_uri, :uri)
RETURNING id
