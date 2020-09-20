package de.timmi6790.mineplexleaderboardupdate.leaderboard;

import de.timmi6790.mineplexleaderboardupdate.MineplexLeaderboardUpdate;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Data;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.tinylog.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Data
public abstract class AbstractLeaderboardUpdate<D extends LeaderboardData, L extends Leaderboard> {
    private static final int UPDATE_THREADS = 1;
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36";

    private final String leaderboardType;
    private final String leaderboardBaseUrl;
    private final Jdbi database;

    private final String sqlUpdateLastCheckQuery;
    private final String sqlInsertNewIdQuery;
    private final String sqlBoardsInNeedOfUpdateQuery;
    private final String sqlInsertNewDataQuery;

    public abstract List<D> getBoardsInNeedOfUpdate();

    protected abstract List<L> getLastSavedLeaderboard(LeaderboardData leaderboardData);

    protected abstract List<L> parseWebLeaderboard(String response);

    public abstract void updatePlayerNames(final D leaderboardInfo, final List<L> leaderboard);

    public abstract Map<String, Object> parseLeaderboardForInsert(L leaderboard, long insertId);

    public Optional<String> getWebResponse(final Map<String, Object> parameters) {
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

        return Optional.of(response.getBody());
    }

    public Optional<List<L>> getWebLeaderboard(final Map<String, Object> parameters) {
        final Optional<String> webResponse = this.getWebResponse(parameters);
        return webResponse.flatMap(response -> Optional.of(this.parseWebLeaderboard(response)).filter(list -> !list.isEmpty()));

    }

    private Optional<List<L>> getNewWebLeaderboard(final Map<String, Object> parameters, final LeaderboardData leaderboardData) {
        return this.getWebLeaderboard(parameters).filter(leaderboard -> !leaderboard.equals(this.getLastSavedLeaderboard(leaderboardData)));
    }

    public void update(final D leaderboardData) {
        Logger.debug("[{}] update run for {}", this.leaderboardType, leaderboardData);

        // Update the last update time
        this.getDatabase().useHandle(handle ->
                handle.createUpdate(this.getSqlUpdateLastCheckQuery())
                        .bind("leaderboardId", leaderboardData.getDatabaseId())
                        .execute()
        );

        // Get new leaderboard from website
        final Optional<List<L>> leaderboardOpt = this.getNewWebLeaderboard(
                leaderboardData.getNewWebLeaderboardParameters(),
                leaderboardData
        );

        if (!leaderboardOpt.isPresent()) {
            // No data found
            return;
        }

        Logger.info("[{}] Updating ", this.getLeaderboardType(), leaderboardData.getLogInfo());
        final List<L> leaderboard = leaderboardOpt.get();

        this.updatePlayerNames(leaderboardData, leaderboard);
        this.getDatabase().useHandle(handle -> {
            // Insert new leaderboard save point
            final long insertId = handle.createUpdate(this.getSqlInsertNewIdQuery())
                    .bind("leaderboardId", leaderboardData.getDatabaseId())
                    .executeAndReturnGeneratedKeys()
                    .mapTo(long.class)
                    .first();

            Logger.info("[{}] Insert new {} with {}",
                    this.getLeaderboardType(),
                    leaderboardData.getLogInfo(),
                    insertId
            );

            // Insert new data
            final PreparedBatch leaderboardInsert = handle.prepareBatch(this.getSqlInsertNewDataQuery());
            leaderboard.forEach(leaderboardEntry -> leaderboardInsert.add(this.parseLeaderboardForInsert(leaderboardEntry, insertId)));
            leaderboardInsert.execute();
        });
    }

    public void start() {
        Logger.info("Start {}", this.leaderboardType);

        final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(UPDATE_THREADS);
        Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(() -> {
            final List<D> boards = this.getBoardsInNeedOfUpdate();

            final CountDownLatch countDownLatch = new CountDownLatch(boards.size());
            for (final D leaderboardData : boards) {
                executor.submit(() -> {
                    try {
                        this.update(leaderboardData);
                    } catch (final Exception e) {
                        Logger.error("[{}] {}", this.getLeaderboardType(), e);
                        MineplexLeaderboardUpdate.getInstance().getSentry().sendException(e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

            try {
                countDownLatch.await();
            } catch (final InterruptedException e) {
                Logger.error(e);
            }

        }, 0, 5, TimeUnit.MINUTES);
    }
}
