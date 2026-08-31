package com.ankit.joblens.onboarding;

import java.util.List;

public record IntegratedCountry(
    String code, String name, List<String> sources, String capabilityExplanation) {}
