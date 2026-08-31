package com.ankit.joblens.onboarding;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OnboardingService {
  private static final long MAX_RESUME_SIZE = 5 * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES =
      Set.of(
          "application/pdf",
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/x-tika-ooxml");

  private final OnboardingRepository repository;
  private final ProfileIntelligenceRepository intelligenceRepository;
  private final ProfileIntelligenceExtractor intelligenceExtractor;
  private final ProviderCountryCatalog countryCatalog;
  private final Tika tika = new Tika();

  @Autowired
  public OnboardingService(
      OnboardingRepository repository,
      ProfileIntelligenceRepository intelligenceRepository,
      ProfileIntelligenceExtractor intelligenceExtractor,
      ProviderCountryCatalog countryCatalog) {
    this.repository = repository;
    this.intelligenceRepository = intelligenceRepository;
    this.intelligenceExtractor = intelligenceExtractor;
    this.countryCatalog = countryCatalog;
  }

  @Transactional
  public OnboardingProfile upload(UUID workspaceId, MultipartFile file) throws Exception {
    byte[] content = validateFile(file);
    String detectedType = tika.detect(content, file.getOriginalFilename());
    if (!ALLOWED_TYPES.contains(detectedType)) {
      throw new IllegalArgumentException("Resume must be a PDF, DOC, or DOCX file");
    }
    String text = tika.parseToString(new ByteArrayInputStream(content)).trim();
    if (text.length() < 80) {
      throw new IllegalArgumentException("Resume has too little readable text");
    }
    ResumeTextValidator.validate(text);
    ProfileIntelligenceExtractor.Extraction extraction =
        intelligenceExtractor.extract(
            text,
            intelligenceRepository.skillDefinitions(workspaceId),
            intelligenceRepository.roleDefinitions(workspaceId));
    long resumeId =
        repository.saveResume(
            workspaceId,
            safeFilename(file.getOriginalFilename()),
            detectedType,
            content.length,
            hash(content));
    String summary =
        text.lines().map(String::trim).filter(line -> !line.isBlank()).findFirst().orElse("Resume");
    long profileVersionId = repository.createDraft(workspaceId, resumeId, summary);
    repository.addSkills(
        profileVersionId,
        extraction.skills().stream()
            .map(ProfileIntelligenceExtractor.DetectedSkill::name)
            .toList());
    intelligenceRepository.saveSuggestions(profileVersionId, extraction);
    return repository.latestProfile(workspaceId).orElseThrow();
  }

  public java.util.Optional<OnboardingProfile> latest(UUID workspaceId) {
    return repository.latestProfile(workspaceId);
  }

  public java.util.Optional<SearchPreferences> preferences(UUID workspaceId) {
    return repository.preferences(workspaceId);
  }

  public List<SkillOption> skillOptions(UUID workspaceId, String query) {
    return intelligenceRepository.skillOptions(workspaceId, query);
  }

  public List<RoleOption> roleOptions(UUID workspaceId, String query) {
    return intelligenceRepository.roleOptions(workspaceId, query);
  }

  public List<IntegratedCountry> countries() {
    return countryCatalog.countries();
  }

  public ProfileIntelligence intelligence(UUID workspaceId) {
    OnboardingProfile profile =
        repository
            .latestProfile(workspaceId)
            .orElseThrow(() -> new IllegalStateException("Upload a valid resume first"));
    return intelligenceRepository.intelligence(workspaceId, profile.id());
  }

  @Transactional
  public OnboardingProfile updateSkills(UUID workspaceId, List<String> requestedSkills) {
    OnboardingProfile profile =
        repository
            .latestProfile(workspaceId)
            .orElseThrow(() -> new IllegalStateException("Upload a valid resume first"));
    List<String> skills =
        intelligenceRepository.resolveOrCreateSkills(workspaceId, requestedSkills);
    if (skills.isEmpty()) {
      throw new IllegalArgumentException("Select at least one skill");
    }
    if ("ACTIVE".equals(profile.status())) {
      profile = repository.forkDraft(workspaceId, profile);
    } else if (!"DRAFT".equals(profile.status())) {
      throw new IllegalStateException("Upload a resume before changing this profile");
    }
    repository.replaceDraftSkills(workspaceId, profile.id(), skills);
    return repository.latestProfile(workspaceId).orElseThrow();
  }

  @Transactional
  public void savePreferences(UUID workspaceId, SearchPreferences preferences) {
    validatePreferences(preferences);
    OnboardingProfile profile =
        repository
            .latestProfile(workspaceId)
            .orElseThrow(() -> new IllegalStateException("Upload a valid resume first"));
    if ("ACTIVE".equals(profile.status())) {
      profile = repository.forkDraft(workspaceId, profile);
    } else if (!"DRAFT".equals(profile.status())) {
      throw new IllegalStateException("Upload a resume before changing this profile");
    }
    repository.savePreferences(workspaceId, profile.id(), preferences);
  }

  @Transactional
  public long completeSetup(
      UUID workspaceId, List<String> requestedSkills, SearchPreferences preferences) {
    validatePreferences(preferences);
    List<String> skills =
        intelligenceRepository.resolveOrCreateSkills(workspaceId, requestedSkills);
    if (skills.isEmpty()) {
      throw new IllegalArgumentException("Select at least one skill");
    }
    OnboardingProfile profile =
        repository
            .latestProfile(workspaceId)
            .orElseThrow(() -> new IllegalStateException("Upload a valid resume first"));
    if ("ACTIVE".equals(profile.status())) {
      profile = repository.forkDraft(workspaceId, profile);
    } else if (!"DRAFT".equals(profile.status())) {
      throw new IllegalStateException("Upload a resume before changing this profile");
    }
    repository.replaceDraftSkills(workspaceId, profile.id(), skills);
    repository.savePreferences(workspaceId, profile.id(), preferences);
    OnboardingProfile completed = repository.latestProfile(workspaceId).orElseThrow();
    return repository.confirm(workspaceId, completed);
  }

  @Transactional
  public long confirm(UUID workspaceId) {
    OnboardingProfile profile =
        repository
            .latestProfile(workspaceId)
            .orElseThrow(() -> new IllegalStateException("Upload a valid resume first"));
    if (profile.targetRoles().isEmpty() || profile.targetDomains().isEmpty()) {
      throw new IllegalStateException("Save job preferences before confirming the profile");
    }
    return repository.confirm(workspaceId, profile);
  }

  private static byte[] validateFile(MultipartFile file) throws Exception {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Choose a resume file");
    }
    if (file.getSize() > MAX_RESUME_SIZE) {
      throw new IllegalArgumentException("Resume must be 5 MB or smaller");
    }
    return file.getBytes();
  }

  private static String safeFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      return "resume";
    }
    return java.nio.file.Path.of(filename).getFileName().toString();
  }

  private static String hash(byte[] content) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
  }

  private void validatePreferences(SearchPreferences preferences) {
    if (preferences == null
        || preferences.targetRoles().isBlank()
        || preferences.targetDomains().isBlank()
        || preferences.primaryLocation().isBlank()
        || preferences.keywords().isBlank()
        || preferences.employmentPreference().isBlank()
        || preferences.workPreference().isBlank()) {
      throw new IllegalArgumentException("Complete all required preferences");
    }
    if (preferences.maxPages() < 1 || preferences.maxPages() > 20) {
      throw new IllegalArgumentException("Maximum pages must be between 1 and 20");
    }
    preferences.targets().forEach(target -> countryCatalog.requireSupported(target.countryCode()));
  }
}
