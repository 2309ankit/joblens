package com.ankit.joblens.onboarding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// NOOP fallback when semantic matching is disabled, so the heavier ONNX model bean never loads.
@Configuration
public class SemanticMatchingConfiguration {
  @Bean
  @ConditionalOnProperty(
      prefix = "joblens.onboarding.semantic-matching",
      name = "enabled",
      havingValue = "false")
  public SemanticSkillMatcher disabledSemanticSkillMatcher() {
    return SemanticSkillMatcher.NOOP;
  }
}
