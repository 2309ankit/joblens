package com.ankit.joblens.batchapi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch")
public class BatchExecutionController {

    private final JobExplorer jobExplorer;

    public BatchExecutionController(JobExplorer jobExplorer) {
        this.jobExplorer = jobExplorer;
    }

    @GetMapping("/executions")
    public List<JobExecutionResponse> executions() {
        List<JobExecution> executions = new ArrayList<>();
        for (String jobName : jobExplorer.getJobNames()) {
            jobExplorer.getJobInstances(jobName, 0, 100)
                    .forEach(instance -> executions.addAll(jobExplorer.getJobExecutions(instance)));
        }
        return executions.stream()
                .sorted(Comparator.comparingLong(JobExecution::getId).reversed())
                .map(BatchExecutionController::toResponse)
                .toList();
    }

    private static JobExecutionResponse toResponse(JobExecution execution) {
        List<StepExecutionResponse> steps = execution.getStepExecutions().stream()
                .sorted(Comparator.comparingLong(StepExecution::getId))
                .map(step -> new StepExecutionResponse(
                        step.getId(),
                        step.getStepName(),
                        step.getStatus().name(),
                        step.getReadCount(),
                        step.getWriteCount(),
                        step.getSkipCount(),
                        step.getCommitCount(),
                        step.getRollbackCount()))
                .toList();
        return new JobExecutionResponse(
                execution.getId(),
                execution.getJobInstanceId(),
                execution.getJobInstance().getJobName(),
                execution.getStatus().name(),
                execution.getStartTime(),
                execution.getEndTime(),
                execution.getExitStatus().getExitCode(),
                steps);
    }
}
