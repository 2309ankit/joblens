package com.ankit.joblens.lifecycle;

import java.time.LocalDate;

public record FollowUpRecord(long id, long applicationId, String status, LocalDate dueDate) {
}
