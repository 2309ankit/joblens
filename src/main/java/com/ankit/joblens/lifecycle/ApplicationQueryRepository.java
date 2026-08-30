package com.ankit.joblens.lifecycle;

import com.ankit.joblens.jdbc.ClasspathSql;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationQueryRepository {

  private static final String LIST_APPLICATIONS =
      ClasspathSql.load("sql/lifecycle-query/list-applications.sql");
  private static final String FIND_APPLICATION =
      ClasspathSql.load("sql/lifecycle-query/find-application.sql");
  private static final String FIND_HISTORY =
      ClasspathSql.load("sql/lifecycle-query/find-application-history.sql");
  private static final String FIND_FOLLOW_UPS =
      ClasspathSql.load("sql/lifecycle-query/find-application-follow-ups.sql");
  private static final String LIST_FOLLOW_UPS =
      ClasspathSql.load("sql/lifecycle-query/list-follow-ups.sql");
  private static final String FIND_FOLLOW_UP =
      ClasspathSql.load("sql/lifecycle-query/find-follow-up.sql");

  private final NamedParameterJdbcTemplate jdbc;

  public ApplicationQueryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> findApplications(String status) {
    return query(LIST_APPLICATIONS, new MapSqlParameterSource().addValue("status", status));
  }

  public Map<String, Object> findApplication(long id) {
    List<Map<String, Object>> rows = query(FIND_APPLICATION, Map.of("id", id));
    if (rows.isEmpty()) {
      return null;
    }
    Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
    result.put("history", query(FIND_HISTORY, Map.of("id", id)));
    result.put("followUps", query(FIND_FOLLOW_UPS, Map.of("id", id)));
    return result;
  }

  public List<Map<String, Object>> findFollowUps(String status, LocalDate dueOnOrBefore) {
    return query(
        LIST_FOLLOW_UPS,
        new MapSqlParameterSource()
            .addValue("status", status)
            .addValue("dueOnOrBefore", dueOnOrBefore));
  }

  public Map<String, Object> findFollowUp(long id) {
    List<Map<String, Object>> rows = query(FIND_FOLLOW_UP, Map.of("id", id));
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private List<Map<String, Object>> query(String sql, Map<String, ?> parameters) {
    return jdbc.query(sql, parameters, (rs, rowNum) -> row(rs));
  }

  private List<Map<String, Object>> query(String sql, MapSqlParameterSource parameters) {
    return jdbc.query(sql, parameters, (rs, rowNum) -> row(rs));
  }

  private static Map<String, Object> row(java.sql.ResultSet resultSet)
      throws java.sql.SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    var metadata = resultSet.getMetaData();
    for (int index = 1; index <= metadata.getColumnCount(); index++) {
      Object value = resultSet.getObject(index);
      if (value instanceof java.sql.Date date) {
        value = date.toLocalDate();
      }
      row.put(metadata.getColumnLabel(index), value);
    }
    return row;
  }
}
