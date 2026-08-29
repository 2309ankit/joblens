DELETE FROM duplicate_cluster_member
WHERE cluster_id = :clusterId
  AND normalized_job_id NOT IN (:memberIds)
