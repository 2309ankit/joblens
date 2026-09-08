package com.ankit.joblens.discovery;

/** Indicates that the same Find Jobs command has already been admitted by this process. */
public class ActiveFindJobsRunException extends RuntimeException {
  public ActiveFindJobsRunException() {
    super("A Find Jobs run is already in progress.");
  }
}
