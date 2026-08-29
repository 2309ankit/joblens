SELECT id, source_history_id, follow_up_type, generation_version,
       due_date, status, completed_on, created_at, updated_at
FROM application_follow_up
WHERE application_id = :id
ORDER BY due_date, id
