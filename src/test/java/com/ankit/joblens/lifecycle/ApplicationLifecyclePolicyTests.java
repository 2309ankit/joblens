package com.ankit.joblens.lifecycle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationLifecyclePolicyTests {

    private final ApplicationLifecyclePolicy policy = new ApplicationLifecyclePolicy();

    @Test
    void permitsForwardProgressAndTerminalExits() {
        assertThat(policy.canTransition(ApplicationStatus.SAVED, ApplicationStatus.APPLIED)).isTrue();
        assertThat(policy.canTransition(ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW)).isTrue();
        assertThat(policy.canTransition(ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER)).isTrue();
        assertThat(policy.canTransition(ApplicationStatus.OFFER, ApplicationStatus.ACCEPTED)).isTrue();
        assertThat(policy.canTransition(ApplicationStatus.SCREENING, ApplicationStatus.REJECTED)).isTrue();
    }

    @Test
    void rejectsBackwardSameAndTerminalTransitions() {
        assertThat(policy.canTransition(ApplicationStatus.APPLIED, ApplicationStatus.SAVED)).isFalse();
        assertThat(policy.canTransition(ApplicationStatus.APPLIED, ApplicationStatus.APPLIED)).isFalse();
        assertThat(policy.canTransition(ApplicationStatus.ACCEPTED, ApplicationStatus.WITHDRAWN)).isFalse();
        assertThat(policy.canTransition(ApplicationStatus.REJECTED, ApplicationStatus.APPLIED)).isFalse();
    }

    @Test
    void mapsOnlyActionableStatusesToDeterministicFollowUps() {
        assertThat(policy.followUpFor(ApplicationStatus.APPLIED).orElseThrow())
                .isEqualTo(new FollowUpPlan(FollowUpType.APPLICATION_CHECK_IN, 7));
        assertThat(policy.followUpFor(ApplicationStatus.SCREENING).orElseThrow())
                .isEqualTo(new FollowUpPlan(FollowUpType.RECRUITER_CHECK_IN, 5));
        assertThat(policy.followUpFor(ApplicationStatus.INTERVIEW).orElseThrow())
                .isEqualTo(new FollowUpPlan(FollowUpType.INTERVIEW_THANK_YOU, 1));
        assertThat(policy.followUpFor(ApplicationStatus.OFFER).orElseThrow())
                .isEqualTo(new FollowUpPlan(FollowUpType.OFFER_DECISION, 3));
        assertThat(policy.followUpFor(ApplicationStatus.ACCEPTED)).isEmpty();
    }
}
