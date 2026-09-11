package com.ankit.joblens.intelligence;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobScoreCalculator {
  public static final String POLICY_VERSION = "universal-v2";
  private static final Set<String> GENERIC_TITLE_WORDS =
      Set.of(
          "senior",
          "junior",
          "lead",
          "principal",
          "staff",
          "manager",
          "engineer",
          "developer",
          "specialist",
          "executive",
          "officer",
          "associate");
  // Technology-agnostic filler words that make a target-role phrase LOOK distinctive but aren't —
  // distinct from GENERIC_TITLE_WORDS (seniority/role-noise). A target role of just "Software
  // Engineer" strips to no distinctive tokens, so it can never self-qualify a job as Recommended
  // on title alone (see BUG-M3-006: ".NET Software Engineer" phrase-matching "Software Engineer").
  private static final Set<String> BROAD_TITLE_WORDS =
      Set.of(
          "software",
          "application",
          "applications",
          "system",
          "systems",
          "solution",
          "solutions",
          "technology",
          "technical",
          "it");
  private final JdbcTemplate jdbc;
  private final CandidateProfileService profiles;
  private final RoleRankingRepository roleRanking;

  @Autowired
  public JobScoreCalculator(
      JdbcTemplate jdbc, CandidateProfileService profiles, RoleRankingRepository roleRanking) {
    this.jdbc = jdbc;
    this.profiles = profiles;
    this.roleRanking = roleRanking;
  }

  JobScoreCalculator(JdbcTemplate jdbc, CandidateProfileService profiles) {
    this(jdbc, profiles, null);
  }

  public RoleRankingContext context(Long candidateProfileId) {
    CandidateProfileConfig candidate =
        candidateProfileId == null ? profiles.loadDefault() : profiles.load(candidateProfileId);
    List<RoleRankingContext.TargetRole> roles =
        roleRanking == null ? List.of() : roleRanking.targetRoles(candidate.id());
    if (roles.isEmpty()) {
      int[] priority = {1};
      roles =
          candidate.roles().stream()
              .limit(3)
              .map(
                  role ->
                      new RoleRankingContext.TargetRole(null, role, priority[0]++, List.of(), null))
              .toList();
    }
    if (roles.isEmpty()) {
      roles =
          List.of(
              new RoleRankingContext.TargetRole(null, "General opportunity", 1, List.of(), null));
    }
    return new RoleRankingContext(candidate, roles);
  }

  public JobScore calculate(NormalizedJobView job) {
    return calculate(job, context(null));
  }

  public JobScore calculate(NormalizedJobView job, Long candidateProfileId) {
    return calculate(job, context(candidateProfileId));
  }

  public JobScore calculate(NormalizedJobView job, RoleRankingContext context) {
    Set<String> extractedJobSkills = extractedJobSkills(job.id());
    List<JobScore.RoleScore> roleScores =
        context.targetRoles().stream()
            .map(role -> scoreRole(job, context.candidate(), role, extractedJobSkills))
            .toList();
    JobScore.RoleScore best =
        roleScores.stream()
            .max(
                Comparator.comparingInt(JobScore.RoleScore::total)
                    .thenComparing(
                        Comparator.comparingInt(JobScore.RoleScore::rolePriority).reversed()))
            .orElseThrow();
    return new JobScore(
        job.id(),
        context.candidate().id(),
        best.total(),
        best.title() + best.skill(),
        best.sector(),
        best.seniority(),
        best.location(),
        best.employment(),
        best.salary(),
        best.freshness(),
        best.qualifiesRecommended(),
        best.reasons(),
        best,
        roleScores);
  }

  private JobScore.RoleScore scoreRole(
      NormalizedJobView job,
      CandidateProfileConfig candidate,
      RoleRankingContext.TargetRole role,
      Set<String> extractedJobSkills) {
    var reasons = new ArrayList<JobScore.Reason>();
    ScorePart title = titleScore(job.title(), role);
    reasons.add(new JobScore.Reason("ROLE_TITLE", title.points(), title.reason()));

    List<CandidateProfileConfig.CandidateSkill> candidateMatches =
        candidate.skills().values().stream()
            .filter(
                skill ->
                    extractedJobSkills.contains(skill.name().toLowerCase(Locale.ROOT))
                        || containsSkill(job, skill.name()))
            .toList();
    double totalImportance =
        candidate.skills().values().stream()
            .mapToDouble(CandidateProfileConfig.CandidateSkill::importance)
            .sum();
    int universalSkill =
        (int)
            Math.round(
                Math.min(
                    8,
                    16
                        * candidateMatches.stream()
                            .mapToDouble(CandidateProfileConfig.CandidateSkill::importance)
                            .sum()
                        / Math.max(1, totalImportance)));
    reasons.add(
        new JobScore.Reason(
            "CONFIRMED_SKILLS",
            universalSkill,
            candidateMatches.isEmpty()
                ? "No confirmed candidate skill matched"
                : "Matched confirmed skills: "
                    + candidateMatches.stream()
                        .map(CandidateProfileConfig.CandidateSkill::name)
                        .sorted()
                        .toList()));

    int calibratedSkill = 0;
    RoleRankingContext.CalibrationPack pack = role.calibrationPack();
    if (pack != null) {
      calibratedSkill += calibratedSkillPart(job, extractedJobSkills, pack, "CORE", 4, reasons);
      calibratedSkill +=
          calibratedSkillPart(job, extractedJobSkills, pack, "PREFERRED", 2, reasons);
      calibratedSkill +=
          calibratedSkillPart(job, extractedJobSkills, pack, "SUPPORTING", 1, reasons);
    }
    int skill = Math.min(15, universalSkill + calibratedSkill);

    String text = searchableText(job);
    long domainMatches = candidate.domains().stream().filter(text::contains).count();
    int sector = (int) Math.min(15, domainMatches * 3);
    reasons.add(
        new JobScore.Reason(
            "SECTOR",
            sector,
            sector == 0
                ? "No optional preferred-sector signal matched"
                : "Preferred-sector signals matched: " + domainMatches));

    ScorePart seniority = seniorityScore(job.title(), role.name());
    reasons.add(new JobScore.Reason("SENIORITY", seniority.points(), seniority.reason()));
    LocationResult location = locationScore(job, candidate);
    reasons.add(new JobScore.Reason("LOCATION", location.marketPoints(), location.marketReason()));
    reasons.add(
        new JobScore.Reason(
            "WORK_ARRANGEMENT", location.arrangementPoints(), location.arrangementReason()));
    ScorePart employment = employmentScore(job, candidate.preferences());
    reasons.add(new JobScore.Reason("EMPLOYMENT", employment.points(), employment.reason()));
    int salary =
        job.salaryMin() != null || job.salaryMax() != null
            ? 5
            : Integer.parseInt(candidate.preferences().getOrDefault("salary.missing.points", "3"));
    salary = Math.min(10, salary);
    reasons.add(
        new JobScore.Reason(
            "SALARY",
            salary,
            job.salaryMin() == null && job.salaryMax() == null
                ? "Salary unavailable"
                : "Salary provided"));
    int freshness = freshness(job.postedAt(), candidate);
    reasons.add(
        new JobScore.Reason(
            "FRESHNESS",
            freshness,
            job.postedAt() == null ? "Posted date unavailable" : "Posting freshness band"));
    int total =
        title.points()
            + skill
            + sector
            + seniority.points()
            + location.points()
            + employment.points()
            + salary
            + freshness;
    boolean titleQualifies = titleQualifiesForRecommendation(job.title(), role);
    boolean qualifies = qualifiesRecommended(skill, titleQualifies);
    return new JobScore.RoleScore(
        role.id(),
        role.name(),
        role.priority(),
        POLICY_VERSION,
        pack == null ? null : pack.code(),
        pack == null ? null : pack.version(),
        pack == null ? "Universal policy" : pack.name(),
        total,
        title.points(),
        skill,
        sector,
        seniority.points(),
        location.points(),
        employment.points(),
        salary,
        freshness,
        qualifies,
        List.copyOf(reasons));
  }

  private int calibratedSkillPart(
      NormalizedJobView job,
      Set<String> extractedJobSkills,
      RoleRankingContext.CalibrationPack pack,
      String level,
      int maximum,
      List<JobScore.Reason> reasons) {
    List<RoleRankingContext.SkillSignal> signals =
        pack.skillSignals().stream().filter(signal -> level.equals(signal.level())).toList();
    if (signals.isEmpty()) {
      return 0;
    }
    List<String> matched =
        signals.stream()
            .map(RoleRankingContext.SkillSignal::name)
            .filter(
                name ->
                    extractedJobSkills.contains(name.toLowerCase(Locale.ROOT))
                        || containsSkill(job, name))
            .toList();
    int points = (int) Math.round(maximum * matched.size() / (double) signals.size());
    reasons.add(
        new JobScore.Reason(
            "CALIBRATED_" + level + "_SKILLS",
            points,
            matched.isEmpty()
                ? "No " + level.toLowerCase(Locale.ROOT) + " signal matched for " + pack.name()
                : "Matched " + level.toLowerCase(Locale.ROOT) + " signals: " + matched));
    return points;
  }

  private ScorePart titleScore(String jobTitle, RoleRankingContext.TargetRole role) {
    if (containsPhrase(jobTitle, role.name())) {
      return new ScorePart(25, "Title matched target role: " + role.name());
    }
    for (String alias : role.aliases()) {
      if (containsPhrase(jobTitle, alias)) {
        return new ScorePart(23, "Title matched role alias: " + alias);
      }
    }
    if (role.calibrationPack() != null) {
      for (RoleRankingContext.TitleSignal signal : role.calibrationPack().titleSignals()) {
        if (containsPhrase(jobTitle, signal.text())) {
          int points = "PRIMARY".equals(signal.level()) ? 22 : 18;
          return new ScorePart(
              points,
              "Title matched "
                  + signal.level().toLowerCase(Locale.ROOT)
                  + " calibrated signal: "
                  + signal.text());
        }
      }
    }
    Set<String> targetTokens = meaningfulTitleTokens(role.name());
    Set<String> jobTokens = meaningfulTitleTokens(jobTitle);
    long overlap = targetTokens.stream().filter(jobTokens::contains).count();
    if (!targetTokens.isEmpty() && overlap == targetTokens.size()) {
      return new ScorePart(18, "All distinctive target-role words matched the job title");
    }
    if (!targetTokens.isEmpty() && overlap * 2 >= targetTokens.size()) {
      return new ScorePart(10, "Some distinctive target-role words matched the job title");
    }
    return new ScorePart(0, "Job title did not match this target role");
  }

  static boolean qualifiesRecommended(int skillScore, boolean titleQualifies) {
    return skillScore > 0 || titleQualifies;
  }

  static boolean titleQualifiesForRecommendation(
      String jobTitle, RoleRankingContext.TargetRole role) {
    for (String alias : role.aliases()) {
      if (containsPhrase(jobTitle, alias)) {
        return true;
      }
    }
    if (role.calibrationPack() != null) {
      for (RoleRankingContext.TitleSignal signal : role.calibrationPack().titleSignals()) {
        if (containsPhrase(jobTitle, signal.text())) {
          return true;
        }
      }
    }
    Set<String> distinctiveTargetTokens = distinctiveQualifyingTokens(role.name());
    if (distinctiveTargetTokens.isEmpty()) {
      return false;
    }
    return meaningfulTitleTokens(jobTitle).containsAll(distinctiveTargetTokens);
  }

  private static Set<String> distinctiveQualifyingTokens(String value) {
    String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
    var tokens = new LinkedHashSet<String>();
    for (String token : normalized.split("[^a-z0-9+#]+")) {
      if (token.length() >= 2
          && !GENERIC_TITLE_WORDS.contains(token)
          && !BROAD_TITLE_WORDS.contains(token)) {
        tokens.add(token);
      }
    }
    // Deliberately no fallback-refill (unlike meaningfulTitleTokens): an empty result here must
    // mean "title alone cannot qualify," not "use generic/broad words anyway."
    return tokens;
  }

  private ScorePart seniorityScore(String jobTitle, String targetRole) {
    int wanted = seniorityLevel(targetRole);
    int offered = seniorityLevel(jobTitle);
    if (wanted == 0 && offered == 0) {
      return new ScorePart(5, "No explicit seniority constraint");
    }
    if (wanted == 0) {
      return new ScorePart(5, "Target role accepts an unspecified seniority range");
    }
    if (offered == 0) {
      return new ScorePart(3, "Job title does not state seniority");
    }
    int difference = Math.abs(wanted - offered);
    return difference == 0
        ? new ScorePart(10, "Job and target seniority matched")
        : difference == 1
            ? new ScorePart(6, "Job seniority is adjacent to the target level")
            : new ScorePart(0, "Job seniority differs from the target level");
  }

  private LocationResult locationScore(NormalizedJobView job, CandidateProfileConfig candidate) {
    CandidateProfileConfig.LocationPreference matched =
        candidate.locationPreferences().stream()
            .filter(
                target ->
                    job.location() != null
                        && job.location()
                            .toLowerCase(Locale.ROOT)
                            .contains(target.location().toLowerCase(Locale.ROOT)))
            .findFirst()
            .orElse(null);
    int marketPoints = matched == null ? 0 : 5;
    String workPreference =
        candidate.preferences().getOrDefault("work.preference", "REMOTE,HYBRID,ONSITE");
    boolean workMatched =
        job.remoteType() != null
            && java.util.Arrays.stream(workPreference.split(","))
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(job.remoteType()));
    String marketReason =
        matched == null
            ? "Advertised location did not match a selected market"
            : "Matched preferred market: " + matched.location() + ", " + matched.countryCode();
    return new LocationResult(
        marketPoints,
        marketReason,
        workMatched ? 5 : 0,
        workMatched ? "Work arrangement matched" : "Work arrangement did not match");
  }

  private ScorePart employmentScore(NormalizedJobView job, Map<String, String> preferences) {
    String wanted = preferences.getOrDefault("employment.preference", "ANY");
    if ("ANY".equalsIgnoreCase(wanted)) {
      return new ScorePart(5, "Any employment type accepted");
    }
    if (job.employmentType() == null) {
      return new ScorePart(3, "Employment type unavailable");
    }
    return wanted.equalsIgnoreCase(job.employmentType())
        ? new ScorePart(10, "Employment type matched: " + wanted)
        : new ScorePart(0, "Employment type did not match: wanted " + wanted);
  }

  private int freshness(OffsetDateTime postedAt, CandidateProfileConfig candidate) {
    if (postedAt == null) {
      return 0;
    }
    long days = Math.max(0, ChronoUnit.DAYS.between(postedAt, OffsetDateTime.now()));
    return days
            <= Integer.parseInt(candidate.preferences().getOrDefault("freshness.days.full", "7"))
        ? 5
        : days
                <= Integer.parseInt(
                    candidate.preferences().getOrDefault("freshness.days.half", "30"))
            ? 2
            : 0;
  }

  private Set<String> extractedJobSkills(long jobId) {
    var names = new LinkedHashSet<String>();
    jdbc.query(
        "SELECT s.canonical_name FROM job_skill js JOIN skill s ON s.id=js.skill_id WHERE js.normalized_job_id=?",
        (org.springframework.jdbc.core.RowCallbackHandler)
            resultSet -> names.add(resultSet.getString(1).toLowerCase(Locale.ROOT)),
        jobId);
    return names;
  }

  private static int seniorityLevel(String value) {
    String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
    if (text.matches(".*\\b(principal|staff)\\b.*")) return 5;
    if (text.matches(".*\\blead\\b.*")) return 4;
    if (text.matches(".*\\bsenior\\b.*")) return 3;
    if (text.matches(".*\\b(junior|entry)\\b.*")) return 1;
    if (text.matches(".*\\b(intern|trainee)\\b.*")) return -1;
    return 0;
  }

  private static Set<String> meaningfulTitleTokens(String value) {
    String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
    var tokens = new LinkedHashSet<String>();
    for (String token : normalized.split("[^a-z0-9+#]+")) {
      if (token.length() >= 2 && !GENERIC_TITLE_WORDS.contains(token)) {
        tokens.add(token);
      }
    }
    if (tokens.isEmpty()) {
      for (String token : normalized.split("[^a-z0-9+#]+")) {
        if (token.length() >= 2) tokens.add(token);
      }
    }
    return tokens;
  }

  private static boolean containsPhrase(String text, String phrase) {
    if (text == null || phrase == null || phrase.isBlank()) {
      return false;
    }
    return Pattern.compile(
            "(?<![A-Za-z0-9+#])" + Pattern.quote(phrase.trim()) + "(?![A-Za-z0-9+#])",
            Pattern.CASE_INSENSITIVE)
        .matcher(text)
        .find();
  }

  private static boolean containsSkill(NormalizedJobView job, String skill) {
    return containsPhrase(
        (job.title() == null ? "" : job.title())
            + "\n"
            + (job.descriptionText() == null ? "" : job.descriptionText()),
        skill);
  }

  private static String searchableText(NormalizedJobView job) {
    return ((job.title() == null ? "" : job.title())
            + " "
            + (job.descriptionText() == null ? "" : job.descriptionText()))
        .toLowerCase(Locale.ROOT);
  }

  private record ScorePart(int points, String reason) {}

  private record LocationResult(
      int marketPoints, String marketReason, int arrangementPoints, String arrangementReason) {
    int points() {
      return marketPoints + arrangementPoints;
    }
  }
}
