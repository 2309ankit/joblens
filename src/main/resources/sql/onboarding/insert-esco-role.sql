INSERT INTO role_catalog (
    canonical_name, category, taxonomy_source, taxonomy_version, external_uri
) VALUES (
    :name, 'ESCO_OCCUPATION', 'ESCO', :version, :uri
)
ON CONFLICT (lower(canonical_name)) WHERE created_by_workspace_id IS NULL
DO UPDATE SET
    taxonomy_version = :version,
    external_uri = COALESCE(role_catalog.external_uri, :uri)
RETURNING id
