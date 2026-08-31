package com.ankit.joblens.discovery;

import java.time.Instant;
import java.time.LocalDate;

public record FindJobsRunSummary(
    long id,
    long candidateProfileId,
    LocalDate businessDate,
    long jobInstanceId,
    long jobExecutionId,
    String batchStatus,
    String outcome,
    Instant startedAt,
    Instant completedAt,
    String failureReason) {}
