package com.ankit.joblens.discovery;

public record PageRequest(int page, int pageSize) {

  public PageRequest {
    if (page < 1) {
      throw new IllegalArgumentException("page must be at least 1");
    }
    if (pageSize < 1) {
      throw new IllegalArgumentException("pageSize must be at least 1");
    }
  }
}
