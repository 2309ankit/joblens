package com.ankit.joblens.batchapi;

import com.ankit.joblens.jdbc.ClasspathSql;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class DuplicateQueryRepository {

  private static final String LIST_CLUSTERS =
      ClasspathSql.load("sql/duplicate-query/list-clusters.sql");
  private static final String FIND_CLUSTER =
      ClasspathSql.load("sql/duplicate-query/find-cluster.sql");
  private static final String FIND_CLUSTER_MEMBERS =
      ClasspathSql.load("sql/duplicate-query/find-cluster-members.sql");
  private static final String FIND_CLUSTER_EVIDENCE =
      ClasspathSql.load("sql/duplicate-query/find-cluster-evidence.sql");
  private static final String FIND_CLUSTER_FOR_JOB =
      ClasspathSql.load("sql/duplicate-query/find-cluster-for-job.sql");
  private static final String LIST_SIMILARITIES =
      ClasspathSql.load("sql/duplicate-query/list-similarities.sql");
  private static final String FIND_SIMILARITY =
      ClasspathSql.load("sql/duplicate-query/find-similarity.sql");
  private static final String FIND_SIMILARITIES_FOR_JOB =
      ClasspathSql.load("sql/duplicate-query/find-similarities-for-job.sql");

  private final NamedParameterJdbcTemplate jdbc;

  public DuplicateQueryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> findClusters() {
    return query(LIST_CLUSTERS, Map.of());
  }

  public Map<String, Object> findCluster(long id) {
    return first(query(FIND_CLUSTER, Map.of("id", id)));
  }

  public List<Map<String, Object>> findClusterMembers(long id) {
    return query(FIND_CLUSTER_MEMBERS, Map.of("id", id));
  }

  public List<Map<String, Object>> findClusterEvidence(long id) {
    return query(FIND_CLUSTER_EVIDENCE, Map.of("id", id));
  }

  public Map<String, Object> findClusterForJob(long jobId) {
    return first(query(FIND_CLUSTER_FOR_JOB, Map.of("jobId", jobId)));
  }

  public List<Map<String, Object>> findSimilarities(String decision, BigDecimal minimumScore) {
    return query(
        LIST_SIMILARITIES,
        new MapSqlParameterSource()
            .addValue("decision", decision)
            .addValue("minimumScore", minimumScore));
  }

  public Map<String, Object> findSimilarity(long id) {
    return first(query(FIND_SIMILARITY, Map.of("id", id)));
  }

  public List<Map<String, Object>> findSimilaritiesForJob(long jobId) {
    return query(FIND_SIMILARITIES_FOR_JOB, Map.of("jobId", jobId));
  }

  private List<Map<String, Object>> query(String sql, Map<String, ?> parameters) {
    return jdbc.query(sql, parameters, (rs, rowNum) -> row(rs));
  }

  private List<Map<String, Object>> query(String sql, MapSqlParameterSource parameters) {
    return jdbc.query(sql, parameters, (rs, rowNum) -> row(rs));
  }

  private static Map<String, Object> first(List<Map<String, Object>> rows) {
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private static Map<String, Object> row(java.sql.ResultSet resultSet)
      throws java.sql.SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    var metadata = resultSet.getMetaData();
    for (int index = 1; index <= metadata.getColumnCount(); index++) {
      row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
    }
    return row;
  }
}
