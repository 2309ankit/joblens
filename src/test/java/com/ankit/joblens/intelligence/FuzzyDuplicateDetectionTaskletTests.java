package com.ankit.joblens.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class FuzzyDuplicateDetectionTaskletTests {
  @Test
  void failureInSecondChunkKeepsFirstCheckpointAndRestartDoesNotReplayIt() {
    var repository = mock(DuplicateDetectionRepository.class);
    var calculator = new FuzzySimilarityCalculator(BigDecimal.valueOf(75), BigDecimal.valueOf(90));
    var jobs =
        LongStream.rangeClosed(1, 51)
            .mapToObj(
                id ->
                    new DuplicateJobView(
                        id,
                        "ADZUNA",
                        "test-" + id,
                        "Java engineer",
                        "Company",
                        "Singapore",
                        "Java Spring " + id,
                        "PERMANENT",
                        "a".repeat(64)))
            .toList();
    when(repository.findJobs()).thenReturn(jobs);
    when(repository.findExactMemberships()).thenReturn(Map.of());
    doNothing()
        .doThrow(new InjectedFuzzyDetectionFailureException())
        .when(repository)
        .reconcileSimilaritiesForLeftJobs(anyList(), anyString(), anyList(), anyBoolean());
    var executionContext = new ExecutionContext();
    var step = mock(StepExecution.class);
    when(step.getExecutionContext()).thenReturn(executionContext);
    var chunk = new ChunkContext(new StepContext(step));
    var contribution = mock(StepContribution.class);
    var tasklet = new FuzzyDuplicateDetectionTasklet(repository, calculator, false);
    assertThat(tasklet.execute(contribution, chunk)).isEqualTo(RepeatStatus.CONTINUABLE);
    assertThat(executionContext.getLong("fuzzy.v2.lastLeftId")).isEqualTo(25);
    assertThatThrownBy(() -> tasklet.execute(contribution, chunk))
        .isInstanceOf(InjectedFuzzyDetectionFailureException.class);
    assertThat(executionContext.getLong("fuzzy.v2.lastLeftId")).isEqualTo(25);

    clearInvocations(repository);
    doNothing()
        .when(repository)
        .reconcileSimilaritiesForLeftJobs(anyList(), anyString(), anyList(), anyBoolean());
    var restarted = new FuzzyDuplicateDetectionTasklet(repository, calculator, false);
    assertThat(restarted.execute(contribution, chunk)).isEqualTo(RepeatStatus.CONTINUABLE);
    verify(repository)
        .reconcileSimilaritiesForLeftJobs(
            eq(LongStream.rangeClosed(26, 50).boxed().toList()),
            eq("fuzzy-v1"),
            anyList(),
            eq(false));
    assertThat(restarted.execute(contribution, chunk)).isEqualTo(RepeatStatus.FINISHED);
    assertThat(executionContext.getLong("fuzzy.v2.lastLeftId")).isEqualTo(51);
  }
}
