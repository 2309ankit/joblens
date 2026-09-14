package com.ankit.joblens.discovery;

public record QueryPlanResult(
    String queryText, int maxPages, String rationale, Integer inputTokens, Integer outputTokens) {}
