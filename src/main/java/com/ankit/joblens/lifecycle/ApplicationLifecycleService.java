package com.ankit.joblens.lifecycle;

import java.time.LocalDate;
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
    if (!repository.jobExists(normalizedJobId)) {
      throw new LifecycleNotFoundException("Normalized job " + normalizedJobId + " was not found");
    }
    long candidateProfileId = repository.findDefaultCandidateId();
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
    ApplicationRecord application = repository.findApplicationForUpdate(applicationId);
    if (application == null) {
      throw new LifecycleNotFoundException("Application " + applicationId + " was not found");
    }
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
    FollowUpRecord followUp = repository.findFollowUpForUpdate(followUpId);
    if (followUp == null) {
      throw new LifecycleNotFoundException("Follow-up " + followUpId + " was not found");
    }
    if (!"OPEN".equals(followUp.status())) {
      throw new LifecycleConflictException("Only an OPEN follow-up can be completed");
    }
    repository.completeFollowUp(followUpId, completedOn);
  }

  private static String normalizeNote(String note) {
    return note == null || note.isBlank() ? null : note.trim();
  }
}
