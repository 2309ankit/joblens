package com.ankit.joblens.searchprofile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchProfileProcessorTests {

    private final SearchProfileProcessor processor = new SearchProfileProcessor();

    @Test
    void normalizesValidProfile() throws Exception {
        SearchProfile result = processor.process(row(" SP001 ", " adzuna ", " java developer ",
                " permanent ", " true ", " java | spring boot | kafka "));

        assertThat(result).isEqualTo(new SearchProfile(
                "SP001", "ADZUNA", "sg", "java developer", "Singapore",
                "java|spring boot|kafka", "", "PERMANENT", true));
    }

    @Test
    void rejectsBlankProfileId() {
        assertThatThrownBy(() -> processor.process(row(" ", "ADZUNA", "java", "any", "true", "java")))
                .isInstanceOf(SearchProfileValidationException.class)
                .hasMessage("profile_id is required");
    }

    @Test
    void rejectsBlankKeywords() {
        assertThatThrownBy(() -> processor.process(row("SP001", "ADZUNA", " ", "any", "true", "java")))
                .isInstanceOf(SearchProfileValidationException.class)
                .hasMessage("keywords are required");
    }

    @Test
    void rejectsUnsupportedSource() {
        assertThatThrownBy(() -> processor.process(row("SP001", "OTHER", "java", "any", "true", "java")))
                .isInstanceOf(SearchProfileValidationException.class)
                .hasMessage("Unsupported source: OTHER");
    }

    @Test
    void rejectsUnsupportedEmploymentType() {
        assertThatThrownBy(() -> processor.process(row("SP001", "ADZUNA", "java", "freelance", "true", "java")))
                .isInstanceOf(SearchProfileValidationException.class)
                .hasMessage("Unsupported employment_type: FREELANCE");
    }

    @Test
    void normalizesAndDeduplicatesSkills() throws Exception {
        SearchProfile result = processor.process(row("SP001", "ADZUNA", "java", "any", "true",
                " Java | spring   boot | kafka | JAVA | "));

        assertThat(result.includeSkills()).isEqualTo("java|spring boot|kafka");
    }

    private static SearchProfileCsvRow row(
            String profileId, String source, String keywords, String employmentType, String active,
            String includeSkills) {
        return new SearchProfileCsvRow(2, "raw", profileId, source, "sg", keywords, "Singapore",
                includeSkills, "", employmentType, active);
    }
}
