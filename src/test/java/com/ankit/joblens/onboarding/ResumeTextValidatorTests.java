package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResumeTextValidatorTests {
  @Test
  void acceptsAResumeWithContactDetailsAndRecognizableSections() {
    String resume =
        """
        Ankit Kumar
        ankit@example.com

        Professional Summary
        Backend engineer building Java services.

        Experience
        Software Engineer, Example Bank, 2020 - 2026

        Technical Skills
        Java, Spring Boot, PostgreSQL

        Education
        Bachelor of Engineering
        """;

    assertThatCode(() -> ResumeTextValidator.validate(resume)).doesNotThrowAnyException();
  }

  @Test
  void rejectsSoftwareInterviewRequirementsEvenWhenTheyContainKnownSkills() {
    String requirement =
        """
        Software Engineer interview requirements
        Job description
        We are looking for a Java and Spring Boot engineer.
        Key responsibilities
        The candidate must design APIs and use PostgreSQL.
        Technical skills
        Java, Spring Boot, Docker
        """;

    assertThatThrownBy(() -> ResumeTextValidator.validate(requirement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a resume");
  }

  @Test
  void rejectsAnUnstructuredSkillDocument() {
    String document =
        "Java Spring Boot PostgreSQL Docker AWS Kafka. This document contains enough readable "
            + "software words but no candidate identity, work history, or education sections.";

    assertThatThrownBy(() -> ResumeTextValidator.validate(document))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a resume");
  }
}
