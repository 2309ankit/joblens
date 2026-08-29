package com.ankit.joblens.batchapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/duplicates")
public class DuplicateQueryController {

    private final JdbcTemplate jdbcTemplate;

    public DuplicateQueryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> clusters() {
        return jdbcTemplate.query("""
                SELECT c.id, c.cluster_key, c.canonical_job_id, c.member_count,
                       n.title AS canonical_title, c.created_at, c.updated_at
                FROM duplicate_cluster c
                JOIN normalized_job n ON n.id = c.canonical_job_id
                ORDER BY c.id
                """, (rs, rowNum) -> row(rs));
    }

    @GetMapping("/{id}")
    public Map<String, Object> cluster(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query("""
                SELECT c.id, c.cluster_key, c.canonical_job_id, c.member_count,
                       c.created_at, c.updated_at
                FROM duplicate_cluster c WHERE c.id = ?
                """, (rs, rowNum) -> row(rs), id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Duplicate cluster not found");
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        result.put("members", jdbcTemplate.queryForList("""
                SELECT n.id, n.source, n.external_job_id, n.title, n.company,
                       n.normalized_content_hash, m.is_canonical, m.added_at
                FROM duplicate_cluster_member m
                JOIN normalized_job n ON n.id = m.normalized_job_id
                WHERE m.cluster_id = ?
                ORDER BY m.is_canonical DESC, n.id
                """, id));
        result.put("evidence", jdbcTemplate.queryForList("""
                SELECT left_job_id, right_job_id, evidence_type, evidence_value, detected_at
                FROM duplicate_match_evidence
                WHERE cluster_id = ?
                ORDER BY left_job_id, right_job_id, evidence_type
                """, id));
        return result;
    }

    private static Map<String, Object> row(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        var metadata = resultSet.getMetaData();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
        }
        return row;
    }
}
