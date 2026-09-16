package com.ankit.joblens.discovery;

public class QueryPlanningException extends RuntimeException {
  public QueryPlanningException(String message) {
    super(message);
  }

  public QueryPlanningException(String message, Throwable cause) {
    super(message, cause);
  }
}
