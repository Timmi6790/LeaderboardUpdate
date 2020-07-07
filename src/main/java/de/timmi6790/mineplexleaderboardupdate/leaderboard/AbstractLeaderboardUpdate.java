package de.timmi6790.mineplexleaderboardupdate.leaderboard;

import de.timmi6790.mineplexleaderboardupdate.MineplexLeaderboardUpdate;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Data;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Data
public abstract class AbstractLeaderboardUpdate<T extends LeaderboardData, D extends Leaderboard> {
    private static final int UPDATE_THREADS = 1;
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36";

    private static final Logger logger = LoggerFactory.getLogger(AbstractLeaderboardUpdate.class);

    private final String leaderboardType;
    private final String leaderboardBaseUrl;
    private final Jdbi database;

    public abstract List<T> getBoardsInNeedOfUpdate();

    protected abstract List<D> getLastLeaderboard(LeaderboardData leaderboardData);

    protected abstract List<D> parseWebLeaderboard(HttpResponse<String> response);

    public abstract void update(final LeaderboardData leaderboardInfo);

    public void start() {
        logger.info("Start {}", this.leaderboardType);

        final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(UPDATE_THREADS);
        executor.setKeepAliveTime(5, TimeUnit.MINUTES);

        Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(() -> {
            final List<T> boards = this.getBoardsInNeedOfUpdate();
            final CountDownLatch countDownLatch = new CountDownLatch(boards.size());
            boards.forEach(leaderboardData ->
                    executor.submit(() -> {
                        try {
                            logger.debug("[{}] update run for {}", this.leaderboardType, leaderboardData);
                            this.update(leaderboardData);
                        } catch (final Exception e) {
                            logger.error("{} {}", this.getLeaderboardType(), e);
                            MineplexLeaderboardUpdate.getSentry().sendException(e);
                        } finally {
                            countDownLatch.countDown();
                        }
                    })
            );

            try {
                countDownLatch.await(1, TimeUnit.HOURS);
            } catch (final InterruptedException ignore) {
            }

        }, 0, 5, TimeUnit.MINUTES);
    }

    public Optional<List<D>> getWebLeaderboard(final Map<String, Object> parameters) {
        final HttpResponse<String> response;
        try {
            response = Unirest.get(this.leaderboardBaseUrl)
                    .queryString(parameters)
                    .queryString("antiCache", String.valueOf(System.currentTimeMillis()))
                    .header("User-Agent", USER_AGENT)
                    .connectTimeout(15_000)
                    .asString();
        } catch (final Exception e) {
            logger.error("{} {}", this.leaderboardType, parameters.values(), e);
            return Optional.empty();
        }

        if (!response.isSuccess() || response.getBody().isEmpty()) {
            logger.error("{} EMPTY {}", this.leaderboardType, parameters.values());
            return Optional.empty();
        }

        return Optional.of(this.parseWebLeaderboard(response))
                .filter(list -> !list.isEmpty());
    }

    protected Optional<List<D>> getNewWebLeaderboard(final Map<String, Object> parameters, final LeaderboardData leaderboardData) {
        return this.getWebLeaderboard(parameters)
                .filter(leaderboard -> this.hasLeaderboardChanged(leaderboard, leaderboardData));
    }

    protected boolean hasLeaderboardChanged(final List<D> leaderboard, final LeaderboardData leaderboardInfo) {
        return !leaderboard.equals(this.getLastLeaderboard(leaderboardInfo));
    }
}
