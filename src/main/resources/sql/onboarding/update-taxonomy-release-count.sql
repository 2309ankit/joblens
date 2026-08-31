UPDATE taxonomy_release
SET skill_count = CASE WHEN :conceptType = 'skill' THEN :total ELSE skill_count END,
    occupation_count = CASE WHEN :conceptType = 'occupation' THEN :total ELSE occupation_count END
WHERE id = :releaseId
