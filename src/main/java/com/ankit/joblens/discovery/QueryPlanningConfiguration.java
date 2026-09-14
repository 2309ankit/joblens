package com.ankit.joblens.discovery;

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
@EnableConfigurationProperties(LlmQueryPlanningProperties.class)
public class QueryPlanningConfiguration {

  @Bean
  QueryPlanningClient queryPlanningClient(
      WebClient.Builder builder, ObjectMapper objectMapper, LlmQueryPlanningProperties properties) {
    return new QueryPlanningClient(builder, objectMapper, properties);
  }

  @Bean
  QueryPlanningRepository queryPlanningRepository(NamedParameterJdbcTemplate jdbc) {
    return new QueryPlanningRepository(jdbc);
  }

  @Bean
  @StepScope
  AgenticQueryPlanningTasklet agenticQueryPlanningTasklet(
      QueryPlanningRepository repository,
      QueryPlanningClient client,
      LlmQueryPlanningProperties properties,
      @Value("#{jobParameters['workspaceId']}") String workspaceId) {
    return new AgenticQueryPlanningTasklet(repository, client, properties, workspaceId);
  }

  @Bean
  Step agenticQueryPlanningStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      AgenticQueryPlanningTasklet tasklet) {
    DefaultTransactionAttribute noTransaction = new DefaultTransactionAttribute();
    noTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    return new StepBuilder("agenticQueryPlanningStep", jobRepository)
        .tasklet(tasklet, transactionManager)
        .transactionAttribute(noTransaction)
        .build();
  }
}
