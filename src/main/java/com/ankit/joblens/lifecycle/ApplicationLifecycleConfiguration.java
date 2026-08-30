package com.ankit.joblens.lifecycle;

import java.time.LocalDate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ApplicationLifecycleConfiguration {

  @Bean
  @StepScope
  ApplicationFollowUpTasklet applicationFollowUpTasklet(
      ApplicationLifecycleRepository repository,
      ApplicationLifecyclePolicy policy,
      JobRepository jobRepository,
      @Value("#{jobParameters['businessDate']}") LocalDate businessDate,
      @Value("#{jobParameters['candidateProfileId']}") Long candidateProfileId,
      @Value("#{jobParameters['failAfterApplications']}") Long failAfterApplications,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    boolean firstExecution =
        jobRepository.getJobExecutions(stepExecution.getJobExecution().getJobInstance()).size()
            == 1;
    return new ApplicationFollowUpTasklet(
        repository,
        policy,
        businessDate,
        candidateProfileId,
        failAfterApplications,
        firstExecution);
  }

  @Bean
  Step applicationFollowUpGenerationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ApplicationFollowUpTasklet tasklet) {
    return new StepBuilder("applicationFollowUpGenerationStep", jobRepository)
        .tasklet(tasklet, transactionManager)
        .build();
  }

  @Bean
  Job applicationFollowUpJob(JobRepository jobRepository, Step applicationFollowUpGenerationStep) {
    return new JobBuilder("applicationFollowUpJob", jobRepository)
        .start(applicationFollowUpGenerationStep)
        .build();
  }
}
