SELECT skill.canonical_name, skill.category
FROM workspace_profile_skill profile_skill
JOIN skill ON skill.id = profile_skill.skill_id
WHERE profile_skill.profile_version_id = :profileVersionId
ORDER BY profile_skill.importance DESC, skill.canonical_name
