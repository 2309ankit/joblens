package com.ankit.joblens.lifecycle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecyclePolicy {

  private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = transitions();
  private static final Map<ApplicationStatus, FollowUpPlan> FOLLOW_UPS =
      Map.of(
          ApplicationStatus.APPLIED, new FollowUpPlan(FollowUpType.APPLICATION_CHECK_IN, 7),
          ApplicationStatus.SCREENING, new FollowUpPlan(FollowUpType.RECRUITER_CHECK_IN, 5),
          ApplicationStatus.INTERVIEW, new FollowUpPlan(FollowUpType.INTERVIEW_THANK_YOU, 1),
          ApplicationStatus.OFFER, new FollowUpPlan(FollowUpType.OFFER_DECISION, 3));

  public boolean canTransition(ApplicationStatus from, ApplicationStatus to) {
    return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }

  public Set<ApplicationStatus> allowedTransitions(ApplicationStatus from) {
    return TRANSITIONS.getOrDefault(from, Set.of());
  }

  public Optional<FollowUpPlan> followUpFor(ApplicationStatus status) {
    return Optional.ofNullable(FOLLOW_UPS.get(status));
  }

  private static Map<ApplicationStatus, Set<ApplicationStatus>> transitions() {
    Map<ApplicationStatus, Set<ApplicationStatus>> transitions =
        new EnumMap<>(ApplicationStatus.class);
    transitions.put(
        ApplicationStatus.SAVED,
        EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN));
    transitions.put(
        ApplicationStatus.APPLIED,
        EnumSet.of(
            ApplicationStatus.SCREENING,
            ApplicationStatus.INTERVIEW,
            ApplicationStatus.OFFER,
            ApplicationStatus.REJECTED,
            ApplicationStatus.WITHDRAWN));
    transitions.put(
        ApplicationStatus.SCREENING,
        EnumSet.of(
            ApplicationStatus.INTERVIEW,
            ApplicationStatus.OFFER,
            ApplicationStatus.REJECTED,
            ApplicationStatus.WITHDRAWN));
    transitions.put(
        ApplicationStatus.INTERVIEW,
        EnumSet.of(
            ApplicationStatus.OFFER, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));
    transitions.put(
        ApplicationStatus.OFFER,
        EnumSet.of(
            ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));
    return Map.copyOf(transitions);
  }
}
