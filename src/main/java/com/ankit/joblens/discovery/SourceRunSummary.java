package com.ankit.joblens.discovery;

public record SourceRunSummary(
    String searchProfileId,
    String source,
    String status,
    int pagesAttempted,
    int pagesFetched,
    int recordsReceived,
    int newRecords,
    int changedRecords,
    int unchangedRecords,
    int rawRecords,
    int normalizedRecords,
    int sightedRecords,
    int scoredRecords,
    String failureReason) {}
