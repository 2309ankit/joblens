UPDATE role_catalog
SET taxonomy_version = :version,
    category = 'ESCO_OCCUPATION'
WHERE external_uri = :uri
RETURNING id
