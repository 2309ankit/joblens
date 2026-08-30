package com.ankit.joblens.batchapi;

import java.time.LocalDate;

public record ApplicationTransitionRequest(String status, LocalDate effectiveDate, String note) {}
