SELECT id, from_status, to_status, effective_date, note, changed_at
FROM application_status_history
WHERE application_id = :id
ORDER BY id
