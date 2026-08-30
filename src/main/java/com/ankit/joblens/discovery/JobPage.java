package com.ankit.joblens.discovery;

import java.util.List;

public record JobPage(int page, long totalCount, List<RawSourceJob> jobs, boolean hasMore) {

  public JobPage {
    jobs = List.copyOf(jobs);
  }
}
