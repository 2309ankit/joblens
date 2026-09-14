package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.test.MetaDataInstanceFactory;

class JobDiscoveryTaskletTests {
  private static final UUID WORKSPACE_ID = UUID.randomUUID();

  @Test
  void usesTheAppliedQueryPlanDecisionInsteadOfTheStoredProfile() {
    DiscoveryPersistenceService persistence = mock(DiscoveryPersistenceService.class);
    JobSourceClient client = mock(JobSourceClient.class);
    QueryPlanningRepository queryPlanning = mock(QueryPlanningRepository.class);
    SearchProfile profile = profile("Backend Engineer Apache Camel IBM MQ", 2);
    when(persistence.findNextActiveProfile(null, null, WORKSPACE_ID)).thenReturn(profile);
    when(persistence.startOrResumeFetchRun(profile, 12L, 123L))
        .thenReturn(new FetchRunState(50L, 1, "STARTED"));
    when(client.supports(JobSource.ADZUNA)).thenReturn(true);
    when(client.pageSize()).thenReturn(20);
    when(client.search(any(SearchProfile.class), any(PageRequest.class)))
        .thenReturn(new JobPage(1, 0, List.of(), false));
    when(queryPlanning.findApplied(123L, profile.profileId()))
        .thenReturn(new QueryPlanDecision("Backend Engineer Java", 5));
    JobDiscoveryTasklet tasklet =
        new JobDiscoveryTasklet(
            persistence,
            List.of(client),
            adzunaProperties(),
            null,
            WORKSPACE_ID.toString(),
            new FailureReasonSanitizer(),
            queryPlanning);

    tasklet.execute(mock(StepContribution.class), chunkContext());

    ArgumentCaptor<SearchProfile> captor = ArgumentCaptor.forClass(SearchProfile.class);
    verify(client).search(captor.capture(), any(PageRequest.class));
    assertThat(captor.getValue().keywords()).isEqualTo("Backend Engineer Java");
    assertThat(captor.getValue().maxPages()).isEqualTo(5);
    assertThat(captor.getValue().profileId()).isEqualTo(profile.profileId());
  }

  @Test
  void fallsBackToTheStoredProfileWhenNoDecisionIsApplied() {
    DiscoveryPersistenceService persistence = mock(DiscoveryPersistenceService.class);
    JobSourceClient client = mock(JobSourceClient.class);
    QueryPlanningRepository queryPlanning = mock(QueryPlanningRepository.class);
    SearchProfile profile = profile("Backend Engineer Apache Camel IBM MQ", 2);
    when(persistence.findNextActiveProfile(null, null, WORKSPACE_ID)).thenReturn(profile);
    when(persistence.startOrResumeFetchRun(profile, 12L, 123L))
        .thenReturn(new FetchRunState(50L, 1, "STARTED"));
    when(client.supports(JobSource.ADZUNA)).thenReturn(true);
    when(client.pageSize()).thenReturn(20);
    when(client.search(any(SearchProfile.class), any(PageRequest.class)))
        .thenReturn(new JobPage(1, 0, List.of(), false));
    when(queryPlanning.findApplied(123L, profile.profileId())).thenReturn(null);
    JobDiscoveryTasklet tasklet =
        new JobDiscoveryTasklet(
            persistence,
            List.of(client),
            adzunaProperties(),
            null,
            WORKSPACE_ID.toString(),
            new FailureReasonSanitizer(),
            queryPlanning);

    tasklet.execute(mock(StepContribution.class), chunkContext());

    verify(client).search(eq(profile), any(PageRequest.class));
  }

  private static SearchProfile profile(String keywords, int maxPages) {
    return new SearchProfile(
        "w-abc-adzuna-1",
        "ADZUNA",
        "sg",
        keywords,
        "Singapore",
        "",
        "",
        "ANY",
        true,
        WORKSPACE_ID,
        1L,
        maxPages,
        1L,
        false);
  }

  private static AdzunaProperties adzunaProperties() {
    return new AdzunaProperties(
        "test-id",
        "test-key",
        "https://api.adzuna.com/v1/api",
        Duration.ofSeconds(5),
        10,
        20,
        30,
        3,
        Duration.ofMillis(1));
  }

  private static ChunkContext chunkContext() {
    return new ChunkContext(new StepContext(MetaDataInstanceFactory.createStepExecution()));
  }
}
