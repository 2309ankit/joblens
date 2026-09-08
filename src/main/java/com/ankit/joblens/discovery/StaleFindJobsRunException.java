package com.ankit.joblens.discovery;

/** Indicates that a persisted Find Jobs execution stopped updating beyond the safe threshold. */
public class StaleFindJobsRunException extends RuntimeException {
  public StaleFindJobsRunException() {
    super("The previous search stopped updating and is ready to restart.");
  }
}
