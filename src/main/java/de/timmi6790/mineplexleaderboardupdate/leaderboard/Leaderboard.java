package de.timmi6790.mineplexleaderboardupdate.leaderboard;

import lombok.Data;
import lombok.NonNull;

@Data
public abstract class Leaderboard {
    @NonNull
    private final String player;
    private final long score;
}
