package com.ankit.joblens.intelligence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SkillCatalogService {
  private static final String ACTIVE_FILTER =
      """
      s.created_by_workspace_id IS NULL
      AND (s.taxonomy_source <> 'ESCO' OR EXISTS (
          SELECT 1 FROM taxonomy_release r
          WHERE r.source = 'ESCO' AND r.version = s.taxonomy_version AND r.active
      ))
      """;

  private final JdbcTemplate jdbc;
  private volatile Snapshot cached;

  public SkillCatalogService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Snapshot load() {
    String signature =
        jdbc.queryForObject(
            "SELECT COALESCE((SELECT version FROM taxonomy_release WHERE source='ESCO' AND active), 'JOBLENS')",
            String.class);
    Snapshot current = cached;
    if (current != null && current.signature().equals(signature)) {
      return current;
    }
    synchronized (this) {
      current = cached;
      if (current != null && current.signature().equals(signature)) {
        return current;
      }
      var terms = new ArrayList<CatalogTerm>();
      jdbc.query(
          "SELECT s.id, s.canonical_name FROM skill s WHERE " + ACTIVE_FILTER,
          resultSet -> {
            var skill =
                new SkillDefinition(resultSet.getLong("id"), resultSet.getString("canonical_name"));
            terms.add(new CatalogTerm(key(skill.name()), skill));
          });
      jdbc.query(
          "SELECT a.alias_name, s.id, s.canonical_name FROM skill_alias a JOIN skill s ON s.id=a.skill_id WHERE "
              + ACTIVE_FILTER,
          resultSet -> {
            var skill =
                new SkillDefinition(resultSet.getLong("id"), resultSet.getString("canonical_name"));
            terms.add(new CatalogTerm(key(resultSet.getString("alias_name")), skill));
          });
      cached = new Snapshot(signature, List.copyOf(terms));
      return cached;
    }
  }

  static String key(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  public record Snapshot(String signature, List<CatalogTerm> terms) {}

  public record CatalogTerm(String phrase, SkillDefinition skill) {}

  public record SkillDefinition(long id, String name) {}
}
