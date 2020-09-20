package de.timmi6790.mineplexleaderboardupdate.leaderboard;

import lombok.Data;
import lombok.NonNull;

import java.util.Map;

@Data
public abstract class LeaderboardData {
    private final int databaseId;
    @NonNull
    private final String websiteName;

    public abstract Map<String, Object> getNewWebLeaderboardParameters();

    public abstract String getLogInfo();
}
