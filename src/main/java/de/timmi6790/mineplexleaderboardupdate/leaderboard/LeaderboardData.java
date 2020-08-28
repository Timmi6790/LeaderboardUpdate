package de.timmi6790.mineplexleaderboardupdate.leaderboard;

import lombok.Data;
import lombok.NonNull;

@Data
public abstract class LeaderboardData {
    private final int databaseId;
    @NonNull
    private final String websiteName;
}
