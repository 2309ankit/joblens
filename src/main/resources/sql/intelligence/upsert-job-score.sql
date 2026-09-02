INSERT INTO job_score(
    normalized_job_id, candidate_profile_id, total_score, technical_score,
    domain_score, seniority_score, location_score, employment_score,
    salary_score, freshness_score, normalized_content_hash,
    best_target_role_id, best_target_role_name, ranking_policy_version,
    calibration_pack_code, calibration_pack_version
) VALUES (
    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
    (SELECT normalized_content_hash FROM normalized_job WHERE id=?),
    ?, ?, ?, ?, ?
)
ON CONFLICT(normalized_job_id,candidate_profile_id)
DO UPDATE SET total_score=EXCLUDED.total_score,
              technical_score=EXCLUDED.technical_score,
              domain_score=EXCLUDED.domain_score,
              seniority_score=EXCLUDED.seniority_score,
              location_score=EXCLUDED.location_score,
              employment_score=EXCLUDED.employment_score,
              salary_score=EXCLUDED.salary_score,
              freshness_score=EXCLUDED.freshness_score,
              normalized_content_hash=EXCLUDED.normalized_content_hash,
              best_target_role_id=EXCLUDED.best_target_role_id,
              best_target_role_name=EXCLUDED.best_target_role_name,
              ranking_policy_version=EXCLUDED.ranking_policy_version,
              calibration_pack_code=EXCLUDED.calibration_pack_code,
              calibration_pack_version=EXCLUDED.calibration_pack_version,
              calculated_at=CURRENT_TIMESTAMP
