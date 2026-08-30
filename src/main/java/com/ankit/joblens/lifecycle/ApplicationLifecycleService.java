package com.ankit.joblens.lifecycle;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationLifecycleService {

  private final ApplicationLifecycleRepository repository;
  private final ApplicationLifecyclePolicy policy;

  public ApplicationLifecycleService(
      ApplicationLifecycleRepository repository, ApplicationLifecyclePolicy policy) {
    this.repository = repository;
    this.policy = policy;
  }

  @Transactional
  public long create(long normalizedJobId, LocalDate effectiveDate, String note) {
    return create(
        normalizedJobId, repository.findDefaultCandidateId(), effectiveDate, normalizeNote(note));
  }

  @Transactional
  public long create(
      long normalizedJobId, long candidateProfileId, LocalDate effectiveDate, String note) {
    if (!repository.jobExists(normalizedJobId)) {
      throw new LifecycleNotFoundException("Normalized job " + normalizedJobId + " was not found");
    }
    long applicationId =
        repository.createApplication(
            normalizedJobId, candidateProfileId, effectiveDate, normalizeNote(note));
    repository.insertHistory(
        applicationId, null, ApplicationStatus.SAVED, effectiveDate, normalizeNote(note));
    return applicationId;
  }

  @Transactional
  public long create(
      long normalizedJobId,
      long candidateProfileId,
      UUID workspaceId,
      LocalDate effectiveDate,
      String note) {
    if (!repository.jobExists(normalizedJobId, workspaceId)) {
      throw new LifecycleNotFoundException("Normalized job " + normalizedJobId + " was not found");
    }
    long applicationId =
        repository.createApplication(
            normalizedJobId, candidateProfileId, effectiveDate, normalizeNote(note));
    repository.insertHistory(
        applicationId, null, ApplicationStatus.SAVED, effectiveDate, normalizeNote(note));
    return applicationId;
  }

  @Transactional
  public void transition(
      long applicationId, ApplicationStatus target, LocalDate effectiveDate, String note) {
    transition(applicationId, null, target, effectiveDate, note);
  }

  @Transactional
  public void transition(
      long applicationId,
      Long candidateProfileId,
      ApplicationStatus target,
      LocalDate effectiveDate,
      String note) {
    ApplicationRecord application = repository.findApplicationForUpdate(applicationId);
    if (application == null) {
      throw new LifecycleNotFoundException("Application " + applicationId + " was not found");
    }
    requireOwner(
        application.candidateProfileId(), candidateProfileId, "Application", applicationId);
    if (effectiveDate.isBefore(application.statusEffectiveDate())) {
      throw new LifecycleValidationException(
          "Transition date cannot be before the current status effective date");
    }
    if (!policy.canTransition(application.status(), target)) {
      throw new LifecycleConflictException(
          "Transition from " + application.status() + " to " + target + " is not allowed");
    }
    repository.updateStatus(applicationId, target, effectiveDate, normalizeNote(note));
    repository.insertHistory(
        applicationId, application.status(), target, effectiveDate, normalizeNote(note));
  }

  @Transactional
  public void completeFollowUp(long followUpId, LocalDate completedOn) {
    completeFollowUp(followUpId, null, completedOn);
  }

  @Transactional
  public void completeFollowUp(long followUpId, Long candidateProfileId, LocalDate completedOn) {
    FollowUpRecord followUp = repository.findFollowUpForUpdate(followUpId);
    if (followUp == null) {
      throw new LifecycleNotFoundException("Follow-up " + followUpId + " was not found");
    }
    requireOwner(followUp.candidateProfileId(), candidateProfileId, "Follow-up", followUpId);
    if (!"OPEN".equals(followUp.status())) {
      throw new LifecycleConflictException("Only an OPEN follow-up can be completed");
    }
    repository.completeFollowUp(followUpId, completedOn);
  }

  private static void requireOwner(
      long actualCandidateProfileId, Long expectedCandidateProfileId, String type, long id) {
    if (expectedCandidateProfileId != null
        && actualCandidateProfileId != expectedCandidateProfileId) {
      throw new LifecycleNotFoundException(type + " " + id + " was not found");
    }
  }

  private static String normalizeNote(String note) {
    return note == null || note.isBlank() ? null : note.trim();
  }
}
