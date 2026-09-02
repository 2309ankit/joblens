package com.ankit.joblens.intelligence;

import static com.ankit.joblens.jdbc.ClasspathSql.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRankingRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public RoleRankingRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<RoleRankingContext.TargetRole> targetRoles(long candidateProfileId) {
    return jdbc.query(
        load("sql/intelligence/list-candidate-target-roles.sql"),
        Map.of("candidateProfileId", candidateProfileId),
        (resultSet, row) -> {
          long roleId = resultSet.getLong("role_id");
          Long packId = resultSet.getObject("pack_id", Long.class);
          List<String> aliases =
              jdbc.queryForList(
                  load("sql/intelligence/list-role-ranking-aliases.sql"),
                  Map.of("roleId", roleId),
                  String.class);
          RoleRankingContext.CalibrationPack pack =
              packId == null ? null : calibrationPack(packId, roleId, resultSet);
          return new RoleRankingContext.TargetRole(
              roleId,
              resultSet.getString("canonical_name"),
              resultSet.getInt("priority"),
              aliases,
              pack);
        });
  }

  private RoleRankingContext.CalibrationPack calibrationPack(
      long packId, long roleId, java.sql.ResultSet resultSet) throws java.sql.SQLException {
    var titleSignals = new ArrayList<RoleRankingContext.TitleSignal>();
    jdbc.query(
        load("sql/intelligence/list-calibrated-title-signals.sql"),
        Map.of("packId", packId, "roleId", roleId),
        (org.springframework.jdbc.core.RowCallbackHandler)
            row ->
                titleSignals.add(
                    new RoleRankingContext.TitleSignal(
                        row.getString("signal_text"), row.getString("signal_level"))));
    var skillSignals = new ArrayList<RoleRankingContext.SkillSignal>();
    jdbc.query(
        load("sql/intelligence/list-calibrated-skill-signals.sql"),
        Map.of("packId", packId, "roleId", roleId),
        (org.springframework.jdbc.core.RowCallbackHandler)
            row ->
                skillSignals.add(
                    new RoleRankingContext.SkillSignal(
                        row.getString("canonical_name"), row.getString("signal_level"))));
    return new RoleRankingContext.CalibrationPack(
        packId,
        resultSet.getString("pack_code"),
        resultSet.getString("pack_version"),
        resultSet.getString("pack_name"),
        List.copyOf(titleSignals),
        List.copyOf(skillSignals));
  }
}
