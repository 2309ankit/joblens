package com.ankit.joblens.intelligence;

import org.springframework.batch.infrastructure.item.ItemProcessor;

public class ScoringProcessor implements ItemProcessor<NormalizedJobView, JobScore> {
  private final JobScoreCalculator c;
  private final Long candidateProfileId;

  public ScoringProcessor(JobScoreCalculator c, Long candidateProfileId) {
    this.c = c;
    this.candidateProfileId = candidateProfileId;
  }

  public JobScore process(NormalizedJobView j) {
    return c.calculate(j, candidateProfileId);
  }
}
