package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.bedrock;

import de.timmi6790.mineplexleaderboardupdate.MapBuilder;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.AbstractLeaderboardUpdate;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.Leaderboard;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.LeaderboardData;
import kong.unirest.HttpResponse;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LeaderboardUpdateBedrock extends AbstractLeaderboardUpdate<LeaderboardDataBedrock, LeaderboardBedrock> {
    private static final Pattern HTML_ROW_PARSER = Pattern.compile("<tr>|<tr >|<tr class=\"LeaderboardsOdd\">|<tr class=\"LeaderboardsHead\">[^<]*");
    private static final Pattern LEADERBOARD_PATTERN = Pattern.compile("^<td>\\d*<\\/td><td>(.{1,33})<\\/td><td> ([\\d,]*)<\\/td>");

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());

    private static final String UPDATE_LAST_UPDATE = "UPDATE BedrockLeaderboards SET last_update = CURRENT_TIMESTAMP WHERE id = :leaderboardId LIMIT 1;";

    private static final String GET_UPDATE_BOARDS = "SELECT board.id, board.website_name game_name " +
            "FROM BedrockLeaderboards board " +
            "WHERE TIMESTAMPDIFF(SECOND, board.last_update, CURRENT_TIMESTAMP) >= 1800 " +
            "AND (board.deprecated = 0 " +
            "OR (SELECT COUNT(1) FROM BedrockLeaderboardSaveIDs WHERE leaderboard_id = board.id LIMIT 1) = 0) " +
            "ORDER BY last_update;";

    private static final String GET_PLAYERS_BY_NAME = "SELECT player.`name` player\n" +
            "FROM BedrockPlayers player\n" +
            "WHERE player.`name` IN (<names>);";

    private static final String INSERT_PLAYER = "INSERT INTO BedrockPlayers(name) VALUES(:playerName);";

    private static final String GET_LAST_LEADERBOARD = "SELECT player.`name`, save.score " +
            "FROM BedrockLeaderboardSaveIDs saveId " +
            "INNER JOIN BedrockLeaderboardSaves save ON save.leaderboard_save_id = saveId.id " +
            "INNER JOIN BedrockPlayers player ON player.id = save.player_id " +
            "WHERE saveId.id =(SELECT id FROM BedrockLeaderboardSaveIDs WHERE leaderboard_id = :leaderboardId ORDER BY id DESC LIMIT 1);";

    private static final String INSERT_NEW_SAVE_ID = "INSERT INTO BedrockLeaderboardSaveIDs (leaderboard_id) VALUES (:leaderboardId);";
    private static final String INSERT_LEADERBOARD_DATA = "INSERT INTO BedrockLeaderboardSaves(leaderboard_save_id, player_id, score) VALUES (:lastInsertId, (SELECT id FROM BedrockPlayers WHERE `name` = :name LIMIT 1), :score);";

    public LeaderboardUpdateBedrock(final String leaderboardBaseUrl, final Jdbi database) {
        super("Bedrock", leaderboardBaseUrl, database);

        this.getDatabase().registerRowMapper(LeaderboardDataBedrock.class,
                (rs, ctx) -> new LeaderboardDataBedrock(rs.getInt("id"), rs.getString("game_name"))

        ).registerRowMapper(LeaderboardBedrock.class,
                (rs, ctx) -> new LeaderboardBedrock(rs.getString("name"), rs.getLong("score"))
        );
    }

    @Override
    public void update(final LeaderboardData leaderboardInfo) {
        this.getDatabase().useHandle(handle ->
                handle.createUpdate(UPDATE_LAST_UPDATE)
                        .bind("leaderboardId", leaderboardInfo.getDatabaseId())
                        .execute()
        );

        final Optional<List<LeaderboardBedrock>> newLeaderboardOpt = this.getNewWebLeaderboard(
                new MapBuilder<String, Object>(() -> new HashMap<>(1))
                        .put("game", leaderboardInfo.getWebsiteName())
                        .build(),
                leaderboardInfo
        );

        if (!newLeaderboardOpt.isPresent()) {
            return;
        }

        logger.info("[{}] Updating {}", this.getLeaderboardType(), leaderboardInfo.getWebsiteName());

        final List<LeaderboardBedrock> leaderboard = newLeaderboardOpt.get();
        final Set<String> playerNames = leaderboard
                .stream()
                .map(Leaderboard::getPlayer)
                .collect(Collectors.toSet());
        this.getDatabase().useHandle(handle -> {
            final Set<String> newPlayers = handle.createQuery(GET_PLAYERS_BY_NAME)
                    .bindList("names", playerNames)
                    .mapTo(String.class)
                    .stream()
                    .filter(player -> !playerNames.contains(player))
                    .collect(Collectors.toSet());

            // Insert new players
            final PreparedBatch newPlayerBatch = handle.prepareBatch(INSERT_PLAYER);
            newPlayers.forEach(player -> {
                logger.debug("[{}] New player {}", this.getLeaderboardType(), player);
                newPlayerBatch.bind("playerName", player);
                newPlayerBatch.add();
            });
            newPlayerBatch.execute();

            // Insert new data
            final long insertId = handle.createUpdate(INSERT_NEW_SAVE_ID)
                    .bind("leaderboardId", leaderboardInfo.getDatabaseId())
                    .executeAndReturnGeneratedKeys()
                    .mapTo(long.class)
                    .first();

            logger.info("[{}] Insert new {} with {}", this.getLeaderboardType(), leaderboardInfo.getWebsiteName(), insertId);

            final PreparedBatch leaderboardDataInsert = handle.prepareBatch(INSERT_LEADERBOARD_DATA);
            leaderboard.forEach(row -> {
                leaderboardDataInsert.bind("lastInsertId", insertId);
                leaderboardDataInsert.bind("name", row.getPlayer());
                leaderboardDataInsert.bind("score", row.getScore());
                leaderboardDataInsert.add();
            });
            leaderboardDataInsert.execute();
        });
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
    protected List<LeaderboardBedrock> getLastLeaderboard(final LeaderboardData leaderboardData) {
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
