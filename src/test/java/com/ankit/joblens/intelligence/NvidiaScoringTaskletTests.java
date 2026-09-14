package com.ankit.joblens.intelligence;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import tools.jackson.databind.ObjectMapper;

class NvidiaScoringTaskletTests {

  @Test
  void disabledRankingDoesNotReadCandidatesOrCallProvider() {
    NvidiaScoringRepository repository = mock(NvidiaScoringRepository.class);
    NebiusNvidiaScoringClient client = mock(NebiusNvidiaScoringClient.class);
    JobScoreCalculator calculator = mock(JobScoreCalculator.class);
    NvidiaRankingProperties properties =
        new NvidiaRankingProperties(
            false,
            "https://api.tokenfactory.nebius.com/v1",
            "",
            "",
            Duration.ofSeconds(20),
            25,
            200,
            500,
            "nvidia-job-fit-v1");
    NvidiaScoringTasklet tasklet =
        new NvidiaScoringTasklet(
            repository, client, properties, calculator, new ObjectMapper(), null, null);

    tasklet.execute(null, null);

    verify(repository, never()).callsToday(org.mockito.ArgumentMatchers.anyString());
    verify(client, never())
        .score(
            org.mockito.ArgumentMatchers.any(NvidiaScoringCandidate.class),
            org.mockito.ArgumentMatchers.any(RoleRankingContext.class));
  }

  @Test
  void cachedResultDoesNotCallProviderAgain() {
    NvidiaScoringRepository repository = mock(NvidiaScoringRepository.class);
    NebiusNvidiaScoringClient client = mock(NebiusNvidiaScoringClient.class);
    JobScoreCalculator calculator = mock(JobScoreCalculator.class);
    RoleRankingContext context = context();
    when(calculator.context(2L)).thenReturn(context);
    when(repository.callsToday("nvidia/test-model")).thenReturn(0);
    when(repository.shortlist(2L, null, 25)).thenReturn(List.of(scoringCandidate()));
    when(repository.hasCachedScore(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
    NvidiaScoringTasklet tasklet = tasklet(repository, client, calculator, properties(200));

    tasklet.execute(mock(StepContribution.class), null);

    verify(client, never())
        .score(
            org.mockito.ArgumentMatchers.any(NvidiaScoringCandidate.class),
            org.mockito.ArgumentMatchers.any(RoleRankingContext.class));
  }

  @Test
  void providerFailureIsRecordedAndFallbackDoesNotFailStep() {
    NvidiaScoringRepository repository = mock(NvidiaScoringRepository.class);
    NebiusNvidiaScoringClient client = mock(NebiusNvidiaScoringClient.class);
    JobScoreCalculator calculator = mock(JobScoreCalculator.class);
    RoleRankingContext context = context();
    NvidiaScoringCandidate candidate = scoringCandidate();
    when(calculator.context(2L)).thenReturn(context);
    when(repository.callsToday("nvidia/test-model")).thenReturn(0);
    when(repository.shortlist(2L, null, 25)).thenReturn(List.of(candidate));
    when(repository.hasCachedScore(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    when(client.score(candidate, context)).thenThrow(new NvidiaScoringException("timeout"));
    NvidiaScoringTasklet tasklet = tasklet(repository, client, calculator, properties(200));

    tasklet.execute(mock(StepContribution.class), null);

    verify(repository)
        .recordFailure(
            org.mockito.ArgumentMatchers.eq(candidate),
            org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(NvidiaRankingProperties.class),
            org.mockito.ArgumentMatchers.contains("timeout"));
  }

  @Test
  void dailyLimitStopsBeforeReadingShortlist() {
    NvidiaScoringRepository repository = mock(NvidiaScoringRepository.class);
    NebiusNvidiaScoringClient client = mock(NebiusNvidiaScoringClient.class);
    JobScoreCalculator calculator = mock(JobScoreCalculator.class);
    when(calculator.context(2L)).thenReturn(context());
    when(repository.callsToday("nvidia/test-model")).thenReturn(10);
    NvidiaScoringTasklet tasklet = tasklet(repository, client, calculator, properties(10));

    tasklet.execute(mock(StepContribution.class), null);

    verify(repository, never())
        .shortlist(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt());
    verify(client, never())
        .score(
            org.mockito.ArgumentMatchers.any(NvidiaScoringCandidate.class),
            org.mockito.ArgumentMatchers.any(RoleRankingContext.class));
  }

  private static NvidiaScoringTasklet tasklet(
      NvidiaScoringRepository repository,
      NebiusNvidiaScoringClient client,
      JobScoreCalculator calculator,
      NvidiaRankingProperties properties) {
    return new NvidiaScoringTasklet(
        repository, client, properties, calculator, new ObjectMapper(), 2L, null);
  }

  private static NvidiaRankingProperties properties(int dailyLimit) {
    return new NvidiaRankingProperties(
        true,
        "https://api.tokenfactory.nebius.com/v1",
        "test-key",
        "nvidia/test-model",
        Duration.ofSeconds(20),
        25,
        dailyLimit,
        500,
        "nvidia-job-fit-v1");
  }

  private static NvidiaScoringCandidate scoringCandidate() {
    return new NvidiaScoringCandidate(
        new NormalizedJobView(
            10,
            "JOOBLE",
            "external-10",
            "Java Engineer",
            "Example",
            "Singapore",
            "Build Java services",
            "FULL_TIME",
            BigDecimal.ZERO,
            BigDecimal.TEN,
            "SGD",
            "HYBRID",
            OffsetDateTime.now(),
            "https://example.test/10",
            "a".repeat(64)),
        75,
        true,
        3L);
  }

  private static RoleRankingContext context() {
    return new RoleRankingContext(
        new CandidateProfileConfig(
            2,
            "Singapore",
            List.of(),
            Set.of("Backend Engineer"),
            Set.of("Technology"),
            Map.of(1L, new CandidateProfileConfig.CandidateSkill("Java", "PRODUCTION", 1)),
            Map.of()),
        List.of());
  }
}
