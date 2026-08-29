package com.ankit.joblens.intelligence;

public record RawJobPosting(
        long id,
        String source,
        String externalJobId,
        String sourceUrl,
        String payloadHash,
        String rawJson) {
}
