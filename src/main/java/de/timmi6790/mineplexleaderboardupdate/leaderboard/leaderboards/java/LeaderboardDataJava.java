package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java;

import de.timmi6790.mineplexleaderboardupdate.leaderboard.LeaderboardData;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

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
}
