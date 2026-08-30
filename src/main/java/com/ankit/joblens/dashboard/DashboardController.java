package com.ankit.joblens.dashboard;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.onboarding.OnboardingService;
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
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class DashboardController {
  private final NamedParameterJdbcTemplate jdbc;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;
  private final OnboardingService onboarding;

  public DashboardController(
      NamedParameterJdbcTemplate jdbc,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles,
      OnboardingService onboarding) {
    this.jdbc = jdbc;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
    this.onboarding = onboarding;
  }

  @GetMapping({"/", "/dashboard"})
  public String dashboard(Model model, HttpServletRequest request, HttpServletResponse response) {
    long candidateProfileId;
    java.util.UUID workspaceId = workspaceContext.resolve(request, response);
    try {
      candidateProfileId = candidateProfiles.requireCandidateProfile(workspaceId);
    } catch (IllegalStateException exception) {
      return "redirect:/setup";
    }
    Map<String, Object> parameters =
        Map.of("candidateProfileId", candidateProfileId, "workspaceId", workspaceId);
    model.addAttribute("businessDate", java.time.LocalDate.now());
    onboarding
        .preferences(workspaceId)
        .ifPresent(
            preferences -> {
              model.addAttribute(
                  "linkedInSearchUrl",
                  UriComponentsBuilder.fromUriString("https://www.linkedin.com/jobs/search/")
                      .queryParam("keywords", preferences.keywords())
                      .queryParam("location", preferences.searchLocation())
                      .encode()
                      .toUriString());
              model.addAttribute(
                  "jobStreetSearchUrl",
                  "https://sg.jobstreet.com/" + slug(preferences.keywords()) + "-jobs");
            });
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
              row.put("applicationId", resultSet.getObject("application_id", Long.class));
              row.put("applicationStatus", resultSet.getString("application_status"));
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

  private static String slug(String value) {
    String slug = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    return slug.replaceAll("(^-)|(-$)", "");
  }
}
