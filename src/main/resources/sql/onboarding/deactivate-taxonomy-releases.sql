UPDATE taxonomy_release
SET active = FALSE
WHERE source = 'ESCO' AND active AND id <> :releaseId
