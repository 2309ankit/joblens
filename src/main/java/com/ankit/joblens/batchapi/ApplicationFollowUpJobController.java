package com.ankit.joblens.batchapi;

import com.ankit.joblens.lifecycle.ApplicationFollowUpTasklet;
import java.time.LocalDate;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/batch/follow-ups")
public class ApplicationFollowUpJobController {

  private final JobOperator jobOperator;
  private final Job job;

  public ApplicationFollowUpJobController(
      JobOperator jobOperator, @Qualifier("applicationFollowUpJob") Job job) {
    this.jobOperator = jobOperator;
    this.job = job;
  }

  @PostMapping("/run")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public JobLaunchResponse run(
      @RequestParam LocalDate businessDate,
      @RequestParam(required = false) Long failAfterApplications)
      throws JobExecutionException {
    if (failAfterApplications != null && failAfterApplications < 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "failAfterApplications must be positive");
    }
    var parameters =
        new JobParametersBuilder()
            .addLocalDate("businessDate", businessDate, true)
            .addString("followUpVersion", ApplicationFollowUpTasklet.GENERATION_VERSION, true);
    if (failAfterApplications != null) {
      parameters.addLong("failAfterApplications", failAfterApplications, false);
    }
    JobExecution execution = jobOperator.start(job, parameters.toJobParameters());
    return BatchResponses.from(execution);
  }
}
