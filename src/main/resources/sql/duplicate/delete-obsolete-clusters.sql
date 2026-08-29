DELETE FROM duplicate_cluster
WHERE cluster_key NOT IN (:desiredKeys)
