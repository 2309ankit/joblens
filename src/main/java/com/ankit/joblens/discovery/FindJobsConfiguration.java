package com.ankit.joblens.discovery;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FindJobsConfiguration {

  @Bean
  Job findJobsJob(
      JobRepository jobRepository,
      @Qualifier("jobDiscoveryStep") Step discovery,
      @Qualifier("jobNormalizationStep") Step normalization,
      @Qualifier("skillExtractionStep") Step skills,
      @Qualifier("exactDuplicateDetectionStep") Step exactDuplicates,
      @Qualifier("fuzzyDuplicateDetectionStep") Step fuzzyDuplicates,
      @Qualifier("scoringStep") Step scoring) {
    return new JobBuilder("findJobsJob", jobRepository)
        .start(discovery)
        .next(normalization)
        .next(skills)
        .next(exactDuplicates)
        .next(fuzzyDuplicates)
        .next(scoring)
        .build();
  }
}
