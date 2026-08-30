package com.ankit.joblens.profile;

import com.ankit.joblens.jdbc.ClasspathSql;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class ResumeProfileRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public ResumeProfileRepository(NamedParameterJdbcTemplate j) {
    jdbc = j;
  }

  long id() {
    return jdbc.queryForObject(
        ClasspathSql.load("sql/profile/find-default.sql"), Map.of(), Long.class);
  }

  String summary(long id) {
    return jdbc.queryForObject(
        ClasspathSql.load("sql/profile/find-summary.sql"), Map.of("profileId", id), String.class);
  }

  List<String> skills(long id) {
    return jdbc.query(
        ClasspathSql.load("sql/profile/find-skills.sql"),
        Map.of("profileId", id),
        (r, n) -> r.getString(1));
  }

  List<String> catalog() {
    return jdbc.query(
        ClasspathSql.load("sql/profile/catalog.sql"), Map.of(), (r, n) -> r.getString(1));
  }

  void save(long id, String s, List<String> names) {
    var p = new MapSqlParameterSource("profileId", id).addValue("summary", s);
    jdbc.update(ClasspathSql.load("sql/profile/update-summary.sql"), p);
    jdbc.update(ClasspathSql.load("sql/profile/clear-skills.sql"), p);
    for (String n : names)
      jdbc.update(ClasspathSql.load("sql/profile/add-skill.sql"), p.addValue("skill", n));
  }
}
