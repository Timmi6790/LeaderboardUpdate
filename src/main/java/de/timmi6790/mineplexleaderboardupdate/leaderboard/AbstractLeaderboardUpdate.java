package de.timmi6790.mineplexleaderboardupdate.leaderboard;

import de.timmi6790.mineplexleaderboardupdate.MineplexLeaderboardUpdate;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Data;
import org.jdbi.v3.core.Jdbi;
import org.tinylog.Logger;

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

    private final String leaderboardType;
    private final String leaderboardBaseUrl;
    private final Jdbi database;

    public abstract List<T> getBoardsInNeedOfUpdate();

    protected abstract List<D> getLastSavedLeaderboard(LeaderboardData leaderboardData);

    protected abstract List<D> parseWebLeaderboard(HttpResponse<String> response);

    public abstract void update(final LeaderboardData leaderboardInfo);

    public void start() {
        Logger.info("Start {}", this.leaderboardType);

        final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(UPDATE_THREADS);
        Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(() -> {
            final List<T> boards = this.getBoardsInNeedOfUpdate();
            final CountDownLatch countDownLatch = new CountDownLatch(boards.size());
            for (final T leaderboardData : boards) {
                executor.submit(() -> {
                    try {
                        Logger.debug("[{}] update run for {}", this.leaderboardType, leaderboardData);
                        this.update(leaderboardData);
                    } catch (final Exception e) {
                        Logger.error("[{}] {}", this.getLeaderboardType(), e);
                        MineplexLeaderboardUpdate.getSentry().sendException(e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

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
                    .queryString("antiCache", System.currentTimeMillis())
                    .header("User-Agent", USER_AGENT)
                    .connectTimeout(15_000)
                    .asString();
        } catch (final Exception e) {
            Logger.error("{} {}", this.leaderboardType, parameters.values(), e);
            return Optional.empty();
        }

        if (!response.isSuccess() || response.getBody().isEmpty()) {
            Logger.error("{} EMPTY {}", this.leaderboardType, parameters.values());
            return Optional.empty();
        }

        return Optional.of(this.parseWebLeaderboard(response)).filter(list -> !list.isEmpty());
    }

    protected Optional<List<D>> getNewWebLeaderboard(final Map<String, Object> parameters, final LeaderboardData leaderboardData) {
        return this.getWebLeaderboard(parameters).filter(leaderboard -> !leaderboard.equals(this.getLastSavedLeaderboard(leaderboardData)));
    }
}
