package com.ankit.joblens.intelligence;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

public class ScoringWriter implements ItemWriter<JobScore> {
  private final JdbcTemplate jdbc;

  public ScoringWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void write(Chunk<? extends JobScore> c) {
    for (var s : c) {
      jdbc.update(
          "INSERT INTO job_score(normalized_job_id,candidate_profile_id,total_score,technical_score,domain_score,seniority_score,location_score,employment_score,salary_score,freshness_score,normalized_content_hash) VALUES (?,?,?,?,?,?,?,?,?,?,(SELECT normalized_content_hash FROM normalized_job WHERE id=?)) ON CONFLICT(normalized_job_id,candidate_profile_id) DO UPDATE SET total_score=EXCLUDED.total_score,technical_score=EXCLUDED.technical_score,domain_score=EXCLUDED.domain_score,seniority_score=EXCLUDED.seniority_score,location_score=EXCLUDED.location_score,employment_score=EXCLUDED.employment_score,salary_score=EXCLUDED.salary_score,freshness_score=EXCLUDED.freshness_score,normalized_content_hash=EXCLUDED.normalized_content_hash,calculated_at=CURRENT_TIMESTAMP",
          s.normalizedJobId(),
          s.candidateProfileId(),
          s.total(),
          s.technical(),
          s.domain(),
          s.seniority(),
          s.location(),
          s.employment(),
          s.salary(),
          s.freshness(),
          s.normalizedJobId());
      long id =
          jdbc.queryForObject(
              "SELECT id FROM job_score WHERE normalized_job_id=? AND candidate_profile_id=?",
              Long.class,
              s.normalizedJobId(),
              s.candidateProfileId());
      jdbc.update("DELETE FROM job_score_reason WHERE job_score_id=?", id);
      for (var r : s.reasons())
        jdbc.update(
            "INSERT INTO job_score_reason(job_score_id,category,points,reason_text) VALUES (?,?,?,?)",
            id,
            r.category(),
            r.points(),
            r.text());
      jdbc.update(
          "UPDATE normalized_job SET scored_content_hash=normalized_content_hash WHERE id=?",
          s.normalizedJobId());
    }
  }
}
