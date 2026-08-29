package com.ankit.joblens.batchapi;

import java.time.LocalDate;

public record CreateApplicationRequest(long normalizedJobId, LocalDate effectiveDate, String note) {
}
