package com.ankit.joblens.batchapi;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameter;

final class BatchResponses {

    private BatchResponses() {
    }

    static JobLaunchResponse from(JobExecution execution) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (JobParameter<?> parameter : execution.getJobParameters()) {
            parameters.put(parameter.name(), parameter.value());
        }
        return new JobLaunchResponse(
                execution.getId(),
                execution.getJobInstanceId(),
                execution.getJobInstance().getJobName(),
                execution.getStatus().name(),
                execution.getStartTime(),
                execution.getEndTime(),
                parameters);
    }
}
