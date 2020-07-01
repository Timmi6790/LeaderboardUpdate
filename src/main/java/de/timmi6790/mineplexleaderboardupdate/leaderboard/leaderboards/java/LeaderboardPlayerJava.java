package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java;

import lombok.Data;
import lombok.NonNull;

import java.util.UUID;

@Data
public class LeaderboardPlayerJava {
    @NonNull
    private final UUID uuid;
    @NonNull
    private final String name;
}
