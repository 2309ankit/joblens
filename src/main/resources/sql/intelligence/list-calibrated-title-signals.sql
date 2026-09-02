SELECT signal_text, signal_level
FROM role_calibration_title_signal
WHERE pack_id = :packId
  AND role_id = :roleId
ORDER BY signal_level, signal_text
