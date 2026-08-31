package com.ankit.joblens.onboarding;

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
@EnableConfigurationProperties(EscoProperties.class)
public class EscoTaxonomyConfiguration {
  @Bean
  EscoTaxonomyClient escoTaxonomyClient(
      WebClient.Builder builder, ObjectMapper objectMapper, EscoProperties properties) {
    return new EscoTaxonomyClient(builder, objectMapper, properties);
  }

  @Bean
  @StepScope
  EscoTaxonomyImportTasklet escoTaxonomyImportTasklet(
      EscoTaxonomyClient client,
      EscoTaxonomyRepository repository,
      EscoProperties properties,
      @Value("#{jobParameters['taxonomyVersion']}") String requestedVersion) {
    String version = requestedVersion == null ? properties.version() : requestedVersion;
    return new EscoTaxonomyImportTasklet(client, repository, version);
  }

  @Bean
  Step escoTaxonomyImportStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      EscoTaxonomyImportTasklet tasklet) {
    DefaultTransactionAttribute noTransaction = new DefaultTransactionAttribute();
    noTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    return new StepBuilder("escoTaxonomyImportStep", jobRepository)
        .tasklet(tasklet, transactionManager)
        .transactionAttribute(noTransaction)
        .build();
  }

  @Bean
  Job escoTaxonomyImportJob(JobRepository jobRepository, Step escoTaxonomyImportStep) {
    return new JobBuilder("escoTaxonomyImportJob", jobRepository)
        .start(escoTaxonomyImportStep)
        .build();
  }
}
