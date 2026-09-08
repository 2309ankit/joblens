package com.ankit.joblens.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

class FindJobsServiceTests {

  @Test
  void returnsAndProjectsTheExistingActiveExecutionBeforeStartingAnother() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    long candidateProfileId = 42L;
    LocalDate businessDate = LocalDate.of(2026, 9, 7);
    JobOperator operator = mock(JobOperator.class);
    Job job = mock(Job.class);
    WorkspaceSearchRunRepository runs = mock(WorkspaceSearchRunRepository.class);
    JobRepository repository = mock(JobRepository.class);
    JobExecution active = activeExecution();
    when(job.getName()).thenReturn("findJobsJob");
    when(runs.definitionVersion(workspaceId)).thenReturn("definition-v1");
    when(repository.getLastJobExecution(eq("findJobsJob"), any(JobParameters.class)))
        .thenReturn(active);

    JobExecution result =
        new FindJobsService(operator, job, runs, repository, Duration.ofMinutes(30))
            .run(workspaceId, candidateProfileId, businessDate);

    assertThat(result).isSameAs(active);
    verify(operator, never()).start(any(Job.class), any(JobParameters.class));
    verify(runs).record(workspaceId, candidateProfileId, businessDate, active);
  }

  @Test
  void projectsTheExecutionThatWonAConcurrentLaunchRace() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    JobOperator operator = mock(JobOperator.class);
    Job job = mock(Job.class);
    WorkspaceSearchRunRepository runs = mock(WorkspaceSearchRunRepository.class);
    JobRepository repository = mock(JobRepository.class);
    JobExecution active = activeExecution();
    when(job.getName()).thenReturn("findJobsJob");
    when(runs.definitionVersion(workspaceId)).thenReturn("definition-v1");
    when(repository.getLastJobExecution(eq("findJobsJob"), any(JobParameters.class)))
        .thenReturn(null, active);
    when(operator.start(eq(job), any(JobParameters.class)))
        .thenThrow(new JobExecutionAlreadyRunningException("JobInstance 12 is already running"));

    JobExecution result =
        new FindJobsService(operator, job, runs, repository, Duration.ofMinutes(30))
            .run(workspaceId, 42L, LocalDate.of(2026, 9, 7));

    assertThat(result).isSameAs(active);
    verify(repository, times(2)).getLastJobExecution(eq("findJobsJob"), any(JobParameters.class));
    verify(runs).record(workspaceId, 42L, LocalDate.of(2026, 9, 7), active);
  }

  @Test
  void marksAnExecutionStaleOnlyAfterTheConfiguredUpdateThreshold() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    JobOperator operator = mock(JobOperator.class);
    Job job = mock(Job.class);
    WorkspaceSearchRunRepository runs = mock(WorkspaceSearchRunRepository.class);
    JobRepository repository = mock(JobRepository.class);
    JobExecution stale = activeExecution();
    stale.setLastUpdated(LocalDateTime.now().minusMinutes(31));
    when(job.getName()).thenReturn("findJobsJob");
    when(runs.definitionVersion(workspaceId)).thenReturn("definition-v1");
    when(repository.getLastJobExecution(eq("findJobsJob"), any(JobParameters.class)))
        .thenReturn(stale);

    assertThatThrownBy(
            () ->
                new FindJobsService(operator, job, runs, repository, Duration.ofMinutes(30))
                    .run(workspaceId, 42L, LocalDate.of(2026, 9, 7)))
        .isInstanceOf(StaleFindJobsRunException.class)
        .hasMessageNotContaining("JobInstance")
        .hasMessageNotContaining("JobExecution");

    verify(runs).record(workspaceId, 42L, LocalDate.of(2026, 9, 7), stale);
    verify(runs).markStale(workspaceId, stale.getId());
    verify(operator, never()).start(any(Job.class), any(JobParameters.class));
  }

  @Test
  void recoversAStaleExecutionBeforeRestartingItsCheckpoint() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    JobOperator operator = mock(JobOperator.class);
    Job job = mock(Job.class);
    WorkspaceSearchRunRepository runs = mock(WorkspaceSearchRunRepository.class);
    JobRepository repository = mock(JobRepository.class);
    JobExecution stale = activeExecution();
    JobExecution recovered = activeExecution();
    recovered.setStatus(BatchStatus.FAILED);
    JobExecution restarted = activeExecution();
    restarted.setStatus(BatchStatus.COMPLETED);
    FindJobsRunSummary summary =
        new FindJobsRunSummary(
            7L,
            42L,
            LocalDate.of(2026, 9, 7),
            12L,
            stale.getId(),
            "STALE",
            "STALE",
            null,
            null,
            "The search stopped updating before completion.");
    when(runs.find(workspaceId, stale.getId()))
        .thenReturn(Optional.of(new FindJobsRunDetail(summary, List.of())));
    when(repository.getJobExecution(stale.getId())).thenReturn(stale);
    when(operator.recover(stale)).thenReturn(recovered);
    when(operator.restart(recovered)).thenReturn(restarted);

    JobExecution result =
        new FindJobsService(operator, job, runs, repository, Duration.ofMinutes(30))
            .restart(workspaceId, stale.getId());

    assertThat(result).isSameAs(restarted);
    verify(operator).recover(stale);
    verify(operator).restart(recovered);
    verify(runs, times(2))
        .record(eq(workspaceId), eq(42L), eq(LocalDate.of(2026, 9, 7)), any(JobExecution.class));
  }

  private static JobExecution activeExecution() {
    JobInstance instance = new JobInstance(12L, "findJobsJob");
    JobExecution execution = new JobExecution(34L, instance, new JobParameters());
    execution.setStatus(BatchStatus.STARTED);
    return execution;
  }
}
