package com.ankit.joblens.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ankit.joblens.searchprofile.SearchProfile;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.test.MetaDataInstanceFactory;

class AgenticQueryPlanningTaskletTests {
  private static final UUID WORKSPACE_ID = UUID.randomUUID();

  @Test
  void disabledPlanningDoesNotReadProfilesOrCallProvider() {
    QueryPlanningRepository repository = mock(QueryPlanningRepository.class);
    QueryPlanningClient client = mock(QueryPlanningClient.class);
    AgenticQueryPlanningTasklet tasklet =
        new AgenticQueryPlanningTasklet(
            repository, client, properties(false, 20), WORKSPACE_ID.toString());

    tasklet.execute(null, null);

    verify(repository, never()).callsToday(anyString());
    verify(client, never()).plan(any(QueryPlanCandidate.class));
  }

  @Test
  void successfulPlanIsRecordedAndAppliedForTheProfile() {
    QueryPlanningRepository repository = mock(QueryPlanningRepository.class);
    QueryPlanningClient client = mock(QueryPlanningClient.class);
    SearchProfile profile = profile("w-abc-adzuna-1");
    when(repository.callsToday("nvidia/test-model")).thenReturn(0);
    when(repository.activeProfiles(WORKSPACE_ID, 5)).thenReturn(List.of(profile));
    when(repository.latestOutcome("w-abc-adzuna-1"))
        .thenReturn(new QueryPlanningRepository.LatestOutcome("EMPTY", 0));
    QueryPlanResult result =
        new QueryPlanResult("Backend Engineer Java", 3, "Broadened a narrow query.", 80, 20);
    when(client.plan(any(QueryPlanCandidate.class))).thenReturn(result);
    AgenticQueryPlanningTasklet tasklet = tasklet(repository, client, properties(true, 20));

    tasklet.execute(mock(StepContribution.class), chunkContext());

    verify(repository)
        .recordSuccess(
            eq(123L),
            eq("w-abc-adzuna-1"),
            eq("Backend Engineer Java Apache Camel IBM MQ"),
            any(LlmQueryPlanningProperties.class),
            eq(result));
  }

  @Test
  void providerFailureIsRecordedAndFallbackDoesNotFailStep() {
    QueryPlanningRepository repository = mock(QueryPlanningRepository.class);
    QueryPlanningClient client = mock(QueryPlanningClient.class);
    SearchProfile profile = profile("w-abc-adzuna-1");
    when(repository.callsToday("nvidia/test-model")).thenReturn(0);
    when(repository.activeProfiles(WORKSPACE_ID, 5)).thenReturn(List.of(profile));
    when(repository.latestOutcome("w-abc-adzuna-1")).thenReturn(null);
    when(client.plan(any(QueryPlanCandidate.class)))
        .thenThrow(new QueryPlanningException("timeout"));
    AgenticQueryPlanningTasklet tasklet = tasklet(repository, client, properties(true, 20));

    tasklet.execute(mock(StepContribution.class), chunkContext());

    verify(repository)
        .recordFailure(
            eq(123L),
            eq("w-abc-adzuna-1"),
            any(LlmQueryPlanningProperties.class),
            contains("timeout"));
  }

  @Test
  void oneProfileFailureDoesNotBlockAnother() {
    QueryPlanningRepository repository = mock(QueryPlanningRepository.class);
    QueryPlanningClient client = mock(QueryPlanningClient.class);
    SearchProfile first = profile("w-abc-adzuna-1");
    SearchProfile second = profile("w-abc-jooble-2");
    when(repository.callsToday("nvidia/test-model")).thenReturn(0);
    when(repository.activeProfiles(WORKSPACE_ID, 5)).thenReturn(List.of(first, second));
    QueryPlanResult result = new QueryPlanResult("Backend Engineer", 2, "Kept as-is.", 10, 5);
    when(client.plan(any(QueryPlanCandidate.class)))
        .thenThrow(new QueryPlanningException("boom"))
        .thenReturn(result);
    AgenticQueryPlanningTasklet tasklet = tasklet(repository, client, properties(true, 20));

    tasklet.execute(mock(StepContribution.class), chunkContext());

    verify(repository).recordFailure(eq(123L), eq("w-abc-adzuna-1"), any(), contains("boom"));
    verify(repository)
        .recordSuccess(eq(123L), eq("w-abc-jooble-2"), anyString(), any(), eq(result));
  }

  @Test
  void dailyLimitStopsBeforeReadingActiveProfiles() {
    QueryPlanningRepository repository = mock(QueryPlanningRepository.class);
    QueryPlanningClient client = mock(QueryPlanningClient.class);
    when(repository.callsToday("nvidia/test-model")).thenReturn(20);
    AgenticQueryPlanningTasklet tasklet = tasklet(repository, client, properties(true, 20));

    tasklet.execute(mock(StepContribution.class), chunkContext());

    verify(repository, never()).activeProfiles(any(), anyInt());
    verify(client, never()).plan(any(QueryPlanCandidate.class));
  }

  private static AgenticQueryPlanningTasklet tasklet(
      QueryPlanningRepository repository,
      QueryPlanningClient client,
      LlmQueryPlanningProperties properties) {
    return new AgenticQueryPlanningTasklet(repository, client, properties, WORKSPACE_ID.toString());
  }

  private static LlmQueryPlanningProperties properties(boolean enabled, int dailyLimit) {
    return new LlmQueryPlanningProperties(
        enabled,
        "https://api.tokenfactory.nebius.com/v1",
        "test-key",
        "nvidia/test-model",
        Duration.ofSeconds(10),
        5,
        dailyLimit,
        300,
        5,
        "query-plan-v1");
  }

  private static SearchProfile profile(String profileId) {
    return new SearchProfile(
        profileId,
        "ADZUNA",
        "sg",
        "Backend Engineer Java Apache Camel IBM MQ",
        "Singapore",
        "",
        "",
        "ANY",
        true,
        WORKSPACE_ID,
        1L,
        2,
        1L,
        false);
  }

  private static ChunkContext chunkContext() {
    return new ChunkContext(new StepContext(MetaDataInstanceFactory.createStepExecution()));
  }
}
