package com.ankit.joblens.intelligence;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(NvidiaRankingProperties.class)
public class NvidiaRankingConfiguration {

  @Bean
  NebiusNvidiaScoringClient nebiusNvidiaScoringClient(
      WebClient.Builder builder, ObjectMapper objectMapper, NvidiaRankingProperties properties) {
    return new NebiusNvidiaScoringClient(builder, objectMapper, properties);
  }

  @Bean
  NvidiaScoringRepository nvidiaScoringRepository(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    return new NvidiaScoringRepository(jdbc, objectMapper);
  }

  @Bean
  @StepScope
  NvidiaScoringTasklet nvidiaScoringTasklet(
      NvidiaScoringRepository repository,
      NebiusNvidiaScoringClient client,
      NvidiaRankingProperties properties,
      JobScoreCalculator scoreCalculator,
      ObjectMapper objectMapper,
      @Value("#{jobParameters['candidateProfileId']}") Long candidateProfileId,
      @Value("#{jobParameters['workspaceId']}") String workspaceId) {
    return new NvidiaScoringTasklet(
        repository,
        client,
        properties,
        scoreCalculator,
        objectMapper,
        candidateProfileId,
        workspaceId);
  }

  @Bean
  Step nvidiaScoringStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      NvidiaScoringTasklet tasklet) {
    DefaultTransactionAttribute noTransaction = new DefaultTransactionAttribute();
    noTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    return new StepBuilder("nvidiaScoringStep", jobRepository)
        .tasklet(tasklet, transactionManager)
        .transactionAttribute(noTransaction)
        .build();
  }
}
