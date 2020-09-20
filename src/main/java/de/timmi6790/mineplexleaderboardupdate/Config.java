package de.timmi6790.mineplexleaderboardupdate;

import lombok.Data;

@Data
public class Config {
    private final Database database = new Database();
    private final LeaderboardUrls leaderboardUrls = new LeaderboardUrls();
    private String sentryDns = "";

    @Data
    public static class Database {
        private String url = "";
        private String name = "";
        private String password = "";
    }

    @Data
    public static class LeaderboardUrls {
        private String java = "";
        private String bedrock = "";
    }
}
