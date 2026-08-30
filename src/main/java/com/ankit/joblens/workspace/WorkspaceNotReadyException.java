package com.ankit.joblens.workspace;

public class WorkspaceNotReadyException extends IllegalStateException {
  public WorkspaceNotReadyException() {
    super("Confirm your resume and preferences first");
  }
}
