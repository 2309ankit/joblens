package com.ankit.joblens.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ankit.joblens.discovery.AdzunaProperties;
import com.ankit.joblens.discovery.FindJobsService;
import com.ankit.joblens.onboarding.OnboardingService;
import com.ankit.joblens.workspace.WorkspaceCandidateProfileService;
import com.ankit.joblens.workspace.WorkspaceContext;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DashboardControllerTests {

  @Test
  void forwardsTheExistingDashboardRouteToTheReactBundle() {
    assertThat(new DashboardController().dashboard()).isEqualTo("forward:/app/index.html");
  }

  @Test
  void returnsOnlyTheCurrentWorkspaceDashboardReadModel() {
    UUID workspaceId = UUID.randomUUID();
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    WorkspaceContext workspaceContext = mock(WorkspaceContext.class);
    WorkspaceCandidateProfileService candidateProfiles = mock(WorkspaceCandidateProfileService.class);
    OnboardingService onboarding = mock(OnboardingService.class);
    FindJobsService findJobs = mock(FindJobsService.class);
    when(workspaceContext.resolve(any(), any())).thenReturn(workspaceId);
    when(candidateProfiles.requireCandidateProfile(workspaceId)).thenReturn(42L);
    when(onboarding.preferences(workspaceId)).thenReturn(Optional.empty());
    when(findJobs.latest(workspaceId)).thenReturn(Optional.empty());
    when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(3, 2);
    when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());
    var controller =
        new DashboardApiController(
            jdbc,
            workspaceContext,
            candidateProfiles,
            onboarding,
            mock(PortalSearchLinkFactory.class),
            findJobs,
            new AdzunaProperties("", "", "https://example.test", Duration.ofSeconds(1), 1, 1, 30, 1, Duration.ZERO));

    var result =
        controller.dashboard(new MockHttpServletRequest(), new MockHttpServletResponse());

    assertThat(result.jobs()).isEmpty();
    assertThat(result.applicationCount()).isEqualTo(3);
    assertThat(result.openFollowUpCount()).isEqualTo(2);
    assertThat(result.portalSearchLinks()).isEmpty();
    assertThat(result.latestSearchRun()).isNull();
  }
}
