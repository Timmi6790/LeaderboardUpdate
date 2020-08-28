package de.timmi6790.mineplexleaderboardupdate.leaderboard.cleanup;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LeaderboardCleanup {
    private static final int KEEP_ENTRIES = 3;
    private static final int OLDER_THAN_MONTHS = 2;

    private static final String GET_SAVE_IDS = "SELECT deleteData.ids " +
            "FROM ( " +
            "SELECT GROUP_CONCAT(saveID.id) ids, COUNT(*) number " +
            "FROM `java_leaderboard_save_id` saveID " +
            "WHERE saveID.datetime < NOW() - INTERVAL :olderThanMonths MONTH " +
            "GROUP BY TIMESTAMPADD(DAY, TIMESTAMPDIFF(DAY,CURDATE(),CONVERT_TZ(FROM_UNIXTIME(UNIX_TIMESTAMP(saveID.datetime)), '+00:00', '-06:00')), CURDATE()), saveID.leaderboard_id " +
            ") deleteData " +
            "WHERE deleteData.number > :minEntries";
    private static final String DELETE_ID = "DELETE FROM java_leaderboard_save_id WHERE id = :id LIMIT 1";

    private final Jdbi database;

    public LeaderboardCleanup(final Jdbi database) {
        this.database = database;
    }

    public void startCleanup() {
        Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(() -> {
            final List<String[]> foundIds = this.database.withHandle(handle -> handle.createQuery(GET_SAVE_IDS)
                    .bind("olderThanMonths", OLDER_THAN_MONTHS)
                    .bind("minEntries", KEEP_ENTRIES)
                    .map((rs, ctx) -> rs.getString("ids").split(","))
                    .list());

            Logger.info("Found {} entries for deletion.", foundIds.stream().mapToInt(array -> array.length - KEEP_ENTRIES).sum());
            final List<String> idList = new ArrayList<>();
            for (final String[] ids : foundIds) {
                for (int index = 0; ids.length > index; index++) {
                    // Keep
                    // n -> last
                    // n/2 -> middle
                    // n/4 -> one in the morning
                    if (index == ids.length - 1 || index == ids.length / 2 || index == ids.length / 4) {
                        continue;
                    }

                    idList.add(ids[index]);
                }

                if (idList.size() >= 1_000) {
                    this.deleteIds(idList);
                }
            }

            this.deleteIds(idList);
        }, 0, 6, TimeUnit.HOURS);
    }

    private void deleteIds(final List<String> idList) {
        Logger.info("Deleting: {}", idList.size());
        this.database.useHandle(handle -> {
            final PreparedBatch deleteBatch = handle.prepareBatch(DELETE_ID);
            for (final String id : idList) {
                deleteBatch.bind("id", id);
                deleteBatch.add();
            }
            deleteBatch.execute();
        });
        Logger.info("Done");
        idList.clear();
    }
}
