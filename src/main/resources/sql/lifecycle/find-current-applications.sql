SELECT a.id, a.status, a.status_effective_date,
       h.id AS history_id
FROM job_application a
JOIN LATERAL (
    SELECT id
    FROM application_status_history
    WHERE application_id = a.id
    ORDER BY id DESC
    LIMIT 1
) h ON true
WHERE a.status_effective_date <= :businessDate
  AND (CAST(:candidateProfileId AS BIGINT) IS NULL OR a.candidate_profile_id = :candidateProfileId)
ORDER BY a.id
FOR UPDATE OF a
