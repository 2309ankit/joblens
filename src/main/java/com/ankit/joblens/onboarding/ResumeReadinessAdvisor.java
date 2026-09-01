package com.ankit.joblens.onboarding;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class ResumeReadinessAdvisor {
  public static final String ASSESSMENT_VERSION = "readability-v1";
  private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
  private static final Pattern PHONE = Pattern.compile("(?m)(?:\\+?\\d[\\d ()-]{7,}\\d)");
  private static final Pattern EXPERIENCE =
      Pattern.compile("(?im)^\\s*(professional |work |employment )?experience\\s*:?\\s*$");
  private static final Pattern EDUCATION =
      Pattern.compile("(?im)^\\s*(education|academic background|qualifications)\\s*:?\\s*$");
  private static final Pattern SKILLS =
      Pattern.compile("(?im)^\\s*(technical |core )?skills( and technologies)?\\s*:?\\s*$");
  private static final Pattern SUMMARY =
      Pattern.compile("(?im)^\\s*(professional |career )?(summary|profile|objective)\\s*:?\\s*$");
  private static final Pattern EMPLOYMENT_DATE =
      Pattern.compile(
          "(?i)\\b(?:19|20)\\d{2}\\s*(?:[-–—]|to)\\s*(?:(?:19|20)\\d{2}|present|current)\\b");
  private static final Pattern JOB_TITLE_LINE =
      Pattern.compile("(?im)^.{2,80}(?:\\s[-–—|]\\s).{2,100}$");
  private static final Pattern SPACED_LETTERS =
      Pattern.compile("(?m)(?:\\b[A-Za-z]\\s+){4,}[A-Za-z]\\b");
  private static final List<Pattern> REQUIREMENT_SIGNALS =
      List.of(
          Pattern.compile("(?i)\\bjob description\\b"),
          Pattern.compile(
              "(?i)\\b(interview|screening) (requirements?|questions?|instructions?)\\b"),
          Pattern.compile("(?i)\\b(key )?responsibilities\\b"),
          Pattern.compile("(?i)\\b(candidate|applicant) (must|should|will)\\b"),
          Pattern.compile("(?i)\\bwe are (looking|hiring|seeking)\\b"));

  public DraftAssessment assess(String text, String contentType, byte[] content) {
    String normalized = text.replace('\r', '\n').trim();
    int words = (int) Pattern.compile("\\S+").matcher(normalized).results().count();
    var findings = new ArrayList<ResumeReadinessAssessment.Finding>();

    boolean contact = EMAIL.matcher(normalized).find() || PHONE.matcher(normalized).find();
    add(
        findings,
        contact ? "CONTACT_DETAILS_FOUND" : "CONTACT_DETAILS_MISSING",
        "CONTACT",
        contact ? "PASS" : "WARNING",
        contact
            ? "Readable contact details were detected."
            : "No readable email address or phone number was detected.",
        "Keep an email address or phone number in the main document body, not only in a header, footer, image, or text box.",
        contact ? firstMatch(normalized, EMAIL, PHONE) : null,
        contact ? 0 : 15);

    int sections = countMatches(normalized, EXPERIENCE, EDUCATION, SKILLS, SUMMARY);
    add(
        findings,
        sections >= 2 ? "STANDARD_SECTIONS_FOUND" : "STANDARD_SECTIONS_LIMITED",
        "SECTIONS",
        sections >= 2 ? "PASS" : "WARNING",
        sections >= 2
            ? "Multiple standard resume sections were detected."
            : "Fewer than two standard resume section headings were detected.",
        "Use plain headings such as Summary, Experience, Skills, and Education.",
        "Detected standard headings: " + sections,
        sections >= 2 ? 0 : 20);

    boolean jobTitle = JOB_TITLE_LINE.matcher(normalized).find();
    addBooleanFinding(
        findings,
        jobTitle,
        "JOB_TITLE_FOUND",
        "JOB_TITLE_MISSING",
        "EMPLOYMENT",
        "A job-title and organization style line was detected.",
        "No reliably structured job-title line was detected.",
        "Write each role and organization as plain text on one line, followed by its dates.",
        firstMatch(normalized, JOB_TITLE_LINE),
        10);

    boolean dates = EMPLOYMENT_DATE.matcher(normalized).find();
    addBooleanFinding(
        findings,
        dates,
        "EMPLOYMENT_DATES_FOUND",
        "EMPLOYMENT_DATES_MISSING",
        "EMPLOYMENT",
        "An employment date range was detected.",
        "No employment date range was detected.",
        "Use an explicit range such as 2022 - Present for each position.",
        firstMatch(normalized, EMPLOYMENT_DATE),
        10);

    boolean education = EDUCATION.matcher(normalized).find();
    addBooleanFinding(
        findings,
        education,
        "EDUCATION_SECTION_FOUND",
        "EDUCATION_SECTION_MISSING",
        "EDUCATION",
        "A standard education section was detected.",
        "No standard education section was detected.",
        "Use a plain Education or Qualifications heading when education is relevant.",
        education ? "Education heading" : null,
        10);

    long replacementCharacters = normalized.chars().filter(value -> value == 0xfffd).count();
    boolean poorParsing =
        SPACED_LETTERS.matcher(normalized).find()
            || replacementCharacters > Math.max(2, normalized.length() / 100);
    add(
        findings,
        poorParsing ? "PARSING_QUALITY_LOW" : "PARSING_QUALITY_GOOD",
        "PARSING",
        poorParsing ? "REVIEW" : "PASS",
        poorParsing
            ? "Extracted text contains patterns that may not parse as normal words."
            : "Extracted text is readable without measured character corruption.",
        "Export the resume as a text-based PDF or DOCX and verify that its text can be selected and copied normally.",
        poorParsing ? limitedEvidence(normalized) : "Readable characters: " + normalized.length(),
        poorParsing ? 25 : 0);

    boolean excessive = words > 2000;
    add(
        findings,
        excessive ? "RESUME_LENGTH_EXCESSIVE" : "RESUME_LENGTH_REASONABLE",
        "LENGTH",
        excessive ? "WARNING" : "PASS",
        excessive
            ? "The extracted resume is unusually long for reliable review."
            : "The extracted resume length is within the advisor's readability range.",
        "Remove repeated or irrelevant material while preserving evidence needed for the target role.",
        "Extracted words: " + words,
        excessive ? 5 : 0);

    FormatSignals format = inspectFormat(contentType, content);
    addFormatFindings(findings, format, contentType);

    long requirementSignals =
        REQUIREMENT_SIGNALS.stream().filter(pattern -> pattern.matcher(normalized).find()).count();
    boolean suspicious = requirementSignals >= 2 && sections < 2;
    if (suspicious) {
      add(
          findings,
          "DOCUMENT_TYPE_UNCERTAIN",
          "DOCUMENT_TYPE",
          "REVIEW",
          "The document contains several vacancy or interview signals but limited resume structure.",
          "Confirm that this is your career resume before activation, or upload the correct document.",
          "Requirement signals: " + requirementSignals + "; standard headings: " + sections,
          30);
    }

    int score =
        Math.max(
            0,
            100
                - findings.stream()
                    .mapToInt(ResumeReadinessAssessment.Finding::scoreDeduction)
                    .sum());
    String status =
        findings.stream().anyMatch(finding -> "REVIEW".equals(finding.severity()))
            ? "REVIEW_REQUIRED"
            : "READY";
    return new DraftAssessment(status, score, normalized.length(), words, List.copyOf(findings));
  }

  private static void addFormatFindings(
      List<ResumeReadinessAssessment.Finding> findings, FormatSignals format, String contentType) {
    if (!contentType.contains("wordprocessingml")) {
      add(
          findings,
          "FORMAT_NO_MEASURED_RISK",
          "FORMAT",
          "PASS",
          "No file-type-specific layout risk was measured from the extracted content.",
          "Keep important content as selectable text in the main document body.",
          contentType,
          0);
      return;
    }
    if (!format.any()) {
      add(
          findings,
          "DOCX_SIMPLE_LAYOUT",
          "FORMAT",
          "PASS",
          "The DOCX uses a simple main-document layout.",
          "Keep important content in ordinary paragraphs.",
          "No tables, text boxes, headers, footers, or drawings detected",
          0);
    }
    if (format.tables()) {
      add(
          findings,
          "DOCX_TABLE_LAYOUT",
          "FORMAT",
          "WARNING",
          "The DOCX contains a table, which some parsers may read out of order.",
          "Prefer ordinary paragraphs for employment, education, and contact details.",
          "DOCX table markup detected",
          5);
    }
    if (format.textBoxes()) {
      add(
          findings,
          "DOCX_TEXT_BOX",
          "FORMAT",
          "REVIEW",
          "The DOCX contains a text box that may be skipped or read out of order.",
          "Move essential content from text boxes into the main document body.",
          "DOCX text-box markup detected",
          15);
    }
    if (format.headersOrFooters()) {
      add(
          findings,
          "DOCX_HEADER_FOOTER",
          "FORMAT",
          "REVIEW",
          "The DOCX contains header or footer content that may not populate candidate fields.",
          "Keep name and contact information in the main document body.",
          "DOCX header or footer part detected",
          10);
    }
    if (format.drawings()) {
      add(
          findings,
          "DOCX_GRAPHICS",
          "FORMAT",
          "WARNING",
          "The DOCX contains drawings or graphics that may not contribute readable text.",
          "Do not rely on graphics to communicate qualifications or contact details.",
          "DOCX drawing markup detected",
          5);
    }
  }

  private static FormatSignals inspectFormat(String contentType, byte[] content) {
    if (!contentType.contains("wordprocessingml")) {
      return new FormatSignals(false, false, false, false);
    }
    boolean tables = false;
    boolean textBoxes = false;
    boolean headers = false;
    boolean drawings = false;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        String name = entry.getName().toLowerCase(Locale.ROOT);
        headers |= name.startsWith("word/header") || name.startsWith("word/footer");
        if (name.endsWith(".xml")) {
          String xml = new String(zip.readNBytes(2_000_000), StandardCharsets.UTF_8);
          tables |= xml.contains("<w:tbl");
          textBoxes |= xml.contains("txbxContent");
          drawings |= xml.contains("<w:drawing") || xml.contains("<wp:anchor");
        }
      }
    } catch (Exception exception) {
      return new FormatSignals(false, false, false, false);
    }
    return new FormatSignals(tables, textBoxes, headers, drawings);
  }

  private static void addBooleanFinding(
      List<ResumeReadinessAssessment.Finding> findings,
      boolean present,
      String passCode,
      String missingCode,
      String category,
      String passMessage,
      String missingMessage,
      String remediation,
      String evidence,
      int deduction) {
    add(
        findings,
        present ? passCode : missingCode,
        category,
        present ? "PASS" : "WARNING",
        present ? passMessage : missingMessage,
        remediation,
        evidence,
        present ? 0 : deduction);
  }

  private static void add(
      List<ResumeReadinessAssessment.Finding> findings,
      String code,
      String category,
      String severity,
      String message,
      String remediation,
      String evidence,
      int deduction) {
    findings.add(
        new ResumeReadinessAssessment.Finding(
            code,
            category,
            severity,
            message,
            remediation,
            evidence == null ? null : truncate(evidence),
            deduction));
  }

  private static int countMatches(String text, Pattern... patterns) {
    return (int) Set.of(patterns).stream().filter(pattern -> pattern.matcher(text).find()).count();
  }

  private static String firstMatch(String text, Pattern... patterns) {
    for (Pattern pattern : patterns) {
      var matcher = pattern.matcher(text);
      if (matcher.find()) {
        return matcher.group();
      }
    }
    return null;
  }

  private static String limitedEvidence(String text) {
    var spaced = SPACED_LETTERS.matcher(text);
    return spaced.find() ? spaced.group() : "Replacement or unreadable characters detected";
  }

  private static String truncate(String value) {
    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.length() <= 300 ? normalized : normalized.substring(0, 297) + "...";
  }

  public record DraftAssessment(
      String status,
      int score,
      int extractedCharacterCount,
      int wordCount,
      List<ResumeReadinessAssessment.Finding> findings) {}

  private record FormatSignals(
      boolean tables, boolean textBoxes, boolean headersOrFooters, boolean drawings) {
    boolean any() {
      return tables || textBoxes || headersOrFooters || drawings;
    }
  }
}
