package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java;

import de.timmi6790.mineplexleaderboardupdate.MapBuilder;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.AbstractLeaderboardUpdate;
import de.timmi6790.mineplexleaderboardupdate.leaderboard.LeaderboardData;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@ToString
public class LeaderboardUpdateJava extends AbstractLeaderboardUpdate<LeaderboardDataJava, LeaderboardJava> {
    private static final Pattern HTML_ROW_PARSER = Pattern.compile("<tr>|<tr class=\"LeaderboardsOdd\">|<tr class=\"LeaderboardsHead\">[^<]*");
    private static final Pattern LEADERBOARD_PATTERN = Pattern.compile("^<td>\\d*<\\/td><td><img src=\"https:\\/\\/crafatar\\.com\\/avatars\\/(.*)\\?size=\\d*" +
            "&overlay\"><\\/td><td><a href=\"\\/players\\/\\w{1,16}\">(\\w{1,16})<\\/td><td> ([\\d|,]*)<\\/td><\\/td>");

    private static final String GET_UPDATE_BOARDS = "SELECT board.id, game.website_name, stat.website_name stat_name, boards.board_name " +
            "FROM java_leaderboard board " +
            "INNER JOIN java_board boards ON boards.id = board.board_id AND TIMESTAMPDIFF(SECOND, board.last_update, CURRENT_TIMESTAMP) >= boards.update_time " +
            "INNER JOIN java_game game ON game.id = board.game_id " +
            "INNER JOIN java_stat stat ON stat.id = board.stat_id " +
            "WHERE board.deprecated = 0 " +
            "OR (SELECT COUNT(*) FROM java_leaderboard_save_id WHERE leaderboard_id = board.id) = 0 " +
            "ORDER BY last_update;";

    private static final String GET_LAST_LEADERBOARD = "SELECT player.player_name, player.uuid uuid, save.score " +
            "FROM java_leaderboard_save_id saveId " +
            "INNER JOIN java_leaderboard_save_new save ON save.leaderboard_save_id = saveId.id " +
            "INNER JOIN java_player player ON player.id = save.player_id " +
            "WHERE saveId.id =(SELECT id FROM java_leaderboard_save_id WHERE leaderboard_id = :leaderboardId ORDER BY id DESC LIMIT 1);";

    private static final String UPDATE_LAST_UPDATE = "UPDATE java_leaderboard SET last_update = CURRENT_TIMESTAMP WHERE id = :leaderboardId LIMIT 1;";

    private static final String INSERT_NEW_SAVE_ID = "INSERT INTO java_leaderboard_save_id (leaderboard_id) VALUES (:leaderboardId);";
    private static final String INSERT_LEADERBOARD_DATA = "INSERT INTO java_leaderboard_save_new(leaderboard_save_id, player_id, score) VALUES(:lastInsertId, (SELECT id FROM java_player WHERE uuid = :uuid LIMIT 1), :score);";

    private static final String GET_PLAYERS_BY_UUID = "SELECT player.uuid uuid, player.player_name player " +
            "FROM java_player player " +
            "WHERE player.uuid IN (<uuids>);";

    private static final String UPDATE_PLAYER_NAME = "UPDATE java_player player SET player.player_name = :playerName WHERE player.uuid = :uuid LIMIT 1;";
    private static final String INSERT_PLAYER = "INSERT INTO java_player(uuid, player_name) VALUES(:uuid, :playerName);";

    public LeaderboardUpdateJava(final String leaderboardBaseUrl, final Jdbi database) {
        super("Java", leaderboardBaseUrl, database,
                UPDATE_LAST_UPDATE, INSERT_NEW_SAVE_ID, GET_UPDATE_BOARDS, INSERT_LEADERBOARD_DATA);

        this.getDatabase()
                .registerRowMapper(LeaderboardDataJava.class, new LeaderboardDataJava.DatabaseMapper())
                .registerRowMapper(LeaderboardJava.class, new LeaderboardJava.DatabaseMapper())
                .registerRowMapper(LeaderboardPlayerJava.class, new LeaderboardPlayerJava.DatabaseMapper());
    }

    @Override
    public void updatePlayerNames(final LeaderboardDataJava leaderboardInfo, final List<LeaderboardJava> leaderboard) {
        this.getDatabase().useHandle(handle -> {
            final Map<UUID, String> playersInDb = handle.createQuery(GET_PLAYERS_BY_UUID)
                    .bindList("uuids", leaderboard.stream().map(LeaderboardJava::getPlayerUUIDBytes).collect(Collectors.toList()))
                    .mapTo(LeaderboardPlayerJava.class)
                    .collect(Collectors.toMap(LeaderboardPlayerJava::getUuid, LeaderboardPlayerJava::getName));

            // Check for new players or player name change
            final PreparedBatch playerUpdateNameBatch = handle.prepareBatch(UPDATE_PLAYER_NAME);
            final PreparedBatch newPlayerBatch = handle.prepareBatch(INSERT_PLAYER);
            for (final LeaderboardJava leaderboardEntry : leaderboard) {
                final String playerName = playersInDb.get(leaderboardEntry.getPlayerUUID());
                if (playerName == null) {
                    Logger.debug("[{}] {}-{}-{} New player entry {} \"{}\"", this.getLeaderboardType(), leaderboardInfo.getWebsiteName(),
                            leaderboardInfo.getStat(), leaderboardInfo.getBoard(), leaderboardEntry.getPlayerUUID(), leaderboardEntry.getPlayer());

                    newPlayerBatch.add(
                            MapBuilder.<String, Object>ofHashMap(2)
                                    .put("playerName", leaderboardEntry.getPlayer())
                                    .put("uuid", leaderboardEntry.getPlayerUUIDBytes())
                                    .build()
                    );

                } else if (!playerName.equals(leaderboardEntry.getPlayer())) {
                    Logger.debug("[{}] {} changed name from \"{}\" to \"{}\"", this.getLeaderboardType(), leaderboardEntry.getPlayerUUID(),
                            leaderboardEntry.getPlayer(), playerName);

                    playerUpdateNameBatch.add(
                            MapBuilder.<String, Object>ofHashMap(2)
                                    .put("playerName", leaderboardEntry.getPlayer())
                                    .put("uuid", leaderboardEntry.getPlayerUUIDBytes())
                                    .build()
                    );
                }
            }
            playerUpdateNameBatch.execute();
            newPlayerBatch.execute();
        });
    }

    @Override
    public Map<String, Object> parseLeaderboardForInsert(final LeaderboardJava leaderboard, final long insertId) {
        return MapBuilder.<String, Object>ofHashMap(3)
                .put("lastInsertId", insertId)
                .put("uuid", leaderboard.getPlayerUUIDBytes())
                .put("score", leaderboard.getScore())
                .build();
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
    protected List<LeaderboardJava> getLastSavedLeaderboard(final LeaderboardData leaderboardData) {
        return this.getDatabase().withHandle(handle ->
                handle.createQuery(GET_LAST_LEADERBOARD)
                        .bind("leaderboardId", leaderboardData.getDatabaseId())
                        .mapTo(LeaderboardJava.class)
                        .list()
        );
    }

    @Override
    protected List<LeaderboardJava> parseWebLeaderboard(final String response) {
        final List<LeaderboardJava> leaderboard = new ArrayList<>(1_000);

        final String[] parts = HTML_ROW_PARSER.split(response);
        for (final String part : parts) {
            final Matcher leaderboardMatcher = LEADERBOARD_PATTERN.matcher(part);
            if (leaderboardMatcher.find()) {
                leaderboard.add(
                        new LeaderboardJava(
                                leaderboardMatcher.group(2),
                                UUID.fromString(leaderboardMatcher.group(1)),
                                Long.parseLong(leaderboardMatcher.group(3).replace(",", ""))
                        )
                );
            }
        }

        return leaderboard;
    }
}
