package com.ankit.joblens.intelligence;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CandidateProfileService {
  private final JdbcTemplate jdbc;

  public CandidateProfileService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public CandidateProfileConfig loadDefault() {
    var base =
        jdbc.queryForMap(
            "SELECT id,primary_location,target_roles,target_domains FROM candidate_profile WHERE name='default' AND active=true");
    var skills = new LinkedHashMap<Long, CandidateProfileConfig.CandidateSkill>();
    jdbc.query(
        "SELECT s.id,s.canonical_name,cs.status,cs.importance FROM candidate_skill cs JOIN skill s ON s.id=cs.skill_id WHERE cs.candidate_profile_id=?",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> {
              skills.put(
                  rs.getLong("id"),
                  new CandidateProfileConfig.CandidateSkill(
                      rs.getString("canonical_name"),
                      rs.getString("status"),
                      rs.getDouble("importance")));
            },
        base.get("id"));
    var prefs = new LinkedHashMap<String, String>();
    jdbc.query(
        "SELECT preference_key,preference_value FROM candidate_preference WHERE candidate_profile_id=?",
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> prefs.put(rs.getString(1), rs.getString(2)),
        base.get("id"));
    return new CandidateProfileConfig(
        ((Number) base.get("id")).longValue(),
        (String) base.get("primary_location"),
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
