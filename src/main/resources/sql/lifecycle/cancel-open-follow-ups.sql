UPDATE application_follow_up
SET status = 'CANCELLED', completed_on = NULL, updated_at = CURRENT_TIMESTAMP
WHERE application_id = :applicationId
  AND status = 'OPEN'
