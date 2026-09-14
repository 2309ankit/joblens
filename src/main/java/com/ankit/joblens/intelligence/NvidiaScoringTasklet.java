package com.ankit.joblens.intelligence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class NvidiaScoringTasklet implements Tasklet {
  private static final Logger log = LoggerFactory.getLogger(NvidiaScoringTasklet.class);

  private final NvidiaScoringRepository repository;
  private final NebiusNvidiaScoringClient client;
  private final NvidiaRankingProperties properties;
  private final JobScoreCalculator scoreCalculator;
  private final ObjectMapper objectMapper;
  private final Long requestedCandidateProfileId;
  private final UUID workspaceId;

  public NvidiaScoringTasklet(
      NvidiaScoringRepository repository,
      NebiusNvidiaScoringClient client,
      NvidiaRankingProperties properties,
      JobScoreCalculator scoreCalculator,
      ObjectMapper objectMapper,
      Long requestedCandidateProfileId,
      String workspaceId) {
    this.repository = repository;
    this.client = client;
    this.properties = properties;
    this.scoreCalculator = scoreCalculator;
    this.objectMapper = objectMapper;
    this.requestedCandidateProfileId = requestedCandidateProfileId;
    this.workspaceId = workspaceId == null ? null : UUID.fromString(workspaceId);
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    if (!properties.enabled()) {
      return RepeatStatus.FINISHED;
    }
    properties.requireEnabledConfiguration();
    RoleRankingContext context = scoreCalculator.context(requestedCandidateProfileId);
    long candidateProfileId = context.candidate().id();
    String candidateFingerprint = fingerprint(candidateFacts(context));
    int remainingDailyCalls =
        properties.maxCallsPerDay() - repository.callsToday(properties.model());
    if (remainingDailyCalls <= 0) {
      log.warn("NVIDIA ranking daily call limit reached for model={}", properties.model());
      return RepeatStatus.FINISHED;
    }

    int calls = 0;
    for (NvidiaScoringCandidate candidate :
        repository.shortlist(candidateProfileId, workspaceId, properties.maxJobsPerRun())) {
      contribution.incrementReadCount();
      String cacheKey = cacheKey(candidate, candidateProfileId, candidateFingerprint);
      if (repository.hasCachedScore(cacheKey)) {
        continue;
      }
      if (calls >= remainingDailyCalls) {
        log.warn("NVIDIA ranking daily call limit reached for model={}", properties.model());
        break;
      }
      calls++;
      try {
        NvidiaScoreResult result = client.score(candidate, context);
        repository.recordSuccess(
            candidate, candidateProfileId, candidateFingerprint, cacheKey, properties, result);
        contribution.incrementWriteCount(1);
      } catch (RuntimeException failure) {
        String reason = sanitize(failure);
        repository.recordFailure(candidate, candidateProfileId, cacheKey, properties, reason);
        log.warn(
            "NVIDIA ranking failed for normalizedJobId={}; deterministic fallback remains active: {}",
            candidate.job().id(),
            reason);
      }
    }
    return RepeatStatus.FINISHED;
  }

  private String candidateFacts(RoleRankingContext context) {
    try {
      return objectMapper.writeValueAsString(context.candidate());
    } catch (JacksonException exception) {
      throw new IllegalStateException("Could not fingerprint candidate ranking facts", exception);
    }
  }

  private String cacheKey(
      NvidiaScoringCandidate candidate, long candidateProfileId, String candidateFingerprint) {
    return fingerprint(
        candidateProfileId
            + "|"
            + candidate.profileVersionId()
            + "|"
            + candidate.job().contentHash()
            + "|"
            + candidateFingerprint
            + "|"
            + properties.model()
            + "|"
            + properties.promptVersion());
  }

  private static String fingerprint(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String sanitize(Throwable failure) {
    String message = failure.getMessage();
    String reason =
        failure.getClass().getSimpleName()
            + ": "
            + (message == null ? "No failure message" : message);
    reason = reason.replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[REDACTED]");
    return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
  }
}
