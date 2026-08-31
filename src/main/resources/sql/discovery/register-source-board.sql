INSERT INTO discovered_source_board (source, source_key, canonical_url)
VALUES (:source, :sourceKey, :canonicalUrl)
ON CONFLICT (source, source_key)
DO UPDATE SET canonical_url = EXCLUDED.canonical_url,
              last_discovered_at = CURRENT_TIMESTAMP
RETURNING id
