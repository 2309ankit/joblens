package com.ankit.joblens.batchapi;

import java.time.LocalDateTime;
import java.util.List;

public record JobExecutionResponse(
    long jobExecutionId,
    long jobInstanceId,
    String jobName,
    String status,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String exitCode,
    List<StepExecutionResponse> steps) {}
