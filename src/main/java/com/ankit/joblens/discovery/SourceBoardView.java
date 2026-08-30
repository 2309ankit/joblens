package com.ankit.joblens.discovery;

import java.time.OffsetDateTime;

public record SourceBoardView(
    String source,
    String sourceKey,
    String canonicalUrl,
    String status,
    String failureReason,
    OffsetDateTime firstDiscoveredAt,
    OffsetDateTime lastDiscoveredAt,
    OffsetDateTime validatedAt) {}
