package com.ankit.joblens.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
  @Bean
  OpenAPI jobLensOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("JobLens API")
                .version("v1")
                .description("Batch-first job-market intelligence and application tracking API"));
  }
}
