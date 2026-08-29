SELECT id, source, external_job_id, title, company, location, description_text,
       employment_type, normalized_content_hash
FROM normalized_job
ORDER BY id
