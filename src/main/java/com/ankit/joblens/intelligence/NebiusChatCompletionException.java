package com.ankit.joblens.intelligence;

public class NebiusChatCompletionException extends RuntimeException {
  public NebiusChatCompletionException(String message) {
    super(message);
  }

  public NebiusChatCompletionException(String message, Throwable cause) {
    super(message, cause);
  }
}
