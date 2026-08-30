package com.ankit.joblens.intelligence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SkillExtractor {
  private final SkillCatalogService catalog;

  public SkillExtractor(SkillCatalogService catalog) {
    this.catalog = catalog;
  }

  public ExtractedJobSkills extract(long id, String hash, String title, String description) {
    String text = ((title == null ? "" : title) + "\n" + (description == null ? "" : description));
    Map<Long, ExtractedJobSkills.SkillMatch> found = new LinkedHashMap<>();
    for (var entry : catalog.load().entrySet()) {
      String phrase = entry.getKey();
      Pattern pattern =
          Pattern.compile(
              "(?<![A-Za-z0-9+#])" + Pattern.quote(phrase) + "(?![A-Za-z0-9+#])",
              Pattern.CASE_INSENSITIVE);
      var matcher = pattern.matcher(text);
      int count = 0;
      while (matcher.find()) count++;
      if (count > 0)
        found.put(
            entry.getValue().id(),
            new ExtractedJobSkills.SkillMatch(
                entry.getValue().id(),
                entry.getValue().name(),
                count,
                matcherEvidence(text, pattern)));
    }
    var matches = new ArrayList<>(found.values());
    matches.sort(Comparator.comparing(ExtractedJobSkills.SkillMatch::skillName));
    return new ExtractedJobSkills(id, hash, matches);
  }

  private static String matcherEvidence(String text, Pattern pattern) {
    var m = pattern.matcher(text);
    return m.find() ? m.group() : "";
  }
}
