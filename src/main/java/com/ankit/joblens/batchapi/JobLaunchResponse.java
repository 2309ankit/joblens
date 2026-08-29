package com.ankit.joblens.batchapi;

import java.time.LocalDateTime;
import java.util.Map;

public record JobLaunchResponse(
        long jobExecutionId,
        long jobInstanceId,
        String jobName,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Map<String, Object> parameters) {
}
