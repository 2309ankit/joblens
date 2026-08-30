package com.ankit.joblens.discovery;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FindJobsService {
  private final JobOperator jobOperator;
  private final Job findJobsJob;
  private final WorkspaceSearchRunRepository runs;

  public FindJobsService(
      JobOperator jobOperator,
      @Qualifier("findJobsJob") Job findJobsJob,
      WorkspaceSearchRunRepository runs) {
    this.jobOperator = jobOperator;
    this.findJobsJob = findJobsJob;
    this.runs = runs;
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
}
