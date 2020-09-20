package de.timmi6790.mineplexleaderboardupdate.leaderboard.leaderboards.bedrock;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class LeaderboardUpdateBedrockTest {
    private static LeaderboardUpdateBedrock leaderboardUpdateBedrock;

    @BeforeAll
    static void setUp() {
        leaderboardUpdateBedrock = Mockito.spy(new LeaderboardUpdateBedrock("", Jdbi.create("")));

        final String content = getContentFromFile("bedrock/100_entries");
        doReturn(Optional.of(content)).when(leaderboardUpdateBedrock).getWebResponse(any());
    }

    @SneakyThrows
    private static String getContentFromFile(@NonNull final String path) {
        final ClassLoader classLoader = LeaderboardUpdateBedrockTest.class.getClassLoader();

        final URI uri = classLoader.getResource(path).toURI();
        final byte[] encoded = Files.readAllBytes(Paths.get(uri));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void validatePlayerData(@NonNull final LeaderboardBedrock data, @NonNull final String requiredName, final long requiredScore) {
        assertThat(data.getPlayer()).isEqualTo(requiredName);
        assertThat(data.getScore()).isEqualTo(requiredScore);
    }

    @Test
    void parseWebLeaderboard() {
        final String content = getContentFromFile("bedrock/100_entries");

        final List<LeaderboardBedrock> leaderboard = leaderboardUpdateBedrock.parseWebLeaderboard(content);
        assertThat(leaderboard).hasSize(100);

        validatePlayerData(
                leaderboard.get(0),
                "forevrrfury",
                1_084L
        );

        validatePlayerData(
                leaderboard.get(1),
                "bomblobbers",
                1_012L
        );

        validatePlayerData(
                leaderboard.get(2),
                "rainn3959",
                754L
        );

        validatePlayerData(
                leaderboard.get(3),
                "itzselenasavage",
                742L
        );

        validatePlayerData(
                leaderboard.get(4),
                "fiendishlytm",
                593L
        );

        validatePlayerData(
                leaderboard.get(49),
                "thunder pro 573",
                79L
        );

        validatePlayerData(
                leaderboard.get(50),
                "Potatoking726",
                79L
        );

        validatePlayerData(
                leaderboard.get(99),
                "endrmine",
                58L
        );
    }

    @Test
    void parseWebLeaderboardEmpty() {
        final String content = getContentFromFile("bedrock/0_entries");

        final List<LeaderboardBedrock> leaderboard = leaderboardUpdateBedrock.parseWebLeaderboard(content);
        assertThat(leaderboard).isEmpty();
    }
}