package com.ankit.joblens.lifecycle;

import java.time.LocalDate;

public record FollowUpRecord(
    long id, long applicationId, long candidateProfileId, String status, LocalDate dueDate) {}
