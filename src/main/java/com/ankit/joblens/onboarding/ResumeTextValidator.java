package com.ankit.joblens.onboarding;

import java.util.List;
import java.util.regex.Pattern;

final class ResumeTextValidator {
  private static final Pattern CONTACT_DETAILS =
      Pattern.compile(
          "(?i)([\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|linkedin\\.com/in/|(?:\\+?\\d[\\d ()-]{7,}\\d))");
  private static final Pattern RESUME_TITLE =
      Pattern.compile("(?im)^\\s*(resume|curriculum vitae|cv)\\s*$");
  private static final List<Pattern> RESUME_SECTIONS =
      List.of(
          Pattern.compile("(?im)^\\s*(professional |work |employment )?experience\\s*:?\\s*$"),
          Pattern.compile("(?im)^\\s*(education|academic background|qualifications)\\s*:?\\s*$"),
          Pattern.compile("(?im)^\\s*(technical )?skills( and technologies)?\\s*:?\\s*$"),
          Pattern.compile(
              "(?im)^\\s*(professional |career )?(summary|profile|objective)\\s*:?\\s*$"),
          Pattern.compile("(?im)^\\s*(projects|certifications|achievements)\\s*:?\\s*$"));
  private static final List<Pattern> REQUIREMENT_SIGNALS =
      List.of(
          Pattern.compile("(?i)\\bjob description\\b"),
          Pattern.compile(
              "(?i)\\b(interview|screening) (requirements?|questions?|instructions?)\\b"),
          Pattern.compile("(?i)\\b(key )?responsibilities\\b"),
          Pattern.compile("(?i)\\b(candidate|applicant) (must|should|will)\\b"),
          Pattern.compile("(?i)\\bwe are (looking|hiring|seeking)\\b"));

  private ResumeTextValidator() {}

  static void validate(String text) {
    boolean hasContactDetails = CONTACT_DETAILS.matcher(text).find();
    boolean hasResumeTitle = RESUME_TITLE.matcher(text).find();
    long sectionCount =
        RESUME_SECTIONS.stream().filter(pattern -> pattern.matcher(text).find()).count();
    long requirementCount =
        REQUIREMENT_SIGNALS.stream().filter(pattern -> pattern.matcher(text).find()).count();

    boolean hasResumeStructure = sectionCount >= 2 && (hasContactDetails || hasResumeTitle);
    if (!hasResumeStructure || (requirementCount >= 2 && !hasResumeTitle)) {
      throw new IllegalArgumentException(
          "This looks like a job or interview document, not a resume. Upload a resume containing contact details and sections such as Experience, Education, and Skills.");
    }
  }
}
