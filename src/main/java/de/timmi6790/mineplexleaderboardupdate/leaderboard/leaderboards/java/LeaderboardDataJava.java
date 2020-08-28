package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java;

import de.timmi6790.mineplexleaderboardupdate.leaderboard.LeaderboardData;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LeaderboardDataJava extends LeaderboardData {
    @NonNull
    private final String stat;
    @NonNull
    private final String board;

    public LeaderboardDataJava(final int databaseId, final String websiteName, final String stat, final String board) {
        super(databaseId, websiteName);

        this.stat = stat;
        this.board = board;
    }

    public static class DatabaseMapper implements RowMapper<LeaderboardDataJava> {
        @Override
        public LeaderboardDataJava map(final ResultSet rs, final StatementContext ctx) throws SQLException {
            return new LeaderboardDataJava(
                    rs.getInt("id"),
                    rs.getString("website_name"),
                    rs.getString("stat_name"),
                    rs.getString("board_name")
            );
        }
    }
}
