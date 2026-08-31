package com.ankit.joblens.intelligence;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SkillCatalogService {
  private final JdbcTemplate jdbc;

  public SkillCatalogService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, SkillDefinition> load() {
    Map<String, SkillDefinition> result = new LinkedHashMap<>();
    jdbc.query(
        "SELECT id, canonical_name FROM skill WHERE created_by_workspace_id IS NULL",
        rs -> {
          var skill = new SkillDefinition(rs.getLong("id"), rs.getString("canonical_name"));
          result.put(key(skill.name()), skill);
        });
    jdbc.query(
        "SELECT a.alias_name, s.id, s.canonical_name FROM skill_alias a JOIN skill s ON s.id=a.skill_id WHERE s.created_by_workspace_id IS NULL",
        rs -> {
          var skill = new SkillDefinition(rs.getLong("id"), rs.getString("canonical_name"));
          result.put(key(rs.getString("alias_name")), skill);
        });
    return result;
  }

  static String key(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  public record SkillDefinition(long id, String name) {}
}
