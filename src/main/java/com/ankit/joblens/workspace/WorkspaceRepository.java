package com.ankit.joblens.workspace;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public WorkspaceRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean exists(UUID workspaceId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            load("sql/workspace/exists.sql"), Map.of("workspaceId", workspaceId), Boolean.class));
  }

  public void create(UUID workspaceId) {
    jdbc.update(load("sql/workspace/create.sql"), Map.of("workspaceId", workspaceId));
  }
}
