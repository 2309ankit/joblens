UPDATE taxonomy_release
SET status = 'COMPLETE', active = TRUE, completed_at = CURRENT_TIMESTAMP
WHERE id = :releaseId
