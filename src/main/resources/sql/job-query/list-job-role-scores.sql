SELECT score.id,
       score.target_role_name,
       score.role_priority,
       score.policy_version,
       pack.code AS calibration_pack_code,
       pack.version AS calibration_pack_version,
       pack.display_name AS calibration_pack_name,
       score.total_score,
       score.title_score,
       score.skill_score,
       score.sector_score,
       score.seniority_score,
       score.location_score,
       score.employment_score,
       score.salary_score,
       score.freshness_score
FROM job_role_score score
LEFT JOIN role_calibration_pack pack ON pack.id = score.calibration_pack_id
WHERE score.normalized_job_id = :id
  AND score.candidate_profile_id = :candidateProfileId
ORDER BY score.total_score DESC, score.role_priority
