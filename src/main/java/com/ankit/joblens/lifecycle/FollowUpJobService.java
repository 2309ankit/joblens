package com.ankit.joblens.lifecycle;

import java.time.LocalDate;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FollowUpJobService {
  private final JobOperator jobOperator;
  private final Job job;
  private final ApplicationLifecycleRepository repository;

  public FollowUpJobService(
      JobOperator jobOperator,
      @Qualifier("applicationFollowUpJob") Job job,
      ApplicationLifecycleRepository repository) {
    this.jobOperator = jobOperator;
    this.job = job;
    this.repository = repository;
  }

  public JobExecution run(
      LocalDate businessDate, Long candidateProfileId, Long failAfterApplications)
      throws JobExecutionException {
    var parameters =
        new JobParametersBuilder()
            .addLocalDate("businessDate", businessDate, true)
            .addString("followUpVersion", ApplicationFollowUpTasklet.GENERATION_VERSION, true)
            .addLong(
                "applicationStateVersion",
                repository.currentStateVersion(candidateProfileId),
                true);
    if (candidateProfileId != null) {
      parameters.addLong("candidateProfileId", candidateProfileId, true);
    }
    if (failAfterApplications != null) {
      parameters.addLong("failAfterApplications", failAfterApplications, false);
    }
    return jobOperator.start(job, parameters.toJobParameters());
  }
}
