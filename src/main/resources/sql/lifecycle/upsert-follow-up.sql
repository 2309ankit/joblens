INSERT INTO application_follow_up (
    application_id, source_history_id, follow_up_type, generation_version, due_date
) VALUES (
    :applicationId, :historyId, :followUpType, :generationVersion, :dueDate
)
ON CONFLICT (application_id, source_history_id, follow_up_type, generation_version)
DO UPDATE SET
    due_date = EXCLUDED.due_date,
    updated_at = CASE
        WHEN application_follow_up.due_date IS DISTINCT FROM EXCLUDED.due_date
        THEN CURRENT_TIMESTAMP ELSE application_follow_up.updated_at END
