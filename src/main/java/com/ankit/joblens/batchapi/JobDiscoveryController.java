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

@RestController
@RequestMapping("/api/batch/discovery")
public class JobDiscoveryController {

    private final JobOperator jobOperator;
    private final Job jobDiscoveryJob;

    public JobDiscoveryController(JobOperator jobOperator,
            @Qualifier("jobDiscoveryJob") Job jobDiscoveryJob) {
        this.jobOperator = jobOperator;
        this.jobDiscoveryJob = jobDiscoveryJob;
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobLaunchResponse run(
            @RequestParam LocalDate businessDate,
            @RequestParam(required = false) String profileId) throws JobExecutionException {
        JobParametersBuilder parameters = new JobParametersBuilder()
                .addLocalDate("businessDate", businessDate, true);
        if (profileId != null && !profileId.isBlank()) {
            parameters.addString("profileId", profileId.trim(), true);
        }
        JobExecution execution = jobOperator.start(jobDiscoveryJob, parameters.toJobParameters());
        return BatchResponses.from(execution);
    }
}
