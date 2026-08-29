package com.ankit.joblens.lifecycle;

import java.time.LocalDate;

public record FollowUpApplication(
        long applicationId,
        ApplicationStatus status,
        LocalDate statusEffectiveDate,
        long historyId) {
}
