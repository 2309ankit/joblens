package com.ankit.joblens.onboarding;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;
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
  private final Tika tika = new Tika();

  public OnboardingService(OnboardingRepository repository) {
    this.repository = repository;
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
    String lower = text.toLowerCase(Locale.ROOT);
    var skills =
        repository.skillCatalog().stream()
            .filter(skill -> lower.contains(skill.toLowerCase(Locale.ROOT)))
            .toList();
    if (skills.isEmpty()) {
      throw new IllegalArgumentException("No known skills were found; review the resume format");
    }
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
    repository.addSkills(profileVersionId, skills);
    return repository.latestProfile(workspaceId).orElseThrow();
  }

  public java.util.Optional<OnboardingProfile> latest(UUID workspaceId) {
    return repository.latestProfile(workspaceId);
  }

  @Transactional
  public void savePreferences(UUID workspaceId, SearchPreferences preferences) {
    OnboardingProfile profile =
        repository
            .latestProfile(workspaceId)
            .orElseThrow(() -> new IllegalStateException("Upload a valid resume first"));
    if (!"DRAFT".equals(profile.status())) {
      throw new IllegalStateException("Upload a new resume before changing an active profile");
    }
    repository.savePreferences(workspaceId, profile.id(), preferences);
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
}
