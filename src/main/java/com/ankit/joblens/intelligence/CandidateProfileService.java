package com.ankit.joblens.intelligence;

import com.ankit.joblens.jdbc.ClasspathSql;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CandidateProfileService {
  private final NamedParameterJdbcTemplate jdbc;

  public CandidateProfileService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public CandidateProfileConfig loadDefault() {
    long profileId =
        jdbc.queryForObject(
            ClasspathSql.load("sql/profile/find-default-candidate-id.sql"), Map.of(), Long.class);
    return load(profileId);
  }

  public CandidateProfileConfig load(long profileId) {
    var base =
        jdbc.queryForMap(
            ClasspathSql.load("sql/profile/find-candidate-profile.sql"),
            Map.of("candidateProfileId", profileId));
    var skills = new LinkedHashMap<Long, CandidateProfileConfig.CandidateSkill>();
    jdbc.query(
        ClasspathSql.load("sql/profile/list-candidate-skills.sql"),
        Map.of("candidateProfileId", profileId),
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> {
              skills.put(
                  rs.getLong("id"),
                  new CandidateProfileConfig.CandidateSkill(
                      rs.getString("canonical_name"),
                      rs.getString("status"),
                      rs.getDouble("importance")));
            });
    var prefs = new LinkedHashMap<String, String>();
    jdbc.query(
        ClasspathSql.load("sql/profile/list-candidate-preferences.sql"),
        Map.of("candidateProfileId", profileId),
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> prefs.put(rs.getString(1), rs.getString(2)));
    var locations =
        jdbc.query(
            ClasspathSql.load("sql/profile/list-candidate-search-markets.sql"),
            Map.of("candidateProfileId", profileId),
            (resultSet, row) ->
                new CandidateProfileConfig.LocationPreference(
                    resultSet.getString("country_code"), resultSet.getString("location")));
    return new CandidateProfileConfig(
        ((Number) base.get("id")).longValue(),
        (String) base.get("primary_location"),
        locations,
        array(base.get("target_roles")),
        array(base.get("target_domains")),
        skills,
        prefs);
  }

  private static Set<String> array(Object value) {
    if (value instanceof String[] a)
      return java.util.Arrays.stream(a).map(String::toLowerCase).collect(Collectors.toSet());
    return Set.of();
  }
}
