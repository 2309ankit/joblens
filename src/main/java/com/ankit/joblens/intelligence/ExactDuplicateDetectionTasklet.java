package com.ankit.joblens.intelligence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class ExactDuplicateDetectionTasklet implements Tasklet {

    private final DuplicateDetectionRepository repository;
    private final boolean failThisExecution;

    public ExactDuplicateDetectionTasklet(DuplicateDetectionRepository repository, boolean failThisExecution) {
        this.repository = repository;
        this.failThisExecution = failThisExecution;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<DuplicateJobView> jobs = repository.findJobs();

        Map<Long, Long> parent = new HashMap<>();
        Map<String, Long> firstBySourceIdentity = new HashMap<>();
        Map<String, Long> firstByContentHash = new HashMap<>();
        for (DuplicateJobView job : jobs) {
            parent.put(job.id(), job.id());
            unionWithFirst(parent, firstBySourceIdentity, job.sourceIdentity(), job.id());
            unionWithFirst(parent, firstByContentHash, job.normalizedContentHash(), job.id());
        }

        Map<Long, List<DuplicateJobView>> components = new LinkedHashMap<>();
        for (DuplicateJobView job : jobs) {
            components.computeIfAbsent(find(parent, job.id()), ignored -> new ArrayList<>()).add(job);
        }
        List<List<DuplicateJobView>> duplicateGroups = components.values().stream()
                .filter(group -> group.size() > 1)
                .peek(group -> group.sort(Comparator.comparingLong(DuplicateJobView::id)))
                .sorted(Comparator.comparingLong(group -> group.getFirst().id()))
                .toList();

        List<String> desiredKeys = duplicateGroups.stream().map(this::clusterKey).toList();
        deleteObsoleteClusters(desiredKeys);
        for (List<DuplicateJobView> group : duplicateGroups) {
            reconcileCluster(group);
        }

        jobs.forEach(ignored -> contribution.incrementReadCount());
        contribution.incrementWriteCount(duplicateGroups.stream().mapToLong(List::size).sum());
        if (failThisExecution) {
            throw new InjectedDuplicateDetectionFailureException();
        }
        return RepeatStatus.FINISHED;
    }

    private void reconcileCluster(List<DuplicateJobView> group) {
        long canonicalJobId = group.getFirst().id();
        String key = clusterKey(group);
        long clusterId = repository.upsertCluster(key, canonicalJobId, group.size());

        List<Long> memberIds = group.stream().map(DuplicateJobView::id).toList();
        repository.deleteObsoleteMembers(clusterId, memberIds);
        for (DuplicateJobView job : group) {
            repository.upsertMember(clusterId, job.id(), job.id() == canonicalJobId);
        }

        List<DuplicateEvidence> desiredEvidence = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < group.size(); leftIndex++) {
            DuplicateJobView left = group.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < group.size(); rightIndex++) {
                DuplicateJobView right = group.get(rightIndex);
                if (left.sourceIdentity().equals(right.sourceIdentity())) {
                    desiredEvidence.add(new DuplicateEvidence(
                            left.id(), right.id(), "SOURCE_EXTERNAL_ID", left.sourceIdentity()));
                }
                if (left.normalizedContentHash().equals(right.normalizedContentHash())) {
                    desiredEvidence.add(new DuplicateEvidence(
                            left.id(), right.id(), "NORMALIZED_CONTENT_HASH", left.normalizedContentHash()));
                }
            }
        }
        reconcileEvidence(clusterId, desiredEvidence);
    }

    private void reconcileEvidence(long clusterId, List<DuplicateEvidence> desiredEvidence) {
        List<DuplicateEvidence> existingEvidence = repository.findEvidence(clusterId);
        for (DuplicateEvidence evidence : existingEvidence) {
            if (!desiredEvidence.contains(evidence)) {
                repository.deleteEvidence(clusterId, evidence);
            }
        }
        for (DuplicateEvidence evidence : desiredEvidence) {
            repository.insertEvidence(clusterId, evidence);
        }
    }

    private void deleteObsoleteClusters(List<String> desiredKeys) {
        repository.deleteObsoleteClusters(desiredKeys);
    }

    private String clusterKey(List<DuplicateJobView> group) {
        return "exact:v1:" + group.getFirst().id();
    }

    private static void unionWithFirst(Map<Long, Long> parent, Map<String, Long> firstByValue,
            String value, long jobId) {
        Long firstId = firstByValue.putIfAbsent(value, jobId);
        if (firstId != null) {
            union(parent, firstId, jobId);
        }
    }

    private static void union(Map<Long, Long> parent, long left, long right) {
        long leftRoot = find(parent, left);
        long rightRoot = find(parent, right);
        if (leftRoot != rightRoot) {
            parent.put(Math.max(leftRoot, rightRoot), Math.min(leftRoot, rightRoot));
        }
    }

    private static long find(Map<Long, Long> parent, long id) {
        long root = id;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }
        long current = id;
        while (current != root) {
            long next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

}
