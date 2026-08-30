package com.ankit.joblens.searchprofile;

public record SearchProfileCsvRow(
    long rowNumber,
    String rawRecord,
    String profileId,
    String source,
    String sourceKey,
    String keywords,
    String location,
    String includeSkills,
    String excludeSkills,
    String employmentType,
    String active) {}
