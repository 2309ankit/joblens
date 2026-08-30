package com.ankit.joblens.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record WeeklyMarketInsight(
    long id,
    LocalDate weekStart,
    String source,
    int jobCount,
    int companyCount,
    int remoteJobCount,
    BigDecimal averageSalary,
    OffsetDateTime generatedAt) {}
