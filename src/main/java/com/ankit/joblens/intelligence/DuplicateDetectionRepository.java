package com.ankit.joblens.intelligence;

import com.ankit.joblens.jdbc.ClasspathSql;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class DuplicateDetectionRepository {

  private static final String FIND_JOBS = ClasspathSql.load("sql/duplicate/find-jobs.sql");
  private static final String DELETE_OBSOLETE_CLUSTERS =
      ClasspathSql.load("sql/duplicate/delete-obsolete-clusters.sql");
  private static final String DELETE_ALL_CLUSTERS =
      ClasspathSql.load("sql/duplicate/delete-all-clusters.sql");
  private static final String UPSERT_CLUSTER =
      ClasspathSql.load("sql/duplicate/upsert-cluster.sql");
  private static final String DELETE_OBSOLETE_MEMBERS =
      ClasspathSql.load("sql/duplicate/delete-obsolete-members.sql");
  private static final String UPSERT_MEMBER = ClasspathSql.load("sql/duplicate/upsert-member.sql");
  private static final String FIND_EVIDENCE = ClasspathSql.load("sql/duplicate/find-evidence.sql");
  private static final String DELETE_EVIDENCE =
      ClasspathSql.load("sql/duplicate/delete-evidence.sql");
  private static final String INSERT_EVIDENCE =
      ClasspathSql.load("sql/duplicate/insert-evidence.sql");
  private static final String FIND_EXACT_MEMBERSHIPS =
      ClasspathSql.load("sql/duplicate/find-exact-memberships.sql");
  private static final String DELETE_OBSOLETE_SIMILARITIES =
      ClasspathSql.load("sql/duplicate/delete-obsolete-similarities.sql");
  private static final String DELETE_ALL_SIMILARITIES =
      ClasspathSql.load("sql/duplicate/delete-all-similarities.sql");
  private static final String UPSERT_SIMILARITY =
      ClasspathSql.load("sql/duplicate/upsert-similarity.sql");

  private final NamedParameterJdbcTemplate jdbc;

  public DuplicateDetectionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<DuplicateJobView> findJobs() {
    return jdbc.query(
        FIND_JOBS,
        Map.of(),
        (rs, rowNum) ->
            new DuplicateJobView(
                rs.getLong("id"),
                rs.getString("source"),
                rs.getString("external_job_id"),
                rs.getString("title"),
                rs.getString("company"),
                rs.getString("location"),
                rs.getString("description_text"),
                rs.getString("employment_type"),
                rs.getString("normalized_content_hash")));
  }

  public void deleteObsoleteClusters(List<String> desiredKeys) {
    if (desiredKeys.isEmpty()) {
      jdbc.update(DELETE_ALL_CLUSTERS, Map.of());
      return;
    }
    jdbc.update(DELETE_OBSOLETE_CLUSTERS, Map.of("desiredKeys", desiredKeys));
  }

  public long upsertCluster(String key, long canonicalJobId, int memberCount) {
    return jdbc.queryForObject(
        UPSERT_CLUSTER,
        new MapSqlParameterSource()
            .addValue("clusterKey", key)
            .addValue("canonicalJobId", canonicalJobId)
            .addValue("memberCount", memberCount),
        Long.class);
  }

  public void deleteObsoleteMembers(long clusterId, List<Long> memberIds) {
    jdbc.update(
        DELETE_OBSOLETE_MEMBERS,
        new MapSqlParameterSource()
            .addValue("clusterId", clusterId)
            .addValue("memberIds", memberIds));
  }

  public void upsertMember(long clusterId, long jobId, boolean canonical) {
    jdbc.update(
        UPSERT_MEMBER,
        new MapSqlParameterSource()
            .addValue("clusterId", clusterId)
            .addValue("jobId", jobId)
            .addValue("canonical", canonical));
  }

  public List<DuplicateEvidence> findEvidence(long clusterId) {
    return jdbc.query(
        FIND_EVIDENCE,
        Map.of("clusterId", clusterId),
        (rs, rowNum) ->
            new DuplicateEvidence(
                rs.getLong("left_job_id"),
                rs.getLong("right_job_id"),
                rs.getString("evidence_type"),
                rs.getString("evidence_value")));
  }

  public void deleteEvidence(long clusterId, DuplicateEvidence evidence) {
    jdbc.update(DELETE_EVIDENCE, evidenceParameters(clusterId, evidence));
  }

  public void insertEvidence(long clusterId, DuplicateEvidence evidence) {
    jdbc.update(INSERT_EVIDENCE, evidenceParameters(clusterId, evidence));
  }

  public Map<Long, Long> findExactMemberships() {
    Map<Long, Long> memberships = new HashMap<>();
    List<Map<String, Object>> rows = jdbc.queryForList(FIND_EXACT_MEMBERSHIPS, Map.of());
    for (Map<String, Object> row : rows) {
      memberships.put(
          ((Number) row.get("normalized_job_id")).longValue(),
          ((Number) row.get("cluster_id")).longValue());
    }
    return memberships;
  }

  public void reconcileSimilarities(String algorithmVersion, List<JobSimilarity> similarities) {
    List<String> pairKeys = similarities.stream().map(JobSimilarity::pairKey).toList();
    if (pairKeys.isEmpty()) {
      jdbc.update(DELETE_ALL_SIMILARITIES, Map.of("algorithmVersion", algorithmVersion));
    } else {
      jdbc.update(
          DELETE_OBSOLETE_SIMILARITIES,
          new MapSqlParameterSource()
              .addValue("algorithmVersion", algorithmVersion)
              .addValue("pairKeys", pairKeys));
    }
    for (JobSimilarity similarity : similarities) {
      jdbc.update(UPSERT_SIMILARITY, similarityParameters(similarity));
    }
  }

  private static MapSqlParameterSource evidenceParameters(
      long clusterId, DuplicateEvidence evidence) {
    return new MapSqlParameterSource()
        .addValue("clusterId", clusterId)
        .addValue("leftJobId", evidence.leftJobId())
        .addValue("rightJobId", evidence.rightJobId())
        .addValue("evidenceType", evidence.type())
        .addValue("evidenceValue", evidence.value());
  }

  private static MapSqlParameterSource similarityParameters(JobSimilarity similarity) {
    return new MapSqlParameterSource()
        .addValue("leftJobId", similarity.leftJobId())
        .addValue("rightJobId", similarity.rightJobId())
        .addValue("algorithmVersion", similarity.algorithmVersion())
        .addValue("overallScore", similarity.overallScore())
        .addValue("titleScore", similarity.titleScore())
        .addValue("descriptionScore", similarity.descriptionScore())
        .addValue("companyScore", similarity.companyScore())
        .addValue("locationScore", similarity.locationScore())
        .addValue("employmentScore", similarity.employmentScore())
        .addValue("decision", similarity.decision())
        .addValue("explanation", similarity.explanation())
        .addValue("leftContentHash", similarity.leftContentHash())
        .addValue("rightContentHash", similarity.rightContentHash());
  }
}
