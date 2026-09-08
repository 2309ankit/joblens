package com.ankit.joblens.discovery;

import com.ankit.joblens.intelligence.JobScoreCalculator;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FindJobsService {
  private final JobOperator jobOperator;
  private final Job findJobsJob;
  private final WorkspaceSearchRunRepository runs;
  private final JobRepository jobRepository;
  private final Duration staleAfter;
  private final ConcurrentMap<CommandKey, Boolean> commands = new ConcurrentHashMap<>();

  public FindJobsService(
      JobOperator jobOperator,
      @Qualifier("findJobsJob") Job findJobsJob,
      WorkspaceSearchRunRepository runs,
      JobRepository jobRepository,
      @Value("${joblens.find-jobs.stale-after:30m}") Duration staleAfter) {
    this.jobOperator = jobOperator;
    this.findJobsJob = findJobsJob;
    this.runs = runs;
    this.jobRepository = jobRepository;
    if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
      throw new IllegalArgumentException("Find Jobs stale-after must be positive");
    }
    this.staleAfter = staleAfter;
  }

  public JobExecution run(UUID workspaceId, long candidateProfileId, LocalDate businessDate)
      throws JobExecutionException {
    String definitionVersion = runs.definitionVersion(workspaceId);
    JobParameters parameters =
        new JobParametersBuilder()
            .addLocalDate("businessDate", businessDate, true)
            .addString("workspaceId", workspaceId.toString(), true)
            .addLong("candidateProfileId", candidateProfileId, true)
            .addString("searchDefinitionVersion", definitionVersion, true)
            .addString("normalizationVersion", "v1", true)
            .addString("duplicateDetectionVersion", "fuzzy-v1", true)
            .addString("rankingPolicyVersion", JobScoreCalculator.POLICY_VERSION, true)
            .toJobParameters();
    CommandKey commandKey =
        new CommandKey(workspaceId, candidateProfileId, businessDate, definitionVersion);
    if (commands.putIfAbsent(commandKey, Boolean.TRUE) != null) {
      JobExecution admitted = jobRepository.getLastJobExecution(findJobsJob.getName(), parameters);
      if (admitted != null && admitted.getStatus().isRunning()) {
        runs.record(workspaceId, candidateProfileId, businessDate, admitted);
        return admitted;
      }
      throw new ActiveFindJobsRunException();
    }
    try {
      return runAdmitted(workspaceId, candidateProfileId, businessDate, parameters);
    } finally {
      commands.remove(commandKey);
    }
  }

  private JobExecution runAdmitted(
      UUID workspaceId, long candidateProfileId, LocalDate businessDate, JobParameters parameters)
      throws JobExecutionException {
    JobExecution active = jobRepository.getLastJobExecution(findJobsJob.getName(), parameters);
    if (active != null && active.getStatus().isRunning()) {
      runs.record(workspaceId, candidateProfileId, businessDate, active);
      if (isStale(active)) {
        runs.markStale(workspaceId, active.getId());
        throw new StaleFindJobsRunException();
      }
      return active;
    }
    JobExecution execution;
    try {
      execution = jobOperator.start(findJobsJob, parameters);
    } catch (JobExecutionAlreadyRunningException exception) {
      // A concurrent request won the Batch uniqueness race.  Project that execution so the
      // workspace sees one product run instead of a framework exception.
      JobExecution concurrent =
          jobRepository.getLastJobExecution(findJobsJob.getName(), parameters);
      if (concurrent != null && concurrent.getStatus().isRunning()) {
        runs.record(workspaceId, candidateProfileId, businessDate, concurrent);
        if (isStale(concurrent)) {
          runs.markStale(workspaceId, concurrent.getId());
          throw new StaleFindJobsRunException();
        }
        return concurrent;
      }
      throw exception;
    } catch (IllegalStateException exception) {
      // Batch 6 can surface a concurrent JobInstance insert as an IllegalStateException before its
      // already-running contract is visible. Reconcile only when the repository proves a winner.
      JobExecution concurrent =
          jobRepository.getLastJobExecution(findJobsJob.getName(), parameters);
      if (concurrent != null && concurrent.getStatus().isRunning()) {
        runs.record(workspaceId, candidateProfileId, businessDate, concurrent);
        return concurrent;
      }
      throw exception;
    }
    runs.record(workspaceId, candidateProfileId, businessDate, execution);
    return execution;
  }

  public List<Map<String, Object>> runs(UUID workspaceId) {
    return runs.list(workspaceId);
  }

  public Optional<FindJobsRunDetail> detail(UUID workspaceId, long jobExecutionId) {
    return runs.find(workspaceId, jobExecutionId);
  }

  public Optional<FindJobsRunDetail> detailByRunId(UUID workspaceId, long runId) {
    return runs.executionId(workspaceId, runId)
        .flatMap(executionId -> runs.find(workspaceId, executionId));
  }

  public Optional<FindJobsRunDetail> latest(UUID workspaceId) {
    return runs.latest(workspaceId);
  }

  public JobExecution restart(UUID workspaceId, long jobExecutionId) throws JobExecutionException {
    FindJobsRunDetail previous =
        runs.find(workspaceId, jobExecutionId)
            .orElseThrow(() -> new IllegalArgumentException("Find jobs run not found"));
    JobExecution failed = jobRepository.getJobExecution(jobExecutionId);
    if (failed == null) {
      throw new IllegalArgumentException("Batch execution not found");
    }
    if (previous.run().status().equals("STALE") && failed.getStatus().isRunning()) {
      failed = jobOperator.recover(failed);
      runs.record(
          workspaceId, previous.run().candidateProfileId(), previous.run().businessDate(), failed);
    }
    if (failed.getStatus() != BatchStatus.FAILED) {
      throw new IllegalStateException("Only a failed Find jobs run can be restarted");
    }
    JobExecution restarted = jobOperator.restart(failed);
    runs.record(
        workspaceId, previous.run().candidateProfileId(), previous.run().businessDate(), restarted);
    return restarted;
  }

  public JobExecution restartByRunId(UUID workspaceId, long runId) throws JobExecutionException {
    long executionId =
        runs.executionId(workspaceId, runId)
            .orElseThrow(() -> new IllegalArgumentException("Find jobs run not found"));
    return restart(workspaceId, executionId);
  }

  private boolean isStale(JobExecution execution) {
    LocalDateTime lastUpdate = execution.getLastUpdated();
    for (var step : execution.getStepExecutions()) {
      if (step.getLastUpdated() != null
          && (lastUpdate == null || step.getLastUpdated().isAfter(lastUpdate))) {
        lastUpdate = step.getLastUpdated();
      }
    }
    return lastUpdate != null && lastUpdate.isBefore(LocalDateTime.now().minus(staleAfter));
  }

  private record CommandKey(
      UUID workspaceId,
      long candidateProfileId,
      LocalDate businessDate,
      String definitionVersion) {}
}
