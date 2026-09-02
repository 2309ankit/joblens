INSERT INTO job_role_score(
    normalized_job_id, candidate_profile_id, target_role_id, target_role_key,
    target_role_name, role_priority, policy_version, calibration_pack_id,
    total_score, title_score, skill_score, sector_score, seniority_score,
    location_score, employment_score, salary_score, freshness_score,
    normalized_content_hash
) VALUES (
    ?, ?, ?, ?, ?, ?, ?,
    (SELECT id FROM role_calibration_pack WHERE code=? AND version=?),
    ?, ?, ?, ?, ?, ?, ?, ?, ?,
    (SELECT normalized_content_hash FROM normalized_job WHERE id=?)
)
RETURNING id
