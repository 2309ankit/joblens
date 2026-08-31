UPDATE taxonomy_release
SET status = 'FAILED', active = FALSE, completed_at = CURRENT_TIMESTAMP
WHERE id = :releaseId AND status <> 'COMPLETE'
