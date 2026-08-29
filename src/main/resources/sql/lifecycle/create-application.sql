INSERT INTO job_application (
    normalized_job_id, candidate_profile_id, status, status_effective_date, note
) VALUES (:jobId, :candidateProfileId, 'SAVED', :effectiveDate, :note)
RETURNING id
