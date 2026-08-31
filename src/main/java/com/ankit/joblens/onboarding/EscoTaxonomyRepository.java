package com.ankit.joblens.onboarding;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EscoTaxonomyRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public EscoTaxonomyRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Release startOrResume(String version) {
    return jdbc.queryForObject(
        load("sql/onboarding/start-taxonomy-release.sql"),
        Map.of("version", version),
        (resultSet, row) -> new Release(resultSet.getLong("id"), resultSet.getString("status")));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistPage(
      long releaseId, String version, String conceptType, EscoTaxonomyPage page) {
    if (!List.of("skill", "occupation").contains(conceptType)) {
      throw new IllegalArgumentException("Unsupported ESCO concept type: " + conceptType);
    }
    for (EscoTaxonomyPage.Concept concept : page.concepts()) {
      long conceptId = upsertConcept(version, conceptType, concept);
      List<String> aliases =
          new ArrayList<>(
              new LinkedHashSet<>(concept.alternativeLabels().stream().map(String::trim).toList()));
      for (String alias : aliases) {
        if (!alias.isBlank() && alias.length() <= 300) {
          jdbc.update(
              load(
                  conceptType.equals("skill")
                      ? "sql/onboarding/insert-esco-skill-alias.sql"
                      : "sql/onboarding/insert-esco-role-alias.sql"),
              Map.of("conceptId", conceptId, "alias", alias));
        }
      }
    }
    jdbc.update(
        load("sql/onboarding/update-taxonomy-release-count.sql"),
        new MapSqlParameterSource()
            .addValue("releaseId", releaseId)
            .addValue("conceptType", conceptType)
            .addValue("total", page.total()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void activate(long releaseId) {
    jdbc.update(
        load("sql/onboarding/deactivate-taxonomy-releases.sql"), Map.of("releaseId", releaseId));
    jdbc.update(
        load("sql/onboarding/activate-taxonomy-release.sql"), Map.of("releaseId", releaseId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(long releaseId) {
    jdbc.update(load("sql/onboarding/fail-taxonomy-release.sql"), Map.of("releaseId", releaseId));
  }

  private long upsertConcept(String version, String conceptType, EscoTaxonomyPage.Concept concept) {
    String updateSql =
        conceptType.equals("skill")
            ? "sql/onboarding/update-esco-skill.sql"
            : "sql/onboarding/update-esco-role.sql";
    var parameters =
        new MapSqlParameterSource()
            .addValue("version", version)
            .addValue("uri", concept.uri())
            .addValue("name", bounded(concept.preferredLabel(), 300));
    List<Long> updated =
        jdbc.query(load(updateSql), parameters, (resultSet, row) -> resultSet.getLong(1));
    if (!updated.isEmpty()) {
      return updated.getFirst();
    }
    String insertSql =
        conceptType.equals("skill")
            ? "sql/onboarding/insert-esco-skill.sql"
            : "sql/onboarding/insert-esco-role.sql";
    return jdbc.queryForObject(load(insertSql), parameters, Long.class);
  }

  private static String bounded(String value, int maximum) {
    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
  }

  public record Release(long id, String status) {
    public boolean complete() {
      return "COMPLETE".equals(status);
    }
  }
}
