package com.ankit.joblens.intelligence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

public class ExactDuplicateDetectionTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final boolean failThisExecution;

    public ExactDuplicateDetectionTasklet(JdbcTemplate jdbcTemplate, boolean failThisExecution) {
        this.jdbcTemplate = jdbcTemplate;
        this.failThisExecution = failThisExecution;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<JobIdentity> jobs = jdbcTemplate.query("""
                SELECT id, source, external_job_id, normalized_content_hash
                FROM normalized_job
                ORDER BY id
                """, (rs, rowNum) -> new JobIdentity(
                rs.getLong("id"), rs.getString("source"), rs.getString("external_job_id"),
                rs.getString("normalized_content_hash")));

        Map<Long, Long> parent = new HashMap<>();
        Map<String, Long> firstBySourceIdentity = new HashMap<>();
        Map<String, Long> firstByContentHash = new HashMap<>();
        for (JobIdentity job : jobs) {
            parent.put(job.id(), job.id());
            unionWithFirst(parent, firstBySourceIdentity, job.sourceIdentity(), job.id());
            unionWithFirst(parent, firstByContentHash, job.normalizedContentHash(), job.id());
        }

        Map<Long, List<JobIdentity>> components = new LinkedHashMap<>();
        for (JobIdentity job : jobs) {
            components.computeIfAbsent(find(parent, job.id()), ignored -> new ArrayList<>()).add(job);
        }
        List<List<JobIdentity>> duplicateGroups = components.values().stream()
                .filter(group -> group.size() > 1)
                .peek(group -> group.sort(Comparator.comparingLong(JobIdentity::id)))
                .sorted(Comparator.comparingLong(group -> group.getFirst().id()))
                .toList();

        List<String> desiredKeys = duplicateGroups.stream().map(this::clusterKey).toList();
        deleteObsoleteClusters(desiredKeys);
        for (List<JobIdentity> group : duplicateGroups) {
            reconcileCluster(group);
        }

        jobs.forEach(ignored -> contribution.incrementReadCount());
        contribution.incrementWriteCount(duplicateGroups.stream().mapToLong(List::size).sum());
        if (failThisExecution) {
            throw new InjectedDuplicateDetectionFailureException();
        }
        return RepeatStatus.FINISHED;
    }

    private void reconcileCluster(List<JobIdentity> group) {
        long canonicalJobId = group.getFirst().id();
        String key = clusterKey(group);
        Long clusterId = jdbcTemplate.queryForObject("""
                INSERT INTO duplicate_cluster (cluster_key, canonical_job_id, member_count)
                VALUES (?, ?, ?)
                ON CONFLICT (cluster_key) DO UPDATE SET
                    canonical_job_id = EXCLUDED.canonical_job_id,
                    member_count = EXCLUDED.member_count,
                    updated_at = CASE
                        WHEN duplicate_cluster.canonical_job_id <> EXCLUDED.canonical_job_id
                          OR duplicate_cluster.member_count <> EXCLUDED.member_count
                        THEN CURRENT_TIMESTAMP ELSE duplicate_cluster.updated_at END
                RETURNING id
                """, Long.class, key, canonicalJobId, group.size());

        List<Long> memberIds = group.stream().map(JobIdentity::id).toList();
        List<Object> memberDeleteArguments = new ArrayList<>();
        memberDeleteArguments.add(clusterId);
        memberDeleteArguments.addAll(memberIds);
        jdbcTemplate.update("DELETE FROM duplicate_cluster_member WHERE cluster_id = ? "
                        + "AND normalized_job_id NOT IN (" + placeholders(memberIds.size()) + ")",
                memberDeleteArguments.toArray());
        for (JobIdentity job : group) {
            jdbcTemplate.update("""
                    INSERT INTO duplicate_cluster_member (cluster_id, normalized_job_id, is_canonical)
                    VALUES (?, ?, ?)
                    ON CONFLICT (cluster_id, normalized_job_id) DO UPDATE
                    SET is_canonical = EXCLUDED.is_canonical
                    """, clusterId, job.id(), job.id() == canonicalJobId);
        }

        List<Evidence> desiredEvidence = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < group.size(); leftIndex++) {
            JobIdentity left = group.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < group.size(); rightIndex++) {
                JobIdentity right = group.get(rightIndex);
                if (left.sourceIdentity().equals(right.sourceIdentity())) {
                    desiredEvidence.add(new Evidence(
                            left.id(), right.id(), "SOURCE_EXTERNAL_ID", left.sourceIdentity()));
                }
                if (left.normalizedContentHash().equals(right.normalizedContentHash())) {
                    desiredEvidence.add(new Evidence(
                            left.id(), right.id(), "NORMALIZED_CONTENT_HASH", left.normalizedContentHash()));
                }
            }
        }
        reconcileEvidence(clusterId, desiredEvidence);
    }

    private void reconcileEvidence(long clusterId, List<Evidence> desiredEvidence) {
        List<Evidence> existingEvidence = jdbcTemplate.query("""
                SELECT left_job_id, right_job_id, evidence_type, evidence_value
                FROM duplicate_match_evidence WHERE cluster_id = ?
                """, (rs, rowNum) -> new Evidence(
                rs.getLong("left_job_id"), rs.getLong("right_job_id"),
                rs.getString("evidence_type"), rs.getString("evidence_value")), clusterId);
        for (Evidence evidence : existingEvidence) {
            if (!desiredEvidence.contains(evidence)) {
                jdbcTemplate.update("""
                        DELETE FROM duplicate_match_evidence
                        WHERE cluster_id = ? AND left_job_id = ? AND right_job_id = ? AND evidence_type = ?
                        """, clusterId, evidence.leftJobId(), evidence.rightJobId(), evidence.type());
            }
        }
        for (Evidence evidence : desiredEvidence) {
            jdbcTemplate.update("""
                    INSERT INTO duplicate_match_evidence (
                        cluster_id, left_job_id, right_job_id, evidence_type, evidence_value
                    ) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (cluster_id, left_job_id, right_job_id, evidence_type) DO NOTHING
                    """, clusterId, evidence.leftJobId(), evidence.rightJobId(), evidence.type(), evidence.value());
        }
    }

    private void deleteObsoleteClusters(List<String> desiredKeys) {
        if (desiredKeys.isEmpty()) {
            jdbcTemplate.update("DELETE FROM duplicate_cluster");
            return;
        }
        jdbcTemplate.update("DELETE FROM duplicate_cluster WHERE cluster_key NOT IN ("
                + placeholders(desiredKeys.size()) + ")", desiredKeys.toArray());
    }

    private String clusterKey(List<JobIdentity> group) {
        return "exact:v1:" + group.getFirst().id();
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static void unionWithFirst(Map<Long, Long> parent, Map<String, Long> firstByValue,
            String value, long jobId) {
        Long firstId = firstByValue.putIfAbsent(value, jobId);
        if (firstId != null) {
            union(parent, firstId, jobId);
        }
    }

    private static void union(Map<Long, Long> parent, long left, long right) {
        long leftRoot = find(parent, left);
        long rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            parent.put(Math.max(leftRoot, rightRoot), Math.min(leftRoot, rightRoot));
        }
    }

    private static long find(Map<Long, Long> parent, long id) {
        long root = id;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }
        long current = id;
        while (current != root) {
            long next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private record JobIdentity(long id, String source, String externalJobId, String normalizedContentHash) {
        String sourceIdentity() {
            return source + ":" + externalJobId;
        }
    }

    private record Evidence(long leftJobId, long rightJobId, String type, String value) {
    }
}
