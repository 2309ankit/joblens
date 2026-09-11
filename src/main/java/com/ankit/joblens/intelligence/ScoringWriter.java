package com.ankit.joblens.intelligence;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.Locale;
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
          load("sql/intelligence/upsert-job-score.sql"),
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
          s.normalizedJobId(),
          s.bestRole().targetRoleId(),
          s.bestRole().targetRoleName(),
          s.bestRole().policyVersion(),
          s.bestRole().calibrationPackCode(),
          s.bestRole().calibrationPackVersion(),
          s.bestRole().qualifiesRecommended());
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
          load("sql/intelligence/delete-job-role-scores.sql"),
          s.normalizedJobId(),
          s.candidateProfileId());
      for (JobScore.RoleScore role : s.roleScores()) {
        long roleScoreId =
            jdbc.queryForObject(
                load("sql/intelligence/insert-job-role-score.sql"),
                Long.class,
                s.normalizedJobId(),
                s.candidateProfileId(),
                role.targetRoleId(),
                role.targetRoleName().toLowerCase(Locale.ROOT),
                role.targetRoleName(),
                role.rolePriority(),
                role.policyVersion(),
                role.calibrationPackCode(),
                role.calibrationPackVersion(),
                role.total(),
                role.title(),
                role.skill(),
                role.sector(),
                role.seniority(),
                role.location(),
                role.employment(),
                role.salary(),
                role.freshness(),
                role.qualifiesRecommended(),
                s.normalizedJobId());
        for (JobScore.Reason reason : role.reasons()) {
          jdbc.update(
              load("sql/intelligence/insert-job-role-score-reason.sql"),
              roleScoreId,
              reason.category(),
              reason.points(),
              reason.text());
        }
      }
      jdbc.update(
          "UPDATE normalized_job SET scored_content_hash=normalized_content_hash WHERE id=?",
          s.normalizedJobId());
    }
  }
}
