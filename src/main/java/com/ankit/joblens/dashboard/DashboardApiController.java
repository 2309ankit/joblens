package com.ankit.joblens.dashboard;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import com.ankit.joblens.discovery.AdzunaProperties;
import com.ankit.joblens.discovery.FindJobsRunDetail;
import com.ankit.joblens.discovery.FindJobsService;
import com.ankit.joblens.onboarding.OnboardingService;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Workspace-owned read model used by the React dashboard. */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Read the current workspace dashboard")
public class DashboardApiController {
  private final NamedParameterJdbcTemplate jdbc;
  private final WorkspaceContext workspaceContext;
  private final WorkspaceCandidateProfileService candidateProfiles;
  private final OnboardingService onboarding;
  private final PortalSearchLinkFactory portalSearchLinks;
  private final FindJobsService findJobs;
  private final AdzunaProperties adzunaProperties;

  public DashboardApiController(
      NamedParameterJdbcTemplate jdbc,
      WorkspaceContext workspaceContext,
      WorkspaceCandidateProfileService candidateProfiles,
      OnboardingService onboarding,
      PortalSearchLinkFactory portalSearchLinks,
      FindJobsService findJobs,
      AdzunaProperties adzunaProperties) {
    this.jdbc = jdbc;
    this.workspaceContext = workspaceContext;
    this.candidateProfiles = candidateProfiles;
    this.onboarding = onboarding;
    this.portalSearchLinks = portalSearchLinks;
    this.findJobs = findJobs;
    this.adzunaProperties = adzunaProperties;
  }

  @GetMapping
  @Operation(summary = "Read the current workspace dashboard")
  public DashboardResponse dashboard(HttpServletRequest request, HttpServletResponse response) {
    UUID workspaceId = workspaceContext.resolve(request, response);
    long candidateProfileId = candidateProfiles.requireCandidateProfile(workspaceId);
    Map<String, Object> parameters =
        Map.of(
            "candidateProfileId", candidateProfileId,
            "workspaceId", workspaceId,
            "maxDaysOld", adzunaProperties.maxDaysOld());
    List<PortalSearchLink> links =
        onboarding
            .preferences(workspaceId)
            .map(
                preferences ->
                    portalSearchLinks.create(
                        preferences,
                        onboarding
                            .latest(workspaceId)
                            .map(profile -> profile.skills())
                            .orElseGet(List::of)))
            .orElseGet(List::of);
    return new DashboardResponse(
        jdbc.query(
            load("sql/dashboard/list-ranked-jobs.sql"), parameters, DashboardApiController::row),
        jdbc.queryForObject(
            load("sql/dashboard/count-applications.sql"), parameters, Integer.class),
        jdbc.queryForObject(
            load("sql/dashboard/count-open-follow-ups.sql"), parameters, Integer.class),
        jdbc.queryForList(load("sql/dashboard/list-insights.sql"), Map.of()),
        links,
        findJobs.latest(workspaceId).orElse(null));
  }

  private static Map<String, Object> row(ResultSet resultSet, int ignored) throws SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    var metadata = resultSet.getMetaData();
    for (int index = 1; index <= metadata.getColumnCount(); index++) {
      row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
    }
    return row;
  }

  public record DashboardResponse(
      List<Map<String, Object>> jobs,
      int applicationCount,
      int openFollowUpCount,
      List<Map<String, Object>> insights,
      List<PortalSearchLink> portalSearchLinks,
      FindJobsRunDetail latestSearchRun) {}
}
