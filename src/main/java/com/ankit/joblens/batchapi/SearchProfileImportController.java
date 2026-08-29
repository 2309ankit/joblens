package com.ankit.joblens.batchapi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/batch/search-profiles")
public class SearchProfileImportController {

    private final JobLauncher jobLauncher;
    private final Job searchProfileImportJob;

    public SearchProfileImportController(
            JobLauncher jobLauncher,
            @Qualifier("searchProfileImportJob") Job searchProfileImportJob) {
        this.jobLauncher = jobLauncher;
        this.searchProfileImportJob = searchProfileImportJob;
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobLaunchResponse importProfiles(
            @RequestParam String inputFile,
            @RequestParam LocalDate businessDate,
            @RequestParam(required = false) Long failOnRow) throws JobExecutionException {
        Path path = Path.of(inputFile).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Input file does not exist or is unreadable: " + inputFile);
        }
        if (failOnRow != null && failOnRow < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failOnRow must refer to a data row (2 or greater)");
        }

        JobParametersBuilder builder = new JobParametersBuilder()
                .addString("inputFile", path.toString(), true)
                .addLocalDate("businessDate", businessDate, true);
        if (failOnRow != null) {
            builder.addLong("failOnRow", failOnRow, false);
        }

        JobExecution execution = jobLauncher.run(searchProfileImportJob, builder.toJobParameters());
        return toResponse(execution);
    }

    private static JobLaunchResponse toResponse(JobExecution execution) {
        return new JobLaunchResponse(
                execution.getId(),
                execution.getJobInstanceId(),
                execution.getJobInstance().getJobName(),
                execution.getStatus().name(),
                execution.getStartTime(),
                execution.getEndTime(),
                parameters(execution.getJobParameters()));
    }

    private static Map<String, Object> parameters(JobParameters jobParameters) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (JobParameter<?> parameter : jobParameters) {
            values.put(parameter.name(), parameter.value());
        }
        return values;
    }
}
