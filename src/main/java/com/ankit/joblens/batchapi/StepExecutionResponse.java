package com.ankit.joblens.batchapi;

public record StepExecutionResponse(
    long stepExecutionId,
    String stepName,
    String status,
    long readCount,
    long writeCount,
    long skipCount,
    long commitCount,
    long rollbackCount) {}
