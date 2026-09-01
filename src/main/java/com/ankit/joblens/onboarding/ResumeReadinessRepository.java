package com.ankit.joblens.onboarding;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ResumeReadinessRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public ResumeReadinessRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(
      long profileVersionId,
      String contentType,
      ResumeReadinessAdvisor.DraftAssessment assessment) {
    long assessmentId =
        jdbc.queryForObject(
            load("sql/onboarding/insert-readiness-assessment.sql"),
            new MapSqlParameterSource()
                .addValue("profileVersionId", profileVersionId)
                .addValue("assessmentVersion", ResumeReadinessAdvisor.ASSESSMENT_VERSION)
                .addValue("status", assessment.status())
                .addValue("score", assessment.score())
                .addValue("contentType", contentType)
                .addValue("extractedCharacterCount", assessment.extractedCharacterCount())
                .addValue("wordCount", assessment.wordCount()),
            Long.class);
    for (ResumeReadinessAssessment.Finding finding : assessment.findings()) {
      jdbc.update(
          load("sql/onboarding/insert-readiness-finding.sql"),
          new MapSqlParameterSource()
              .addValue("assessmentId", assessmentId)
              .addValue("code", finding.code())
              .addValue("category", finding.category())
              .addValue("severity", finding.severity())
              .addValue("message", finding.message())
              .addValue("remediation", finding.remediation())
              .addValue("evidence", finding.evidence())
              .addValue("scoreDeduction", finding.scoreDeduction()));
    }
    jdbc.update(
        load("sql/onboarding/update-resume-readiness-status.sql"),
        Map.of(
            "profileVersionId",
            profileVersionId,
            "status",
            assessment.status(),
            "validationMessage",
            "Machine-readability score " + assessment.score()));
  }

  public ResumeReadinessAssessment find(UUID workspaceId, long profileVersionId) {
    var rows =
        jdbc.query(
            load("sql/onboarding/find-readiness-assessment.sql"),
            Map.of("workspaceId", workspaceId, "profileVersionId", profileVersionId),
            (resultSet, row) ->
                new StoredAssessment(
                    resultSet.getLong("id"),
                    resultSet.getLong("profile_version_id"),
                    resultSet.getString("assessment_version"),
                    resultSet.getString("status"),
                    resultSet.getInt("score"),
                    resultSet.getString("content_type"),
                    resultSet.getInt("extracted_character_count"),
                    resultSet.getInt("word_count"),
                    resultSet.getObject("acknowledged_at", OffsetDateTime.class),
                    resultSet.getObject("created_at", OffsetDateTime.class)));
    if (rows.isEmpty()) {
      throw new IllegalStateException("No readability assessment exists for this profile version");
    }
    StoredAssessment stored = rows.getFirst();
    List<ResumeReadinessAssessment.Finding> findings =
        jdbc.query(
            load("sql/onboarding/find-readiness-findings.sql"),
            Map.of("assessmentId", stored.id()),
            (resultSet, row) ->
                new ResumeReadinessAssessment.Finding(
                    resultSet.getString("finding_code"),
                    resultSet.getString("category"),
                    resultSet.getString("severity"),
                    resultSet.getString("message"),
                    resultSet.getString("remediation"),
                    resultSet.getString("evidence"),
                    resultSet.getInt("score_deduction")));
    return new ResumeReadinessAssessment(
        stored.profileVersionId(),
        stored.version(),
        stored.status(),
        stored.score(),
        stored.contentType(),
        stored.characters(),
        stored.words(),
        stored.acknowledgedAt(),
        stored.createdAt(),
        findings);
  }

  public ResumeReadinessAssessment acknowledge(UUID workspaceId, long profileVersionId) {
    int updated =
        jdbc.update(
            load("sql/onboarding/acknowledge-readiness.sql"),
            Map.of("workspaceId", workspaceId, "profileVersionId", profileVersionId));
    if (updated != 1) {
      ResumeReadinessAssessment current = find(workspaceId, profileVersionId);
      if (!"REVIEW_REQUIRED".equals(current.status())) {
        throw new IllegalStateException(
            "This profile does not require readability acknowledgement");
      }
    }
    return find(workspaceId, profileVersionId);
  }

  public void requireActivationAllowed(UUID workspaceId, long profileVersionId) {
    ResumeReadinessAssessment assessment;
    try {
      assessment = find(workspaceId, profileVersionId);
    } catch (IllegalStateException missingLegacyAssessment) {
      return;
    }
    if (assessment.acknowledgementRequired()) {
      throw new IllegalStateException(
          "Review and acknowledge the resume readability findings before activation");
    }
  }

  public void copy(long sourceProfileVersionId, long draftProfileVersionId) {
    jdbc.update(
        load("sql/onboarding/copy-readiness-assessment.sql"),
        Map.of(
            "sourceProfileVersionId", sourceProfileVersionId,
            "draftProfileVersionId", draftProfileVersionId));
  }

  private record StoredAssessment(
      long id,
      long profileVersionId,
      String version,
      String status,
      int score,
      String contentType,
      int characters,
      int words,
      OffsetDateTime acknowledgedAt,
      OffsetDateTime createdAt) {}
}
