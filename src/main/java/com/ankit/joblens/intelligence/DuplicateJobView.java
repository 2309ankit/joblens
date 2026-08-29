package com.ankit.joblens.intelligence;

public record DuplicateJobView(
        long id,
        String source,
        String externalJobId,
        String title,
        String company,
        String location,
        String description,
        String employmentType,
        String normalizedContentHash) {

    String sourceIdentity() {
        return source + ":" + externalJobId;
    }
}
