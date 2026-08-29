INSERT INTO application_status_history (
    application_id, from_status, to_status, effective_date, note
) VALUES (:applicationId, :fromStatus, :toStatus, :effectiveDate, :note)
RETURNING id
