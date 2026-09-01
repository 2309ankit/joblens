package com.ankit.joblens.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ResumeReadinessAdvisorTests {
  private final ResumeReadinessAdvisor advisor = new ResumeReadinessAdvisor();

  @Test
  void reportsDeterministicReadableResumeEvidenceWithoutScoringCandidateQuality() {
    var assessment =
        advisor.assess(
            """
            Priya Shah
            priya@example.com
            Professional Summary
            Frontend engineer building accessible interfaces.
            Experience
            Senior Frontend Engineer — Example Retail
            2022 - Present
            Delivered a reusable design system.
            Technical Skills
            JavaScript, TypeScript, React
            Education
            Bachelor of Design
            """,
            "application/pdf",
            "%PDF representative text fixture".getBytes(StandardCharsets.UTF_8));

    assertThat(assessment.status()).isEqualTo("READY");
    assertThat(assessment.score()).isEqualTo(100);
    assertThat(assessment.findings())
        .extracting(ResumeReadinessAssessment.Finding::code)
        .contains(
            "CONTACT_DETAILS_FOUND",
            "STANDARD_SECTIONS_FOUND",
            "JOB_TITLE_FOUND",
            "EMPLOYMENT_DATES_FOUND",
            "EDUCATION_SECTION_FOUND",
            "PARSING_QUALITY_GOOD",
            "RESUME_LENGTH_REASONABLE");
  }

  @Test
  void marksSuspiciousReadableDocumentsForAcknowledgementInsteadOfRejectingThem() {
    var assessment =
        advisor.assess(
            """
            Software Engineer interview requirements
            Job description
            We are looking for a Java developer.
            Key responsibilities include service delivery.
            The candidate must answer the screening questions.
            """,
            "application/pdf",
            "%PDF suspicious but readable fixture".getBytes(StandardCharsets.UTF_8));

    assertThat(assessment.status()).isEqualTo("REVIEW_REQUIRED");
    assertThat(assessment.findings())
        .filteredOn(finding -> finding.code().equals("DOCUMENT_TYPE_UNCERTAIN"))
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.severity()).isEqualTo("REVIEW");
              assertThat(finding.evidence()).contains("Requirement signals");
            });
  }

  @Test
  void reportsOnlyLayoutRisksMeasurableFromDocxMarkup() throws Exception {
    var assessment =
        advisor.assess(
            """
            Alex Example
            alex@example.com
            Summary
            Operations leader.
            Experience
            Operations Manager — Example Company
            2020 - Present
            Education
            Bachelor of Business
            """,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            riskyDocx());

    assertThat(assessment.status()).isEqualTo("REVIEW_REQUIRED");
    assertThat(assessment.findings())
        .extracting(ResumeReadinessAssessment.Finding::code)
        .contains("DOCX_TABLE_LAYOUT", "DOCX_TEXT_BOX", "DOCX_HEADER_FOOTER", "DOCX_GRAPHICS");
  }

  private static byte[] riskyDocx() throws Exception {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      entry(
          zip,
          "word/document.xml",
          "<w:document><w:tbl/><w:txbxContent/><w:drawing/><wp:anchor/></w:document>");
      entry(zip, "word/header1.xml", "<w:hdr><w:t>alex@example.com</w:t></w:hdr>");
    }
    return output.toByteArray();
  }

  private static void entry(ZipOutputStream zip, String name, String value) throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(value.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
