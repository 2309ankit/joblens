UPDATE job_application
SET status = :status,
    status_effective_date = :effectiveDate,
    applied_on = CASE
        WHEN :status = 'APPLIED' AND applied_on IS NULL THEN :effectiveDate
        ELSE applied_on
    END,
    note = COALESCE(:note, note),
    updated_at = CURRENT_TIMESTAMP
WHERE id = :id
