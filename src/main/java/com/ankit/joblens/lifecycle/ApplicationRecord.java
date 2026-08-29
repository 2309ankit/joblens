package com.ankit.joblens.lifecycle;

import java.time.LocalDate;

public record ApplicationRecord(
        long id,
        long normalizedJobId,
        long candidateProfileId,
        ApplicationStatus status,
        LocalDate statusEffectiveDate,
        LocalDate appliedOn,
        String note) {
}
