package com.ankit.joblens.dashboard;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
  private final NamedParameterJdbcTemplate jdbc;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;

  public DashboardController(
      NamedParameterJdbcTemplate jdbc,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles) {
    this.jdbc = jdbc;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
  }

  @GetMapping({"/", "/dashboard"})
  public String dashboard(Model model, HttpServletRequest request, HttpServletResponse response) {
    long candidateProfileId;
    try {
      candidateProfileId =
          candidateProfiles.requireCandidateProfile(workspaceContext.resolve(request, response));
    } catch (IllegalStateException exception) {
      return "redirect:/setup";
    }
    Map<String, Object> parameters = Map.of("candidateProfileId", candidateProfileId);
    model.addAttribute(
        "jobs",
        jdbc.query(
            load("sql/dashboard/list-ranked-jobs.sql"),
            parameters,
            (resultSet, rowNumber) -> {
              var row = new LinkedHashMap<String, Object>();
              row.put("id", resultSet.getLong("id"));
              row.put("title", resultSet.getString("title"));
              row.put("company", resultSet.getString("company"));
              row.put("location", resultSet.getString("location"));
              row.put("score", resultSet.getBigDecimal("score"));
              row.put("source", resultSet.getString("source"));
              row.put("viewCount", resultSet.getInt("view_count"));
              return row;
            }));
    model.addAttribute(
        "applicationCount",
        jdbc.queryForObject(
            load("sql/dashboard/count-applications.sql"), parameters, Integer.class));
    model.addAttribute(
        "openFollowUpCount",
        jdbc.queryForObject(
            load("sql/dashboard/count-open-follow-ups.sql"), parameters, Integer.class));
    model.addAttribute(
        "insights", jdbc.queryForList(load("sql/dashboard/list-insights.sql"), Map.of()));
    return "dashboard";
  }
}
