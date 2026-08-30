package com.ankit.joblens.batchapi;

import com.ankit.joblens.market.*;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market-insights")
public class WeeklyMarketInsightController {
  private final WeeklyMarketInsightRepository repo;
  private final JobOperator operator;
  private final Job job;

  public WeeklyMarketInsightController(
      WeeklyMarketInsightRepository repo,
      JobOperator operator,
      @Qualifier("weeklyMarketInsightJob") Job job) {
    this.repo = repo;
    this.operator = operator;
    this.job = job;
  }

  @GetMapping
  @Operation(
      summary = "List weekly market insights",
      description =
          "Returns job counts, company counts, remote counts, and average salary by week and source")
  public Object list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return repo.find(from, to);
  }

  @PostMapping("/run")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "Run weekly market insights",
      description = "Aggregates normalized jobs for the supplied Monday week start")
  public JobLaunchResponse run(@RequestParam LocalDate weekStart) throws Exception {
    JobExecution e =
        operator.start(
            job,
            new JobParametersBuilder()
                .addLocalDate("weekStart", weekStart, true)
                .toJobParameters());
    return BatchResponses.from(e);
  }
}
