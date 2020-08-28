package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.bedrock;

import de.timmi6790.mineplexleaderboardupdate.MapBuilder;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.AbstractLeaderboardUpdate;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.Leaderboard;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.LeaderboardData;
import kong.unirest.HttpResponse;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.tinylog.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LeaderboardUpdateBedrock extends AbstractLeaderboardUpdate<LeaderboardDataBedrock, LeaderboardBedrock> {
    private static final Pattern HTML_ROW_PARSER = Pattern.compile("<tr>|<tr >|<tr class=\"LeaderboardsOdd\">|<tr class=\"LeaderboardsHead\">[^<]*");
    private static final Pattern LEADERBOARD_PATTERN = Pattern.compile("^<td>\\d*<\\/td><td>(.{1,33})<\\/td><td> ([\\d,]*)<\\/td>");

    private static final String UPDATE_LAST_UPDATE = "UPDATE bedrock_leaderboard SET last_update = CURRENT_TIMESTAMP WHERE id = :leaderboardId LIMIT 1;";

    private static final String GET_UPDATE_BOARDS = "SELECT board.id, board.website_name game_name " +
            "FROM bedrock_leaderboard board " +
            "WHERE TIMESTAMPDIFF(SECOND, board.last_update, CURRENT_TIMESTAMP) >= 1800 " +
            "AND (board.deprecated = 0 " +
            "OR (SELECT COUNT(1) FROM bedrock_leaderboard_save_id WHERE leaderboard_id = board.id LIMIT 1) = 0) " +
            "ORDER BY last_update;";

    private static final String GET_PLAYERS_BY_NAME = "SELECT player.player_name player " +
            "FROM bedrock_player player " +
            "WHERE player.player_name IN (<names>);";

    private static final String INSERT_PLAYER = "INSERT INTO bedrock_player(player_name) VALUES(:playerName);";

    private static final String GET_LAST_LEADERBOARD = "SELECT player.player_name, save.score " +
            "FROM bedrock_leaderboard_save_id saveId " +
            "INNER JOIN bedrock_leaderboard_save save ON save.leaderboard_save_id = saveId.id " +
            "INNER JOIN bedrock_player player ON player.id = save.player_id " +
            "WHERE saveId.id =(SELECT id FROM bedrock_leaderboard_save_id WHERE leaderboard_id = :leaderboardId ORDER BY id DESC LIMIT 1);";

    private static final String INSERT_NEW_SAVE_ID = "INSERT INTO bedrock_leaderboard_save_id (leaderboard_id) VALUES (:leaderboardId);";
    private static final String INSERT_LEADERBOARD_DATA = "INSERT INTO bedrock_leaderboard_save(leaderboard_save_id, player_id, score) " +
            "VALUES (:lastInsertId, (SELECT id FROM bedrock_player WHERE player_name = :playerName LIMIT 1), :score);";

    public LeaderboardUpdateBedrock(final String leaderboardBaseUrl, final Jdbi database) {
        super("Bedrock", leaderboardBaseUrl, database);

        this.getDatabase()
                .registerRowMapper(LeaderboardDataBedrock.class, new LeaderboardDataBedrock.DatabaseMapper())
                .registerRowMapper(LeaderboardBedrock.class, new LeaderboardBedrock.DatabaseMapper());
    }

    @Override
    public void update(final LeaderboardData leaderboardInfo) {
        this.getDatabase().useHandle(handle ->
                handle.createUpdate(UPDATE_LAST_UPDATE)
                        .bind("leaderboardId", leaderboardInfo.getDatabaseId())
                        .execute()
        );

        this.getNewWebLeaderboard(
                MapBuilder.<String, Object>ofHashMap(1)
                        .put("game", leaderboardInfo.getWebsiteName())
                        .build(),
                leaderboardInfo
        ).ifPresent(leaderboard -> {
                    Logger.info("[{}] Updating {}", this.getLeaderboardType(), leaderboardInfo.getWebsiteName());

                    this.getDatabase().useHandle(handle -> {
                        // Remove db player names from the player names list
                        final Set<String> playerNamesInDb = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                        playerNamesInDb.addAll(
                                handle.createQuery(GET_PLAYERS_BY_NAME)
                                        .bindList("names", leaderboard.stream().map(Leaderboard::getPlayer).collect(Collectors.toList()))
                                        .mapTo(String.class)
                                        .list()
                        );

                        // Insert new players
                        final PreparedBatch newPlayerBatch = handle.prepareBatch(INSERT_PLAYER);
                        for (final LeaderboardBedrock leaderboardEntry : leaderboard) {
                            if (playerNamesInDb.contains(leaderboardEntry.getPlayer())) {
                                continue;
                            }

                            Logger.debug("[{}] New player {}", this.getLeaderboardType(), leaderboardEntry.getPlayer());
                            newPlayerBatch.bind("playerName", leaderboardEntry.getPlayer());
                            newPlayerBatch.add();
                        }
                        newPlayerBatch.execute();

                        // Insert new data
                        final long insertId = handle.createUpdate(INSERT_NEW_SAVE_ID)
                                .bind("leaderboardId", leaderboardInfo.getDatabaseId())
                                .executeAndReturnGeneratedKeys()
                                .mapTo(long.class)
                                .first();

                        Logger.info("[{}] Insert new {} with {}", this.getLeaderboardType(), leaderboardInfo.getWebsiteName(), insertId);

                        final PreparedBatch leaderboardDataInsert = handle.prepareBatch(INSERT_LEADERBOARD_DATA);
                        for (final LeaderboardBedrock row : leaderboard) {
                            leaderboardDataInsert.bind("lastInsertId", insertId);
                            leaderboardDataInsert.bind("playerName", row.getPlayer());
                            leaderboardDataInsert.bind("score", row.getScore());
                            leaderboardDataInsert.add();
                        }
                        leaderboardDataInsert.execute();
                    });
                }
        );
    }

    @Override
    public List<LeaderboardDataBedrock> getBoardsInNeedOfUpdate() {
        return this.getDatabase().withHandle(handle ->
                handle.createQuery(GET_UPDATE_BOARDS)
                        .mapTo(LeaderboardDataBedrock.class)
                        .list()
        );
    }

    @Override
    protected List<LeaderboardBedrock> getLastSavedLeaderboard(final LeaderboardData leaderboardData) {
        return this.getDatabase().withHandle(
                handle -> handle.createQuery(GET_LAST_LEADERBOARD)
                        .bind("leaderboardId", leaderboardData.getDatabaseId())
                        .mapTo(LeaderboardBedrock.class)
                        .list()
        );
    }

    @Override
    protected List<LeaderboardBedrock> parseWebLeaderboard(final HttpResponse<String> response) {
        return Arrays.stream(HTML_ROW_PARSER.split(response.getBody()))
                .map(LEADERBOARD_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> new LeaderboardBedrock(matcher.group(1), Long.parseLong(matcher.group(2).replace(",", ""))))
                .collect(Collectors.toList());
    }
}
