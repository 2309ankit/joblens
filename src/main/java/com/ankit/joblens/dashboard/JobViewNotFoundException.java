package com.ankit.joblens.dashboard;

public class JobViewNotFoundException extends RuntimeException {
  public JobViewNotFoundException(long jobId) {
    super("Job " + jobId + " was not found");
  }
}
