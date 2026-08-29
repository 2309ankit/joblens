SELECT id, application_id, status, due_date, completed_on
FROM application_follow_up
WHERE id = :id
FOR UPDATE
