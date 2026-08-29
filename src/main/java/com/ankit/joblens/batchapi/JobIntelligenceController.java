package com.ankit.joblens.batchapi;

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
@RequestMapping("/api/batch/intelligence")
public class JobIntelligenceController {

    private final JobOperator jobOperator;
    private final Job jobIntelligenceJob;

    public JobIntelligenceController(JobOperator jobOperator,
            @Qualifier("jobIntelligenceJob") Job jobIntelligenceJob) {
        this.jobOperator = jobOperator;
        this.jobIntelligenceJob = jobIntelligenceJob;
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobLaunchResponse run(
            @RequestParam LocalDate businessDate,
            @RequestParam(required = false) Long failAfterItems,
            @RequestParam(required = false, defaultValue = "false") boolean failDuplicateDetection)
            throws JobExecutionException {
        if (failAfterItems != null && failAfterItems < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failAfterItems must be positive");
        }
        JobParametersBuilder parameters = new JobParametersBuilder()
                .addLocalDate("businessDate", businessDate, true)
                .addString("normalizationVersion", "v1", true);
        if (failAfterItems != null) {
            parameters.addLong("failAfterItems", failAfterItems, false);
        }
        if (failDuplicateDetection) {
            parameters.addLong("failDuplicateDetection", 1L, false);
        }
        JobExecution execution = jobOperator.start(jobIntelligenceJob, parameters.toJobParameters());
        return BatchResponses.from(execution);
    }
}
