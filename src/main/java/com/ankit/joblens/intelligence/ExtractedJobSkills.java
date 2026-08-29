package com.ankit.joblens.intelligence;
import java.util.List;
public record ExtractedJobSkills(long normalizedJobId, String contentHash, List<SkillMatch> matches) {
    public record SkillMatch(long skillId, String skillName, int mentionCount, String evidence) {}
}
