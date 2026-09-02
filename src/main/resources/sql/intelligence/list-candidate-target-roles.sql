SELECT target.role_id,
       role.canonical_name,
       target.priority,
       pack.id AS pack_id,
       pack.code AS pack_code,
       pack.version AS pack_version,
       pack.display_name AS pack_name
FROM candidate_target_role target
JOIN role_catalog role ON role.id = target.role_id
LEFT JOIN role_calibration_pack_role mapping
  ON mapping.role_id = target.role_id
 AND EXISTS (
     SELECT 1 FROM role_calibration_pack active_pack
     WHERE active_pack.id = mapping.pack_id AND active_pack.active = TRUE
 )
LEFT JOIN role_calibration_pack pack
  ON pack.id = mapping.pack_id
 AND pack.active = TRUE
WHERE target.candidate_profile_id = :candidateProfileId
ORDER BY target.priority
