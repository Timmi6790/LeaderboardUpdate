package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.java;

import lombok.NonNull;
import lombok.SneakyThrows;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class LeaderboardUpdateJavaTest {
    private static LeaderboardUpdateJava leaderboardUpdateJava;

    @BeforeAll
    static void setUp() {
        leaderboardUpdateJava = Mockito.spy(new LeaderboardUpdateJava("", Jdbi.create("")));

        final String content = getContentFromFile("java/1000_entries");
        doReturn(Optional.of(content)).when(leaderboardUpdateJava).getWebResponse(any());
    }

    @SneakyThrows
    private static String getContentFromFile(@NonNull final String path) {
        final ClassLoader classLoader = LeaderboardUpdateJavaTest.class.getClassLoader();

        final URI uri = classLoader.getResource(path).toURI();
        final byte[] encoded = Files.readAllBytes(Paths.get(uri));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void validatePlayerData(@NonNull final LeaderboardJava data, @NonNull final String requiredName, @NonNull final String requiredUUID, final long requiredScore) {
        assertThat(data.getPlayer()).isEqualTo(requiredName);
        assertThat(data.getPlayerUUID()).isEqualTo(UUID.fromString(requiredUUID));
        assertThat(data.getScore()).isEqualTo(requiredScore);
    }

    @Test
    void parseLeaderboardForInsert() {
        final String content = getContentFromFile("java/1000_entries");

        final List<LeaderboardJava> leaderboard = leaderboardUpdateJava.parseWebLeaderboard(content);
        for (final LeaderboardJava leaderboardEntry : leaderboard) {
            final Map<String, Object> parsed = leaderboardUpdateJava.parseLeaderboardForInsert(leaderboardEntry, 1);
            assertThat(parsed).containsEntry("lastInsertId", 1L);
            assertThat(parsed).containsEntry("uuid", leaderboardEntry.getPlayerUUIDBytes());
            assertThat(parsed).containsEntry("score", leaderboardEntry.getScore());
        }
    }

    @Test
    void parseWebLeaderboardBig() {
        final String content = getContentFromFile("java/1000_entries");

        final List<LeaderboardJava> leaderboard = leaderboardUpdateJava.parseWebLeaderboard(content);
        assertThat(leaderboard)
                .hasSize(1_000);

        validatePlayerData(
                leaderboard.get(0),
                "Phinary",
                "b33207e2-0dc5-4cbd-b3ee-6c860727f722",
                42_100_726_053L
        );

        validatePlayerData(
                leaderboard.get(1),
                "Mysticate",
                "5c359761-d55b-43a4-9b75-2cc64f8d027f",
                12_885_842_896L
        );

        validatePlayerData(
                leaderboard.get(2),
                "LCastr0",
                "a68b8d0e-24be-4851-afa1-9e4c506b3e92",
                1_010_133_249L
        );

        validatePlayerData(
                leaderboard.get(3),
                "Relyh",
                "68b61e3c-4be0-4c0c-8897-6a8d3703fe9a",
                1_001_294_815L
        );

        validatePlayerData(
                leaderboard.get(4),
                "B2_mp",
                "efaf9a17-2304-4f42-8433-421523c308dc",
                1_000_984_088L
        );

        validatePlayerData(
                leaderboard.get(499),
                "bwear",
                "464872bd-048f-4a17-859f-17b1ce886210",
                13_345_882L
        );

        validatePlayerData(
                leaderboard.get(500),
                "DraZZeLxCaMZZ",
                "0eb0256c-449f-4a13-9d59-4912e5f5f10d",
                13_345_213L
        );
    }

    @Test
    void parseWebLeaderboardSmall() {
        final String content = getContentFromFile("java/67_entries");

        final List<LeaderboardJava> leaderboard = leaderboardUpdateJava.parseWebLeaderboard(content);
        assertThat(leaderboard)
                .hasSize(67);

        validatePlayerData(
                leaderboard.get(0),
                "cocoalinaa",
                "af033982-0fea-478c-8a71-9a3e343dd531",
                2L
        );

        validatePlayerData(
                leaderboard.get(42),
                "Apsungi",
                "891a8852-e10e-4fe0-b79d-8c1b59716d65",
                1L
        );

        validatePlayerData(
                leaderboard.get(66),
                "alexl2810",
                "ba358fac-70bf-4ec8-8ba3-60cf440dcef2",
                1L
        );
    }

    @Test
    void parseWebLeaderboardEmpty() {
        final String content = getContentFromFile("java/0_entries");

        final List<LeaderboardJava> leaderboard = leaderboardUpdateJava.parseWebLeaderboard(content);
        assertThat(leaderboard).isEmpty();
    }
}