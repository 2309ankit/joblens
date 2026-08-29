package com.ankit.joblens.discovery;

public record RawSourceJob(String externalJobId, String sourceUrl, String rawJson, String payloadHash) {
}
