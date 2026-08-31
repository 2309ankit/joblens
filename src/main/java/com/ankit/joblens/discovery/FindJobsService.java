package com.ankit.joblens.discovery;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FindJobsService {
  private final JobOperator jobOperator;
  private final Job findJobsJob;
  private final WorkspaceSearchRunRepository runs;
  private final JobRepository jobRepository;

  public FindJobsService(
      JobOperator jobOperator,
      @Qualifier("findJobsJob") Job findJobsJob,
      WorkspaceSearchRunRepository runs,
      JobRepository jobRepository) {
    this.jobOperator = jobOperator;
    this.findJobsJob = findJobsJob;
    this.runs = runs;
    this.jobRepository = jobRepository;
  }

  public JobExecution run(UUID workspaceId, long candidateProfileId, LocalDate businessDate)
      throws JobExecutionException {
    String definitionVersion = runs.definitionVersion(workspaceId);
    var parameters =
        new JobParametersBuilder()
            .addLocalDate("businessDate", businessDate, true)
            .addString("workspaceId", workspaceId.toString(), true)
            .addLong("candidateProfileId", candidateProfileId, true)
            .addString("searchDefinitionVersion", definitionVersion, true)
            .addString("normalizationVersion", "v1", true)
            .addString("duplicateDetectionVersion", "fuzzy-v1", true)
            .toJobParameters();
    JobExecution execution = jobOperator.start(findJobsJob, parameters);
    runs.record(workspaceId, candidateProfileId, businessDate, execution);
    return execution;
  }

  public List<Map<String, Object>> runs(UUID workspaceId) {
    return runs.list(workspaceId);
  }

  public Optional<FindJobsRunDetail> detail(UUID workspaceId, long jobExecutionId) {
    return runs.find(workspaceId, jobExecutionId);
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
    if (failed.getStatus() != BatchStatus.FAILED) {
      throw new IllegalStateException("Only a failed Find jobs run can be restarted");
    }
    JobExecution restarted = jobOperator.restart(failed);
    runs.record(
        workspaceId, previous.run().candidateProfileId(), previous.run().businessDate(), restarted);
    return restarted;
  }
}
