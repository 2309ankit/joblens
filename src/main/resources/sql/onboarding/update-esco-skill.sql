UPDATE skill
SET taxonomy_version = :version,
    category = 'ESCO_SKILL'
WHERE external_uri = :uri
RETURNING id
