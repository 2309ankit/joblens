SELECT s.canonical_name
FROM job_skill js
JOIN skill s ON s.id = js.skill_id
WHERE js.normalized_job_id = :id
ORDER BY s.canonical_name
