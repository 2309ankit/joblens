package com.ankit.joblens.intelligence;

public class NormalizationRejectedException extends RuntimeException {

  private final RawJobPosting rawJobPosting;

  public NormalizationRejectedException(RawJobPosting rawJobPosting, String message) {
    super(message);
    this.rawJobPosting = rawJobPosting;
  }

  public NormalizationRejectedException(
      RawJobPosting rawJobPosting, String message, Throwable cause) {
    super(message, cause);
    this.rawJobPosting = rawJobPosting;
  }

  public RawJobPosting rawJobPosting() {
    return rawJobPosting;
  }
}
