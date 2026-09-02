package com.ankit.joblens.onboarding;

public record ProviderQueryPreview(
    String countryCode,
    String location,
    String role,
    String query,
    String generationVersion,
    String origin,
    int priority) {}
