package com.ankit.joblens.batchapi;

import com.ankit.joblens.onboarding.EscoProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping("/api/batch/taxonomy/esco")
@Tag(
    name = "ESCO taxonomy",
    description = "Import the pinned open ESCO skills and occupations taxonomy")
public class EscoTaxonomyController {
  private final JobOperator jobOperator;
  private final Job job;
  private final EscoProperties properties;

  public EscoTaxonomyController(
      JobOperator jobOperator,
      @Qualifier("escoTaxonomyImportJob") Job job,
      EscoProperties properties) {
    this.jobOperator = jobOperator;
    this.job = job;
    this.properties = properties;
  }

  @PostMapping("/import")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "Import the pinned ESCO taxonomy",
      description =
          "Explicitly launches the restartable local taxonomy import. HTTP pages are fetched outside database transactions and persisted idempotently before the release is activated.")
  public JobLaunchResponse importTaxonomy(@RequestParam(required = false) String taxonomyVersion)
      throws JobExecutionException {
    String version =
        taxonomyVersion == null || taxonomyVersion.isBlank()
            ? properties.version()
            : taxonomyVersion.trim();
    JobExecution execution =
        jobOperator.start(
            job,
            new JobParametersBuilder()
                .addString("taxonomyVersion", version, true)
                .addLocalDate("requestedDate", LocalDate.now(), false)
                .toJobParameters());
    return BatchResponses.from(execution);
  }
}
