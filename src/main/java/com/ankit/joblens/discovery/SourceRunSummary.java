package com.ankit.joblens.discovery;

public record SourceRunSummary(
    String searchProfileId,
    String source,
    String countryCode,
    String location,
    String queryText,
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
    String firstZeroStage,
    String failureReason) {}
