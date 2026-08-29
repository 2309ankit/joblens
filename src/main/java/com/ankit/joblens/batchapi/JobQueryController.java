package com.ankit.joblens.batchapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobQueryController {
    private final JdbcTemplate jdbc;
    private final DuplicateQueryRepository duplicateQueryRepository;
    public JobQueryController(JdbcTemplate jdbc, DuplicateQueryRepository duplicateQueryRepository) {
        this.jdbc=jdbc;
        this.duplicateQueryRepository=duplicateQueryRepository;
    }
    @GetMapping
    public List<Map<String,Object>> jobs() {
        return jdbc.query("SELECT n.id,n.title,n.company,n.location,n.description_text,n.employment_type,n.salary_min,n.salary_max,n.salary_currency,n.remote_type,n.posted_at,n.source_url,COALESCE(s.total_score,0) AS score FROM normalized_job n LEFT JOIN job_score s ON s.normalized_job_id=n.id ORDER BY score DESC,n.id", (rs,n)->row(rs));
    }
    @GetMapping("/{id}")
    public Map<String,Object> job(@PathVariable long id) {
        var rows=jdbc.query("SELECT n.*,COALESCE(s.total_score,0) AS score,s.technical_score,s.domain_score,s.seniority_score,s.location_score,s.employment_score,s.salary_score,s.freshness_score FROM normalized_job n LEFT JOIN job_score s ON s.normalized_job_id=n.id WHERE n.id=?",(rs,n)->row(rs),id);
        if(rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Job not found");
        var result=new LinkedHashMap<>(rows.getFirst());
        result.put("skills",jdbc.queryForList("SELECT s.canonical_name FROM job_skill js JOIN skill s ON s.id=js.skill_id WHERE js.normalized_job_id=? ORDER BY s.canonical_name",String.class,id));
        result.put("scoreReasons",jdbc.queryForList("SELECT category,points,reason_text FROM job_score_reason r JOIN job_score s ON s.id=r.job_score_id WHERE s.normalized_job_id=? ORDER BY r.id",id));
        result.put("duplicateCluster", duplicateQueryRepository.findClusterForJob(id));
        result.put("similarityMatches", duplicateQueryRepository.findSimilaritiesForJob(id));
        return result;
    }
    private static Map<String,Object> row(java.sql.ResultSet rs) throws java.sql.SQLException { var m=new LinkedHashMap<String,Object>(); var md=rs.getMetaData(); for(int i=1;i<=md.getColumnCount();i++)m.put(md.getColumnLabel(i),rs.getObject(i)); return m; }
}
