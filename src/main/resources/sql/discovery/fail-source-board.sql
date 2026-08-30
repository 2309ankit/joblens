UPDATE discovered_source_board
SET status = 'FAILED',
    failure_reason = :reason
WHERE source = :source
  AND source_key = :sourceKey
