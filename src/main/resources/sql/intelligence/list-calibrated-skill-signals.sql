SELECT skill.canonical_name, signal.signal_level
FROM role_calibration_skill_signal signal
JOIN skill ON skill.id = signal.skill_id
WHERE signal.pack_id = :packId
  AND signal.role_id = :roleId
ORDER BY signal.signal_level, skill.canonical_name
