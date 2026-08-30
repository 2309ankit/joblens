package com.ankit.joblens.workspace;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceCandidateProfileService {
  private final NamedParameterJdbcTemplate jdbc;

  public WorkspaceCandidateProfileService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long requireCandidateProfile(UUID workspaceId) {
    return jdbc
        .queryForList(
            load("sql/workspace/find-candidate-profile.sql"),
            Map.of("workspaceId", workspaceId),
            Long.class)
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceNotReadyException::new);
  }
}
