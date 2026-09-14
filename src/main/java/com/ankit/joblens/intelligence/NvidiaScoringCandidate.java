package com.ankit.joblens.intelligence;

public record NvidiaScoringCandidate(
    NormalizedJobView job,
    int deterministicScore,
    boolean deterministicQualifiesRecommended,
    Long profileVersionId) {}
