package com.ankit.joblens.discovery;

import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({
  AdzunaProperties.class,
  GreenhouseProperties.class,
  JoobleProperties.class,
  LeverProperties.class
})
public class JobDiscoveryConfiguration {

  @Bean
  AdzunaJobSourceClient adzunaJobSourceClient(
      WebClient.Builder webClientBuilder, ObjectMapper objectMapper, AdzunaProperties properties) {
    return new AdzunaJobSourceClient(webClientBuilder, objectMapper, properties);
  }

  @Bean
  GreenhouseJobSourceClient greenhouseJobSourceClient(
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      GreenhouseProperties properties) {
    return new GreenhouseJobSourceClient(webClientBuilder, objectMapper, properties);
  }

  @Bean
  JoobleJobSourceClient joobleJobSourceClient(
      WebClient.Builder webClientBuilder, ObjectMapper objectMapper, JoobleProperties properties) {
    return new JoobleJobSourceClient(webClientBuilder, objectMapper, properties);
  }

  @Bean
  LeverJobSourceClient leverJobSourceClient(
      WebClient.Builder webClientBuilder, ObjectMapper objectMapper, LeverProperties properties) {
    return new LeverJobSourceClient(webClientBuilder, objectMapper, properties);
  }

  @Bean
  @StepScope
  JobDiscoveryTasklet jobDiscoveryTasklet(
      DiscoveryPersistenceService persistence,
      List<JobSourceClient> clients,
      AdzunaProperties properties,
      @Value("#{jobParameters['profileId']}") String requestedProfileId,
      @Value("#{jobParameters['workspaceId']}") String workspaceId,
      FailureReasonSanitizer failureReasons) {
    return new JobDiscoveryTasklet(
        persistence, clients, properties, requestedProfileId, workspaceId, failureReasons);
  }

  @Bean
  Step jobDiscoveryStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      JobDiscoveryTasklet jobDiscoveryTasklet) {
    DefaultTransactionAttribute noTransaction = new DefaultTransactionAttribute();
    noTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    return new StepBuilder("jobDiscoveryStep", jobRepository)
        .tasklet(jobDiscoveryTasklet, transactionManager)
        .transactionAttribute(noTransaction)
        .build();
  }

  @Bean
  Job jobDiscoveryJob(JobRepository jobRepository, Step jobDiscoveryStep) {
    return new JobBuilder("jobDiscoveryJob", jobRepository).start(jobDiscoveryStep).build();
  }
}
