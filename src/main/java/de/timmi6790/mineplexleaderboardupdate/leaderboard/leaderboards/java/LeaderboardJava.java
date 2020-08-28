package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java;

import de.timmi6790.mineplexleaderboardupdate.leaderboard.Leaderboard;
import de.timmi6790.mineplexleaderboardupdate.utilities.UUUIDUtilities;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Getter
@ToString(callSuper = true)
public class LeaderboardJava extends Leaderboard {
    @NonNull
    private final UUID playerUUID;
    private final byte[] playerUUIDBytes;

    public LeaderboardJava(final String player, final UUID playerUUID, final long score) {
        super(player, score);

        this.playerUUID = playerUUID;
        this.playerUUIDBytes = UUUIDUtilities.getBytesFromUUID(playerUUID);
    }

    public static class DatabaseMapper implements RowMapper<LeaderboardJava> {
        @Override
        public LeaderboardJava map(final ResultSet rs, final StatementContext ctx) throws SQLException {
            return new LeaderboardJava(
                    rs.getString("player_name"),
                    UUUIDUtilities.getUUIDFromBytes(rs.getBytes("uuid")),
                    rs.getLong("score")
            );
        }
    }
}
