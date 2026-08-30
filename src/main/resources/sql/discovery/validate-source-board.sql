UPDATE discovered_source_board
SET status = 'VALIDATED',
    failure_reason = NULL,
    validated_at = CURRENT_TIMESTAMP
WHERE source = :source
  AND source_key = :sourceKey
