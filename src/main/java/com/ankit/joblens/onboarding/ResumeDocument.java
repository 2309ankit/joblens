package com.ankit.joblens.onboarding;

import java.util.List;

record ResumeDocument(String text, List<Segment> segments) {
  Section sectionAt(int offset) {
    return segments.stream()
        .filter(segment -> offset >= segment.startOffset() && offset < segment.endOffset())
        .map(Segment::section)
        .findFirst()
        .orElse(Section.OTHER);
  }

  String evidenceAt(int offset) {
    Segment segment =
        segments.stream()
            .filter(
                candidate -> offset >= candidate.startOffset() && offset < candidate.endOffset())
            .findFirst()
            .orElse(new Segment(Section.OTHER, text, 0, text.length()));
    String evidence = segment.text().trim().replaceAll("\\s+", " ");
    return evidence.length() <= 300 ? evidence : evidence.substring(0, 297) + "...";
  }

  boolean beforeExperience(int offset) {
    return segments.stream()
        .filter(segment -> segment.section() == Section.EXPERIENCE)
        .mapToInt(Segment::startOffset)
        .min()
        .stream()
        .allMatch(experienceStart -> offset < experienceStart);
  }

  enum Section {
    CONTACT,
    SUMMARY,
    CORE_COMPETENCIES,
    SKILLS,
    TOOLS,
    DOMAIN_EXPERTISE,
    EXPERIENCE,
    EDUCATION,
    OTHER;

    boolean explicitlyListsSkills() {
      return this == CORE_COMPETENCIES || this == SKILLS || this == TOOLS;
    }
  }

  record Segment(Section section, String text, int startOffset, int endOffset) {}
}
