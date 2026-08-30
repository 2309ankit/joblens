package com.ankit.joblens.market;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class WeeklyMarketInsightRepository {
  private final JdbcTemplate jdbc;

  public WeeklyMarketInsightRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void generate(LocalDate weekStart) {
    jdbc.update(
        """
            INSERT INTO weekly_market_insight(week_start,source,job_count,company_count,remote_job_count,average_salary)
            SELECT ?, source, count(*), count(DISTINCT NULLIF(trim(company),'')),
                   count(*) FILTER (WHERE remote_type='REMOTE'),
                   avg((salary_min + salary_max) / 2) FILTER (WHERE salary_min IS NOT NULL AND salary_max IS NOT NULL)
            FROM normalized_job WHERE posted_at >= ? AND posted_at < ? GROUP BY source
            ON CONFLICT (week_start,source) DO UPDATE SET job_count=EXCLUDED.job_count, company_count=EXCLUDED.company_count,
              remote_job_count=EXCLUDED.remote_job_count, average_salary=EXCLUDED.average_salary
            """,
        Date.valueOf(weekStart),
        weekStart.atStartOfDay(),
        weekStart.plusDays(7).atStartOfDay());
  }

  public List<WeeklyMarketInsight> find(LocalDate from, LocalDate to) {
    return jdbc.query(
        "SELECT id,week_start,source,job_count,company_count,remote_job_count,average_salary,generated_at FROM weekly_market_insight WHERE week_start BETWEEN ? AND ? ORDER BY week_start DESC,source",
        (rs, n) ->
            new WeeklyMarketInsight(
                rs.getLong(1),
                rs.getDate(2).toLocalDate(),
                rs.getString(3),
                rs.getInt(4),
                rs.getInt(5),
                rs.getInt(6),
                rs.getBigDecimal(7),
                rs.getObject(8, java.time.OffsetDateTime.class)),
        Date.valueOf(from),
        Date.valueOf(to));
  }
}
