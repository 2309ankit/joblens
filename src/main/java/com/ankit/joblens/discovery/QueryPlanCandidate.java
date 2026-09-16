package com.ankit.joblens.discovery;

public record QueryPlanCandidate(
    String searchProfileId,
    String source,
    String originalKeywords,
    int maxPagesCeiling,
    String previousStatus,
    Integer previousRecordsReceived) {}
