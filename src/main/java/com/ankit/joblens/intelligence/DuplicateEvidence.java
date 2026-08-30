package com.ankit.joblens.intelligence;

public record DuplicateEvidence(long leftJobId, long rightJobId, String type, String value) {}
