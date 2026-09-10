package com.ankit.joblens.onboarding;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileIntelligenceRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public ProfileIntelligenceRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<SkillOption> skillOptions(UUID workspaceId, String query) {
    return jdbc.query(
        load("sql/onboarding/list-skill-options.sql"),
        Map.of("workspaceId", workspaceId, "query", catalogQuery(query)),
        (resultSet, row) ->
            new SkillOption(
                resultSet.getString("canonical_name"),
                resultSet.getString("category"),
                resultSet.getBoolean("custom")));
  }

  public List<RoleOption> roleOptions(UUID workspaceId, String query) {
    return jdbc.query(
        load("sql/onboarding/list-role-options.sql"),
        Map.of("workspaceId", workspaceId, "query", catalogQuery(query)),
        (resultSet, row) ->
            new RoleOption(
                resultSet.getString("canonical_name"),
                resultSet.getString("category"),
                resultSet.getBoolean("custom")));
  }

  public List<SectorOption> sectorOptions(UUID workspaceId, String query) {
    return jdbc.query(
        load("sql/onboarding/list-sector-options.sql"),
        Map.of("workspaceId", workspaceId, "query", catalogQuery(query)),
        (resultSet, row) ->
            new SectorOption(
                resultSet.getString("canonical_name"),
                resultSet.getString("category"),
                resultSet.getBoolean("custom"),
                resultSet.getString("taxonomy_version")));
  }

  public List<ProfileIntelligenceExtractor.SkillDefinition> skillDefinitions(UUID workspaceId) {
    var definitions = new LinkedHashMap<Long, TaxonomyTerms>();
    jdbc.query(
        load("sql/onboarding/list-skill-taxonomy.sql"),
        Map.of("workspaceId", workspaceId),
        (RowCallbackHandler)
            resultSet -> {
              long id = resultSet.getLong("id");
              String name = resultSet.getString("canonical_name");
              String category = resultSet.getString("category");
              String taxonomyVersion = resultSet.getString("taxonomy_version");
              definitions
                  .computeIfAbsent(
                      id, ignored -> new TaxonomyTerms(id, name, category, taxonomyVersion))
                  .terms()
                  .add(resultSet.getString("term"));
            });
    return definitions.values().stream()
        .map(
            definition ->
                new ProfileIntelligenceExtractor.SkillDefinition(
                    definition.id(),
                    definition.name(),
                    definition.category(),
                    List.copyOf(definition.terms()),
                    definition.taxonomyVersion()))
        .toList();
  }

  public List<ProfileIntelligenceExtractor.RoleDefinition> roleDefinitions(UUID workspaceId) {
    var definitions = new LinkedHashMap<Long, TaxonomyTerms>();
    jdbc.query(
        load("sql/onboarding/list-role-taxonomy.sql"),
        Map.of("workspaceId", workspaceId),
        (RowCallbackHandler)
            resultSet -> {
              long id = resultSet.getLong("id");
              String name = resultSet.getString("canonical_name");
              String category = resultSet.getString("category");
              String taxonomyVersion = resultSet.getString("taxonomy_version");
              definitions
                  .computeIfAbsent(
                      id, ignored -> new TaxonomyTerms(id, name, category, taxonomyVersion))
                  .terms()
                  .add(resultSet.getString("term"));
            });
    return definitions.values().stream()
        .map(
            definition ->
                new ProfileIntelligenceExtractor.RoleDefinition(
                    definition.id(),
                    definition.name(),
                    definition.category(),
                    List.copyOf(definition.terms()),
                    definition.taxonomyVersion()))
        .toList();
  }

  public void saveSuggestions(
      long profileVersionId, ProfileIntelligenceExtractor.Extraction extraction) {
    extraction
        .skills()
        .forEach(
            skill ->
                jdbc.update(
                    load("sql/onboarding/insert-skill-suggestion.sql"),
                    new MapSqlParameterSource()
                        .addValue("profileVersionId", profileVersionId)
                        .addValue("skillId", skill.id())
                        .addValue("matchedTerm", skill.matchedTerm())
                        .addValue("evidence", skill.evidence())
                        .addValue("confidence", skill.confidence())
                        .addValue("evidenceSection", skill.evidenceSection())
                        .addValue("matchType", skill.matchType())
                        .addValue(
                            "extractorVersion", ProfileIntelligenceExtractor.EXTRACTOR_VERSION)
                        .addValue("taxonomyVersion", skill.taxonomyVersion())
                        .addValue("startOffset", skill.startOffset())
                        .addValue("endOffset", skill.endOffset())));
    extraction
        .roles()
        .forEach(
            role ->
                jdbc.update(
                    load("sql/onboarding/insert-role-suggestion.sql"),
                    new MapSqlParameterSource()
                        .addValue("profileVersionId", profileVersionId)
                        .addValue("roleId", role.id())
                        .addValue("evidenceSource", role.evidenceSource())
                        .addValue("evidence", role.evidence())
                        .addValue("confidence", role.confidence())
                        .addValue("priority", role.priority())
                        .addValue("matchType", role.matchType())
                        .addValue(
                            "extractorVersion", ProfileIntelligenceExtractor.EXTRACTOR_VERSION)
                        .addValue("taxonomyVersion", role.taxonomyVersion())
                        .addValue("startOffset", role.startOffset())
                        .addValue("endOffset", role.endOffset())));
    extraction
        .terms()
        .forEach(
            term ->
                jdbc.update(
                    load("sql/onboarding/insert-term-suggestion.sql"),
                    new MapSqlParameterSource()
                        .addValue("profileVersionId", profileVersionId)
                        .addValue("termKind", term.termKind())
                        .addValue("normalizedTerm", term.normalizedTerm())
                        .addValue("evidenceSection", term.evidenceSection())
                        .addValue("evidence", term.evidence())
                        .addValue("evidenceStrength", term.evidenceStrength())
                        .addValue("reviewState", term.reviewState())
                        .addValue(
                            "extractorVersion", ProfileIntelligenceExtractor.EXTRACTOR_VERSION)
                        .addValue("priority", term.priority())
                        .addValue("matchedCanonicalTerm", term.matchedCanonicalTerm())));
  }

  public ProfileIntelligence intelligence(UUID workspaceId, long profileVersionId) {
    var skillSuggestions =
        jdbc.query(
            load("sql/onboarding/find-profile-skill-suggestions.sql"),
            Map.of("workspaceId", workspaceId, "profileVersionId", profileVersionId),
            (resultSet, row) ->
                new ProfileIntelligence.SkillSuggestion(
                    resultSet.getString("canonical_name"),
                    resultSet.getString("category"),
                    resultSet.getString("matched_term"),
                    resultSet.getString("evidence"),
                    resultSet.getBigDecimal("confidence"),
                    resultSet.getString("evidence_section"),
                    resultSet.getString("match_type"),
                    resultSet.getString("extractor_version"),
                    resultSet.getString("taxonomy_version")));
    var roleSuggestions =
        jdbc.query(
            load("sql/onboarding/find-profile-role-suggestions.sql"),
            Map.of("workspaceId", workspaceId, "profileVersionId", profileVersionId),
            (resultSet, row) ->
                new ProfileIntelligence.RoleSuggestion(
                    resultSet.getString("canonical_name"),
                    resultSet.getString("category"),
                    resultSet.getString("evidence_source"),
                    resultSet.getString("evidence"),
                    resultSet.getBigDecimal("confidence"),
                    resultSet.getString("match_type"),
                    resultSet.getString("extractor_version"),
                    resultSet.getString("taxonomy_version")));
    var termSuggestions =
        jdbc.query(
            load("sql/onboarding/find-profile-term-suggestions.sql"),
            Map.of("workspaceId", workspaceId, "profileVersionId", profileVersionId),
            (resultSet, row) ->
                new ProfileIntelligence.TermSuggestion(
                    resultSet.getString("normalized_term"),
                    resultSet.getString("term_kind"),
                    resultSet.getString("evidence_section"),
                    resultSet.getString("evidence"),
                    resultSet.getBigDecimal("evidence_strength"),
                    resultSet.getString("review_state"),
                    resultSet.getString("matched_canonical_term")));
    return new ProfileIntelligence(skillSuggestions, roleSuggestions, termSuggestions);
  }

  public List<String> resolveOrCreateSkills(UUID workspaceId, List<String> requestedSkills) {
    if (requestedSkills == null) {
      return List.of();
    }
    var resolved = new LinkedHashMap<String, String>();
    for (String requested : requestedSkills) {
      NamedValue value = resolveSkill(workspaceId, normalize(requested, 100, "skill"));
      if (value != null) {
        resolved.putIfAbsent(value.name().toLowerCase(Locale.ROOT), value.name());
      }
    }
    return List.copyOf(resolved.values());
  }

  public List<NamedValue> resolveSkills(UUID workspaceId, List<String> skills) {
    return skills.stream().map(skill -> resolveSkill(workspaceId, skill)).toList();
  }

  public List<String> resolveOrCreateRoles(UUID workspaceId, List<String> requestedRoles) {
    return resolveOrCreateRoleValues(workspaceId, requestedRoles).stream()
        .map(NamedValue::name)
        .toList();
  }

  public List<NamedValue> resolveOrCreateRoleValues(UUID workspaceId, List<String> requestedRoles) {
    var values = new LinkedHashMap<String, NamedValue>();
    for (String requested : requestedRoles) {
      String normalized = normalize(requested, 150, "role");
      if (normalized.isBlank()) {
        continue;
      }
      NamedValue value = resolveRole(workspaceId, normalized);
      values.putIfAbsent(value.name().toLowerCase(Locale.ROOT), value);
    }
    return List.copyOf(values.values());
  }

  public List<NamedValue> resolveOrCreateSectors(UUID workspaceId, List<String> requestedSectors) {
    var values = new LinkedHashMap<String, NamedValue>();
    for (String requested : requestedSectors) {
      String normalized = normalize(requested, 100, "sector");
      if (normalized.isBlank()) {
        continue;
      }
      NamedValue value = resolveSector(workspaceId, normalized);
      values.putIfAbsent(value.name().toLowerCase(Locale.ROOT), value);
    }
    return List.copyOf(values.values());
  }

  private NamedValue resolveSkill(UUID workspaceId, String name) {
    if (name.isBlank()) {
      return null;
    }
    List<NamedValue> existing = queryNamed("sql/onboarding/resolve-skill.sql", workspaceId, name);
    return existing.isEmpty()
        ? createNamed("sql/onboarding/create-custom-skill.sql", workspaceId, name)
        : existing.getFirst();
  }

  private NamedValue resolveRole(UUID workspaceId, String name) {
    List<NamedValue> existing = queryNamed("sql/onboarding/resolve-role.sql", workspaceId, name);
    return existing.isEmpty()
        ? createNamed("sql/onboarding/create-custom-role.sql", workspaceId, name)
        : existing.getFirst();
  }

  private NamedValue resolveSector(UUID workspaceId, String name) {
    List<NamedValue> existing = queryNamed("sql/onboarding/resolve-sector.sql", workspaceId, name);
    return existing.isEmpty()
        ? createNamed("sql/onboarding/create-custom-sector.sql", workspaceId, name)
        : existing.getFirst();
  }

  private List<NamedValue> queryNamed(String sql, UUID workspaceId, String name) {
    return jdbc.query(
        load(sql),
        Map.of("workspaceId", workspaceId, "name", name),
        (resultSet, row) ->
            new NamedValue(resultSet.getLong("id"), resultSet.getString("canonical_name")));
  }

  private NamedValue createNamed(String sql, UUID workspaceId, String name) {
    return jdbc.queryForObject(
        load(sql),
        Map.of("workspaceId", workspaceId, "name", name),
        (resultSet, row) ->
            new NamedValue(resultSet.getLong("id"), resultSet.getString("canonical_name")));
  }

  private static String normalize(String value, int maximumLength, String field) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim().replaceAll("\\s+", " ");
    if (normalized.length() > maximumLength
        || normalized.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(
          "Each " + field + " must be plain text up to " + maximumLength + " characters");
    }
    return normalized;
  }

  private static String catalogQuery(String query) {
    String normalized = query == null ? "" : query.trim().replaceAll("\\s+", " ");
    if (normalized.length() > 150 || normalized.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(
          "Catalogue search must be plain text up to 150 characters");
    }
    return normalized;
  }

  public record NamedValue(long id, String name) {}

  private record TaxonomyTerms(
      long id, String name, String category, String taxonomyVersion, List<String> terms) {
    private TaxonomyTerms(long id, String name, String category, String taxonomyVersion) {
      this(id, name, category, taxonomyVersion, new ArrayList<>());
    }
  }
}
