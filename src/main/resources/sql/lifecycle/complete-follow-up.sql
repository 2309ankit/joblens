UPDATE application_follow_up
SET status = 'COMPLETED', completed_on = :completedOn, updated_at = CURRENT_TIMESTAMP
WHERE id = :id
