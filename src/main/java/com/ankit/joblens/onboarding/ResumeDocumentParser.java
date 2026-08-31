package com.ankit.joblens.onboarding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class ResumeDocumentParser {
  private static final Pattern UPPERCASE_HEADING =
      Pattern.compile(
          "(?<![A-Z])(?:PROFESSIONAL SUMMARY|CAREER SUMMARY|SUMMARY|CORE COMPETENCIES|CORE SKILLS|TECHNICAL SKILLS|SKILLS|TOOLS & ECOSYSTEMS|TOOLS|DOMAIN EXPERTISE|PROFESSIONAL EXPERIENCE|WORK EXPERIENCE|EMPLOYMENT EXPERIENCE|EXPERIENCE|EDUCATION)(?![A-Z])");
  private static final Pattern STANDALONE_HEADING =
      Pattern.compile(
          "(?i)^(professional summary|career summary|summary|core competencies|core skills|technical skills|skills|tools & ecosystems|tools|domain expertise|professional experience|work experience|employment experience|experience|education):?$");

  ResumeDocument parse(String rawText) {
    String prepared =
        rawText
            .replace('\r', '\n')
            .replace('\u2022', '\n')
            .replace('\u25cf', '\n')
            .replace('\u25aa', '\n');
    Matcher matcher = UPPERCASE_HEADING.matcher(prepared);
    StringBuilder split = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(
          split, Matcher.quoteReplacement("\n" + matcher.group().trim() + "\n"));
    }
    matcher.appendTail(split);

    ResumeDocument.Section current = ResumeDocument.Section.CONTACT;
    StringBuilder normalized = new StringBuilder();
    List<ResumeDocument.Segment> segments = new ArrayList<>();
    for (String rawLine : split.toString().split("\\n+")) {
      String line = rawLine.trim().replaceAll("\\s+", " ");
      if (line.isBlank()) {
        continue;
      }
      Matcher heading = STANDALONE_HEADING.matcher(line);
      if (heading.matches()) {
        current = section(heading.group(1));
        continue;
      }
      if (!normalized.isEmpty()) {
        normalized.append('\n');
      }
      int start = normalized.length();
      normalized.append(line);
      segments.add(new ResumeDocument.Segment(current, line, start, normalized.length()));
    }
    return new ResumeDocument(normalized.toString(), List.copyOf(segments));
  }

  private static ResumeDocument.Section section(String heading) {
    return switch (heading.toLowerCase(Locale.ROOT)) {
      case "professional summary", "career summary", "summary" -> ResumeDocument.Section.SUMMARY;
      case "core competencies" -> ResumeDocument.Section.CORE_COMPETENCIES;
      case "core skills", "technical skills", "skills" -> ResumeDocument.Section.SKILLS;
      case "tools & ecosystems", "tools" -> ResumeDocument.Section.TOOLS;
      case "domain expertise" -> ResumeDocument.Section.DOMAIN_EXPERTISE;
      case "professional experience", "work experience", "employment experience", "experience" ->
          ResumeDocument.Section.EXPERIENCE;
      case "education" -> ResumeDocument.Section.EDUCATION;
      default -> ResumeDocument.Section.OTHER;
    };
  }
}
