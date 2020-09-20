package de.timmi6790.mineplexleaderboardupdate;

import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container
    private static final MariaDBContainer MARIA_DB_CONTAINER = (MariaDBContainer) new MariaDBContainer().withClasspathResourceMapping(
            "tables.sql",
            "/docker-entrypoint-initdb.d/createTables.sql",
            BindMode.READ_ONLY
    );

    public static Jdbi getDatabase() {
        System.out.println(MARIA_DB_CONTAINER.getJdbcUrl() + " " + MARIA_DB_CONTAINER.getUsername() + " " + MARIA_DB_CONTAINER.getPassword());

        return Jdbi.create(MARIA_DB_CONTAINER.getJdbcUrl(), MARIA_DB_CONTAINER.getUsername(), MARIA_DB_CONTAINER.getPassword());
    }

    static {
        MARIA_DB_CONTAINER.start();
    }
}