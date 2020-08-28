package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.bedrock;

import de.timmi6790.mineplexleaderboardupdate.leaderboard.Leaderboard;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class LeaderboardBedrock extends Leaderboard {
    public LeaderboardBedrock(final String player, final long score) {
        super(player, score);
    }

    public static class DatabaseMapper implements RowMapper<LeaderboardBedrock> {
        @Override
        public LeaderboardBedrock map(final ResultSet rs, final StatementContext ctx) throws SQLException {
            return new LeaderboardBedrock(
                    rs.getString("player_name"),
                    rs.getLong("score")
            );
        }
    }
}
