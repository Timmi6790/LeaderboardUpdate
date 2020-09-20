package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.bedrock;

import de.timmi6790.commons.builders.MapBuilder;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.LeaderboardData;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class LeaderboardDataBedrock extends LeaderboardData {
    public LeaderboardDataBedrock(final int databaseId, final String websiteName) {
        super(databaseId, websiteName);
    }

    @Override
    public Map<String, Object> getNewWebLeaderboardParameters() {
        return MapBuilder.<String, Object>ofHashMap(1)
                .put("game", this.getWebsiteName())
                .build();
    }

    @Override
    public String getLogInfo() {
        return this.getWebsiteName();
    }

    public static class DatabaseMapper implements RowMapper<LeaderboardDataBedrock> {
        @Override
        public LeaderboardDataBedrock map(final ResultSet rs, final StatementContext ctx) throws SQLException {
            return new LeaderboardDataBedrock(
                    rs.getInt("id"),
                    rs.getString("game_name")
            );
        }
    }
}
