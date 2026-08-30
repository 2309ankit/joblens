package com.ankit.joblens.onboarding;

import java.util.List;

public record OnboardingProfile(
    long id,
    int version,
    String status,
    String summary,
    List<String> targetRoles,
    List<String> targetDomains,
    String primaryLocation,
    List<String> skills) {}
