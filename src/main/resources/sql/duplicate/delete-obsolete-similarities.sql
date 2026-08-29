DELETE FROM job_similarity
WHERE algorithm_version = :algorithmVersion
  AND concat(left_job_id, ':', right_job_id) NOT IN (:pairKeys)
