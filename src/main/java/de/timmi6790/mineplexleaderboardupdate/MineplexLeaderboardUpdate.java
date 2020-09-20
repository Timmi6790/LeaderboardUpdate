package de.timmi6790.mineplexleaderboardupdate;

import de.timmi6790.commons.utilities.GsonUtilities;
import de.timmi6790.mineplexleaderboardupdate.cleanup.LeaderboardCleanup;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.bedrock.LeaderboardUpdateBedrock;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java.LeaderboardUpdateJava;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import lombok.Getter;
import org.jdbi.v3.core.Jdbi;
import org.tinylog.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class MineplexLeaderboardUpdate {
    private static final String VERSION = "3.0.2";
    @Getter
    private static final MineplexLeaderboardUpdate instance = new MineplexLeaderboardUpdate();

    @Getter
    private SentryClient sentry;

    public static void main(final String[] args) {
        instance.start();
    }

    private Config getConfig() {
        final Path basePath = Paths.get(".").toAbsolutePath().normalize();
        final Path configPath = Paths.get(basePath + "/config.json");

        final boolean firstInnit;
        final Config config;
        if (!Files.exists(configPath)) {
            firstInnit = true;
            config = new Config();
        } else {
            firstInnit = false;
            config = GsonUtilities.readJsonFile(configPath, Config.class);
        }

        GsonUtilities.saveToJson(configPath, config);
        if (firstInnit) {
            Logger.info("Created main config file.");
            System.exit(1);
        }

        return config;
    }

    public void start() {
        final Config config = this.getConfig();
        if (!config.getSentryDns().isEmpty()) {
            this.sentry = SentryClientFactory.sentryClient(config.getSentryDns());
            this.sentry.setRelease(VERSION);
        }

        final Jdbi database = Jdbi.create(config.getDatabase().getUrl(), config.getDatabase().getName(), config.getDatabase().getPassword());

        // Leaderboards
        if (!config.getLeaderboardUrls().getJava().isEmpty()) {
            new LeaderboardUpdateJava(config.getLeaderboardUrls().getJava(), database).start();
        }
        if (!config.getLeaderboardUrls().getBedrock().isEmpty()) {
            new LeaderboardUpdateBedrock(config.getLeaderboardUrls().getBedrock(), database).start();
        }

        // Cleanup
        Executors.newSingleThreadExecutor().submit(() -> new LeaderboardCleanup(database).startCleanup());
    }
}
