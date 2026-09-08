package com.ankit.joblens.discovery;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.time.LocalDate;

public record FindJobsRunSummary(
    long id,
    @JsonIgnore long candidateProfileId,
    LocalDate businessDate,
    @JsonIgnore long jobInstanceId,
    @JsonIgnore long jobExecutionId,
    String status,
    String outcome,
    Instant startedAt,
    Instant completedAt,
    String failureReason) {}
