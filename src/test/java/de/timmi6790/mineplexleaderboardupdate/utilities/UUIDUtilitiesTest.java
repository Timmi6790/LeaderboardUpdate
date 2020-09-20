package de.timmi6790.mineplexleaderboardupdate.utilities;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UUIDUtilitiesTest {
    @ParameterizedTest
    @ValueSource(strings = {"30ca932f-3f92-46c9-99e4-4a3e64f345ac", "5a4b4a5d-1870-4d49-8e20-7fad6142b593", "f432486e-1904-4c12-81f2-7f60c625e649",
            "06f7756e-0e2a-42f6-a085-9af212fa3c95", "cb8d3957-2d37-4f76-b291-c115ec0f1cf3", "04a1de80-c33b-4eeb-b8e9-9cb4e91bc3fd"})
    void uuidToBytesAndBack(final String uuidString) {
        final UUID uuid = UUID.fromString(uuidString);
        final byte[] uuidBytes = UUIDUtilities.getBytesFromUUID(uuid);
        final UUID reConvertedUUID = UUIDUtilities.getUUIDFromBytes(uuidBytes);
        assertThat(uuid).isEqualTo(reConvertedUUID);
    }
}