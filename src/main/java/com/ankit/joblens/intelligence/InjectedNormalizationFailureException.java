package com.ankit.joblens.intelligence;

public class InjectedNormalizationFailureException extends RuntimeException {

  public InjectedNormalizationFailureException(long rawJobPostingId) {
    super("Injected normalization failure at raw job posting " + rawJobPostingId);
  }
}
