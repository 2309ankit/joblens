package com.ankit.joblens.discovery;

/** Safe acknowledgement for a user-initiated Find Jobs command. */
public record FindJobsLaunchResponse(long runId, String status, String outcome) {}
