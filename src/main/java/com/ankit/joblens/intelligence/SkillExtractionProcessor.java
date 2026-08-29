package com.ankit.joblens.intelligence;
import org.springframework.batch.infrastructure.item.ItemProcessor;
public class SkillExtractionProcessor implements ItemProcessor<NormalizedJobView, ExtractedJobSkills> {
    private final SkillExtractor extractor;
    public SkillExtractionProcessor(SkillExtractor extractor) { this.extractor=extractor; }
    public ExtractedJobSkills process(NormalizedJobView item) { return extractor.extract(item.id(),item.contentHash(),item.title(),item.descriptionText()); }
}
