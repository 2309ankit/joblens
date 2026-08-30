SELECT s.canonical_name
FROM workspace_profile_skill ps
JOIN skill s ON s.id = ps.skill_id
WHERE ps.profile_version_id = :profileVersionId
ORDER BY s.canonical_name
