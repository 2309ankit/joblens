package com.ankit.joblens.intelligence;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

public class SkillExtractionWriter implements ItemWriter<ExtractedJobSkills> {
  private final JdbcTemplate jdbc;

  public SkillExtractionWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void write(Chunk<? extends ExtractedJobSkills> chunk) {
    for (var job : chunk) {
      jdbc.update("DELETE FROM job_skill WHERE normalized_job_id=?", job.normalizedJobId());
      for (var skill : job.matches())
        jdbc.update(
            "INSERT INTO job_skill(normalized_job_id,skill_id,mention_count,evidence) VALUES (?,?,?,?)",
            job.normalizedJobId(),
            skill.skillId(),
            skill.mentionCount(),
            skill.evidence());
      jdbc.update(
          "UPDATE normalized_job SET skill_extraction_hash=? WHERE id=?",
          job.contentHash(),
          job.normalizedJobId());
    }
  }
}
