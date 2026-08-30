INSERT INTO job_view (normalized_job_id, candidate_profile_id)
VALUES (:jobId, :candidateProfileId)
ON CONFLICT (normalized_job_id, candidate_profile_id)
DO UPDATE SET
    last_viewed_at = CURRENT_TIMESTAMP,
    view_count = job_view.view_count + 1
