package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java;

import de.timmi6790.mineplexleaderboardupdate.MapBuilder;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.AbstractLeaderboardUpdate;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.LeaderboardData;
import de.timmi6790.mineplexleaderboardupdate.utilities.UUUIDUtilities;
import kong.unirest.HttpResponse;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
public class LeaderboardUpdateJava extends AbstractLeaderboardUpdate<LeaderboardDataJava, LeaderboardJava> {
    private static final Pattern HTML_ROW_PARSER = Pattern.compile("<tr>|<tr class=\"LeaderboardsOdd\">|<tr class=\"LeaderboardsHead\">[^<]*");
    private static final Pattern LEADERBOARD_PATTERN = Pattern.compile("^<td>\\d*<\\/td><td><img src=\"https:\\/\\/crafatar\\.com\\/avatars\\/(.*)\\?size=\\d*" +
            "&overlay\"><\\/td><td><a href=\"\\/players\\/\\w{1,16}\">(\\w{1,16})<\\/td><td> ([\\d|,]*)<\\/td><\\/td>");

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardUpdateJava.class);

    private static final String GET_UPDATE_BOARDS = "SELECT board.id, game.website_name, stat.website_name stat_name, boards.board_name " +
            "FROM java_leaderboard board " +
            "INNER JOIN java_board boards ON boards.id = board.board_id AND TIMESTAMPDIFF(SECOND, board.last_update, CURRENT_TIMESTAMP) >= boards.update_time " +
            "INNER JOIN java_game game ON game.id = board.game_id " +
            "INNER JOIN java_stat stat ON stat.id = board.stat_id " +
            "WHERE board.deprecated = 0 " +
            "OR (SELECT COUNT(*) FROM java_leaderboard_save_id WHERE leaderboard_id = board.id LIMIT 1) = 0 " +
            "ORDER BY last_update;";

    private static final String GET_LAST_LEADERBOARD = "SELECT player.player_name, player.uuid uuid, save.score " +
            "FROM java_leaderboard_save_id saveId " +
            "INNER JOIN java_leaderboard_save save ON save.leaderboard_save_id = saveId.id " +
            "INNER JOIN java_player player ON player.id = save.player_id " +
            "WHERE saveId.id =(SELECT id FROM java_leaderboard_save_id WHERE leaderboard_id = :leaderboardId ORDER BY id DESC LIMIT 1);";

    private static final String UPDATE_LAST_UPDATE = "UPDATE java_leaderboard SET last_update = CURRENT_TIMESTAMP WHERE id = :leaderboardId LIMIT 1;";

    private static final String INSERT_NEW_SAVE = "INSERT INTO java_leaderboard_save_id (leaderboard_id) VALUES (:leaderboardId);";
    private static final String INSERT_LEADERBOARD_DATA = "INSERT INTO java_leaderboard_save(leaderboard_save_id, player_id, score) VALUES(:lastInsertId, (SELECT id FROM java_player WHERE uuid = :uuid LIMIT 1), :score);";

    private static final String GET_PLAYERS_BY_UUID = "SELECT player.uuid uuid, player.player_name player " +
            "FROM java_player player " +
            "WHERE player.uuid IN (<uuids>);";

    private static final String UPDATE_PLAYER_NAME = "UPDATE java_player player SET player.player_name = :playerName WHERE player.uuid = :uuid LIMIT 1;";
    private static final String INSERT_PLAYER = "INSERT INTO java_player(uuid, player_name) VALUES(:uuid, :playerName);";

    public LeaderboardUpdateJava(final String leaderboardBaseUrl, final Jdbi database) {
        super("Java", leaderboardBaseUrl, database);

        this.getDatabase().registerRowMapper(LeaderboardDataJava.class,
                (rs, ctx) -> new LeaderboardDataJava(rs.getInt("id"), rs.getString("website_name"), rs.getString("stat_name"), rs.getString("board_name"))

        ).registerRowMapper(LeaderboardJava.class,
                (rs, ctx) -> new LeaderboardJava(rs.getString("player_name"), UUUIDUtilities.getUUIDFromBytes(rs.getBytes("uuid")), rs.getLong("score"))

        ).registerRowMapper(LeaderboardPlayerJava.class,
                (rs, ctx) -> new LeaderboardPlayerJava(UUUIDUtilities.getUUIDFromBytes(rs.getBytes("uuid")), rs.getString("player"))
        );
    }

    @Override
    public void update(final LeaderboardData leaderboardInfo) {
        this.getDatabase().useHandle(handle ->
                handle.createUpdate(UPDATE_LAST_UPDATE)
                        .bind("leaderboardId", leaderboardInfo.getDatabaseId())
                        .execute()
        );

        final LeaderboardDataJava leaderboardInfoJava = (LeaderboardDataJava) leaderboardInfo;
        final Optional<List<LeaderboardJava>> newLeaderboardOpt = this.getNewWebLeaderboard(
                new MapBuilder<String, Object>(() -> new HashMap<>(3))
                        .put("game", leaderboardInfoJava.getWebsiteName())
                        .put("type", leaderboardInfoJava.getStat())
                        .put("boardType", leaderboardInfoJava.getBoard())
                        .build(),
                leaderboardInfoJava
        );

        if (!newLeaderboardOpt.isPresent()) {
            return;
        }

        logger.info("[{}] Updating {} {} {} {}", this.getLeaderboardType(), leaderboardInfoJava.getWebsiteName(), leaderboardInfoJava.getStat(),
                leaderboardInfoJava.getBoard(), leaderboardInfoJava.getDatabaseId());

        final List<LeaderboardJava> leaderboard = newLeaderboardOpt.get();
        final List<byte[]> playerUUIDs = leaderboard.parallelStream()
                .unordered()
                .map(LeaderboardJava::getPlayerUUIDBytes)
                .collect(Collectors.toList());
        this.getDatabase().useHandle(handle -> {
            final Map<UUID, String> playersInDb = handle.createQuery(GET_PLAYERS_BY_UUID)
                    .bindList("uuids", playerUUIDs)
                    .mapTo(LeaderboardPlayerJava.class)
                    .stream()
                    .parallel()
                    .unordered()
                    .collect(Collectors.toConcurrentMap(LeaderboardPlayerJava::getUuid, LeaderboardPlayerJava::getName));

            // Check for new players or player name change
            final PreparedBatch playerUpdateNameBatch = handle.prepareBatch(UPDATE_PLAYER_NAME);
            final PreparedBatch newPlayerBatch = handle.prepareBatch(INSERT_PLAYER);
            leaderboard.forEach(row -> {
                final Optional<String> playerName = Optional.ofNullable(playersInDb.get(row.getPlayerUUID()));
                if (!playerName.isPresent()) {
                    logger.debug("[{}] {}-{}-{} New player entry {} \"{}\"", this.getLeaderboardType(), leaderboardInfoJava.getWebsiteName(),
                            leaderboardInfoJava.getStat(), leaderboardInfoJava.getBoard(), row.getPlayerUUID(), row.getPlayer());

                    newPlayerBatch.bind("playerName", row.getPlayer());
                    newPlayerBatch.bind("uuid", row.getPlayerUUIDBytes());
                    newPlayerBatch.add();

                } else if (!playerName.get().equals(row.getPlayer())) {
                    logger.debug("[{}] {} changed name from \"{}\" to \"{}\"", this.getLeaderboardType(), row.getPlayerUUID(), row.getPlayer(), playerName.get());

                    playerUpdateNameBatch.bind("playerName", row.getPlayer());
                    playerUpdateNameBatch.bind("uuid", row.getPlayerUUIDBytes());
                    playerUpdateNameBatch.add();
                }
            });
            playerUpdateNameBatch.execute();
            newPlayerBatch.execute();

            // Insert new leaderboard save point
            final long insertId = handle.createUpdate(INSERT_NEW_SAVE)
                    .bind("leaderboardId", leaderboardInfoJava.getDatabaseId())
                    .executeAndReturnGeneratedKeys()
                    .mapTo(long.class)
                    .first();

            logger.info("[{}] Insert new {}-{}-{} with {}", this.getLeaderboardType(), leaderboardInfoJava.getWebsiteName(), leaderboardInfoJava.getStat(),
                    leaderboardInfoJava.getBoard(), insertId);

            // Insert new leaderboard data
            final PreparedBatch leaderboardInsert = handle.prepareBatch(INSERT_LEADERBOARD_DATA);
            leaderboard.forEach(row -> {
                leaderboardInsert.bind("lastInsertId", insertId);
                leaderboardInsert.bind("uuid", row.getPlayerUUIDBytes());
                leaderboardInsert.bind("score", row.getScore());
                leaderboardInsert.add();
            });
            leaderboardInsert.execute();
        });
    }

    @Override
    public List<LeaderboardDataJava> getBoardsInNeedOfUpdate() {
        return this.getDatabase().withHandle(handle ->
                handle.createQuery(GET_UPDATE_BOARDS)
                        .mapTo(LeaderboardDataJava.class)
                        .list()
        );
    }

    @Override
    protected List<LeaderboardJava> getLastLeaderboard(final LeaderboardData leaderboardData) {
        return this.getDatabase().withHandle(
                handle -> handle.createQuery(GET_LAST_LEADERBOARD)
                        .bind("leaderboardId", leaderboardData.getDatabaseId())
                        .mapTo(LeaderboardJava.class)
                        .list()
        );
    }

    @Override
    protected List<LeaderboardJava> parseWebLeaderboard(final HttpResponse<String> response) {
        return Arrays.stream(HTML_ROW_PARSER.split(response.getBody()))
                .map(LEADERBOARD_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> new LeaderboardJava(matcher.group(2), UUID.fromString(matcher.group(1)), Long.parseLong(matcher.group(3).replace(",", ""))))
                .collect(Collectors.toList());
    }
}
