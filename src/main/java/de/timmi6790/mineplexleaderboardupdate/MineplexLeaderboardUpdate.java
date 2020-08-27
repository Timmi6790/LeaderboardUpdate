package de.timmi6790.mineplexleaderboardupdate;

import de.timmi6790.mineplexleaderboardupdate.leaderboard.cleanup.LeaderboardCleanup;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.bedrock.LeaderboardUpdateBedrock;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java.LeaderboardUpdateJava;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import lombok.Getter;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.jdbi.v3.core.Jdbi;

import java.io.File;
import java.util.concurrent.Executors;

public class MineplexLeaderboardUpdate {
    private static final String BOT_VERSION = "3.0.2";

    @Getter
    private static SentryClient sentry;

    public static void main(final String[] args) throws ConfigurationException {
        final Configurations configs = new Configurations();
        final Configuration config = configs.properties(new File("config.properties"));

        if (!config.getString("sentry.dsn").isEmpty()) {
            sentry = SentryClientFactory.sentryClient(config.getString("sentry.dsn"));
            sentry.setRelease(BOT_VERSION);
        }

        final Jdbi database = Jdbi.create(config.getString("db.url"), config.getString("db.name"), config.getString("db.password"));
        new LeaderboardUpdateJava(config.getString("mp.javaUrl"), database).start();
        new LeaderboardUpdateBedrock(config.getString("mp.bedrockUrl"), database).start();

        // Cleanup
        Executors.newSingleThreadExecutor().submit(() -> new LeaderboardCleanup(database).startCleanup());
    }
}
