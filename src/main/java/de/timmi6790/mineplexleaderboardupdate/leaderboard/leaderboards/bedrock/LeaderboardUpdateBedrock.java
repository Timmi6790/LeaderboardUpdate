package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.bedrock;

import de.timmi6790.mineplexleaderboardupdate.MapBuilder;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.AbstractLeaderboardUpdate;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.Leaderboard;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.LeaderboardData;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.tinylog.Logger;

import java.util.*;
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
        super("Bedrock", leaderboardBaseUrl, database,
                UPDATE_LAST_UPDATE, INSERT_NEW_SAVE_ID, GET_UPDATE_BOARDS, INSERT_LEADERBOARD_DATA);

        this.getDatabase()
                .registerRowMapper(LeaderboardDataBedrock.class, new LeaderboardDataBedrock.DatabaseMapper())
                .registerRowMapper(LeaderboardBedrock.class, new LeaderboardBedrock.DatabaseMapper());
    }

    @Override
    public void updatePlayerNames(final LeaderboardDataBedrock leaderboardInfo, final List<LeaderboardBedrock> leaderboard) {
        this.getDatabase().useHandle(handle -> {
            // Remove known names from the player names list
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
        });
    }

    @Override
    public Map<String, Object> parseLeaderboardForInsert(final LeaderboardBedrock leaderboard, final long insertId) {
        return MapBuilder.<String, Object>ofHashMap(3)
                .put("lastInsertId", insertId)
                .put("playerName", leaderboard.getPlayer())
                .put("score", leaderboard.getScore())
                .build();
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
    protected List<LeaderboardBedrock> parseWebLeaderboard(final String response) {
        final List<LeaderboardBedrock> leaderboard = new ArrayList<>(100);

        final String[] parts = HTML_ROW_PARSER.split(response);
        for (final String part : parts) {
            final Matcher leaderboardMatcher = LEADERBOARD_PATTERN.matcher(part);
            if (leaderboardMatcher.find()) {
                leaderboard.add(
                        new LeaderboardBedrock(
                                leaderboardMatcher.group(1),
                                Long.parseLong(leaderboardMatcher.group(2).replace(",", ""))
                        )
                );
            }
        }

        return leaderboard;
    }
}
