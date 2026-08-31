package com.ankit.joblens.intelligence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SkillExtractor {
  private final SkillCatalogService catalog;
  private volatile IndexedCatalog indexedCatalog;

  public SkillExtractor(SkillCatalogService catalog) {
    this.catalog = catalog;
  }

  public ExtractedJobSkills extract(long id, String hash, String title, String description) {
    String text = (title == null ? "" : title) + "\n" + (description == null ? "" : description);
    SkillCatalogService.Snapshot snapshot = catalog.load();
    PhraseAutomaton<SkillCatalogService.SkillDefinition> index = index(snapshot);
    Map<Long, MutableMatch> found = new LinkedHashMap<>();
    for (PhraseAutomaton.Hit<SkillCatalogService.SkillDefinition> hit : index.find(text)) {
      if (!safeTerm(hit.phrase(), hit.surface())) {
        continue;
      }
      found.compute(
          hit.value().id(),
          (ignored, current) ->
              current == null
                  ? new MutableMatch(hit.value(), 1, hit.surface())
                  : new MutableMatch(current.skill(), current.count() + 1, current.evidence()));
    }
    var matches = new ArrayList<ExtractedJobSkills.SkillMatch>();
    found
        .values()
        .forEach(
            match ->
                matches.add(
                    new ExtractedJobSkills.SkillMatch(
                        match.skill().id(),
                        match.skill().name(),
                        match.count(),
                        match.evidence())));
    matches.sort(Comparator.comparing(ExtractedJobSkills.SkillMatch::skillName));
    return new ExtractedJobSkills(id, hash, matches);
  }

  private PhraseAutomaton<SkillCatalogService.SkillDefinition> index(
      SkillCatalogService.Snapshot snapshot) {
    IndexedCatalog current = indexedCatalog;
    if (current != null && current.signature().equals(snapshot.signature())) {
      return current.index();
    }
    synchronized (this) {
      current = indexedCatalog;
      if (current != null && current.signature().equals(snapshot.signature())) {
        return current.index();
      }
      var entries =
          snapshot.terms().stream()
              .map(term -> new PhraseAutomaton.Entry<>(term.skill(), term.phrase()))
              .toList();
      indexedCatalog = new IndexedCatalog(snapshot.signature(), new PhraseAutomaton<>(entries));
      return indexedCatalog.index();
    }
  }

  private static boolean safeTerm(String phrase, String surface) {
    if (phrase.length() == 1) {
      return false;
    }
    return phrase.length() > 2 || surface.equals(surface.toUpperCase(Locale.ROOT));
  }

  private record IndexedCatalog(
      String signature, PhraseAutomaton<SkillCatalogService.SkillDefinition> index) {}

  private record MutableMatch(
      SkillCatalogService.SkillDefinition skill, int count, String evidence) {}
}
