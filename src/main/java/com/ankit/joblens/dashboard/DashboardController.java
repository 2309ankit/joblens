package com.ankit.joblens.dashboard;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.LinkedHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
  private final JdbcTemplate jdbc;

  public DashboardController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping({"/", "/dashboard"})
  public String dashboard(Model model) {
    model.addAttribute(
        "jobs",
        jdbc.query(
            load("sql/dashboard/list-ranked-jobs.sql"),
            (rs, i) -> {
              var row = new LinkedHashMap<String, Object>();
              row.put("id", rs.getLong("id"));
              row.put("title", rs.getString("title"));
              row.put("company", rs.getString("company"));
              row.put("location", rs.getString("location"));
              row.put("score", rs.getBigDecimal("score"));
              row.put("source", rs.getString("source"));
              row.put("viewCount", rs.getInt("view_count"));
              return row;
            }));
    model.addAttribute(
        "applicationCount",
        jdbc.queryForObject("SELECT count(*) FROM job_application", Integer.class));
    model.addAttribute(
        "openFollowUpCount",
        jdbc.queryForObject(
            "SELECT count(*) FROM application_follow_up WHERE status='OPEN'", Integer.class));
    model.addAttribute(
        "insights",
        jdbc.queryForList(
            "SELECT week_start,source,job_count,remote_job_count,average_salary FROM weekly_market_insight ORDER BY week_start DESC,source LIMIT 12"));
    return "dashboard";
  }
}
