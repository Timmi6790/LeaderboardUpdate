package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java;

import de.timmi6790.mineplexleaderboardupdate.leaderboard.Leaderboard;
import de.timmi6790.mineplexleaderboardupdate.utilities.UUUIDUtilities;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Getter
@ToString(callSuper = true)
public class LeaderboardJava extends Leaderboard {
    @NonNull
    private final UUID playerUUID;
    @NonNull
    private final byte[] playerUUIDBytes;

    public LeaderboardJava(final String player, final UUID playerUUID, final long score) {
        super(player, score);

        this.playerUUID = playerUUID;
        this.playerUUIDBytes = UUUIDUtilities.getBytesFromUUID(playerUUID);
    }
}
