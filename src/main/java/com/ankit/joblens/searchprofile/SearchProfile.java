package com.ankit.joblens.searchprofile;

public record SearchProfile(
        String profileId,
        String source,
        String sourceKey,
        String keywords,
        String location,
        String includeSkills,
        String excludeSkills,
        String employmentType,
        boolean active) {
}
