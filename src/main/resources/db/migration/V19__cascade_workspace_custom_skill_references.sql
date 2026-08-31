ALTER TABLE candidate_skill
    DROP CONSTRAINT candidate_skill_skill_id_fkey,
    ADD CONSTRAINT candidate_skill_skill_id_fkey
        FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE;

ALTER TABLE workspace_profile_skill
    DROP CONSTRAINT workspace_profile_skill_skill_id_fkey,
    ADD CONSTRAINT workspace_profile_skill_skill_id_fkey
        FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE;
