package com.ankit.joblens.searchprofile;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

public class SearchProfileJdbcWriter implements ItemWriter<SearchProfile> {

    private static final String UPSERT_SQL = """
            INSERT INTO search_profile (
                profile_id, source, source_key, keywords, location,
                include_skills, exclude_skills, employment_type, active
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (profile_id) DO UPDATE SET
                source = EXCLUDED.source,
                source_key = EXCLUDED.source_key,
                keywords = EXCLUDED.keywords,
                location = EXCLUDED.location,
                include_skills = EXCLUDED.include_skills,
                exclude_skills = EXCLUDED.exclude_skills,
                employment_type = EXCLUDED.employment_type,
                active = EXCLUDED.active,
                updated_at = CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchProfileJdbcWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(Chunk<? extends SearchProfile> chunk) {
        List<? extends SearchProfile> profiles = chunk.getItems();
        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                SearchProfile profile = profiles.get(index);
                statement.setString(1, profile.profileId());
                statement.setString(2, profile.source());
                statement.setString(3, profile.sourceKey());
                statement.setString(4, profile.keywords());
                statement.setString(5, profile.location());
                statement.setString(6, profile.includeSkills());
                statement.setString(7, profile.excludeSkills());
                statement.setString(8, profile.employmentType());
                statement.setBoolean(9, profile.active());
            }

            @Override
            public int getBatchSize() {
                return profiles.size();
            }
        });
    }
}
