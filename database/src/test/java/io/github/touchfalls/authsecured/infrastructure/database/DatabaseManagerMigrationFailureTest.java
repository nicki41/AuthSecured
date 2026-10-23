package io.github.touchfalls.authsecured.infrastructure.database;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerMigrationFailureTest {

    private static final PlatformLogger LOGGER = new PlatformLogger() {
        @Override public void info(String message) { }
        @Override public void warning(String message) { }
        @Override public void severe(String message) { }
        @Override public void severe(String message, Throwable throwable) { }
    };

    @Test
    void failedMigrationClosesThePoolInsteadOfLeakingIt(@TempDir Path directory) throws Exception {
        String path = directory.resolve("future-schema.db").toString();
        // A schema version newer than this build understands makes runMigrations() throw
        // from inside the constructor, every time, without touching HikariCP at all.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE authsecured_schema_version (id INTEGER PRIMARY KEY CHECK(id = 1), version INTEGER NOT NULL)");
            statement.execute("INSERT INTO authsecured_schema_version(id, version) VALUES (1, 99)");
        }

        assertThrows(RuntimeException.class, () ->
                new DatabaseManager(LOGGER, "sqlite", "", 0, "", "", "", path, 2));

        // Without the fix, the pool's housekeeper and connection-adder threads for this
        // name keep running forever; nothing ever called close() on the leaked pool.
        boolean poolThreadGone = waitUntil(() -> Thread.getAllStackTraces().keySet().stream()
                .noneMatch(thread -> thread.getName().contains("AuthSecured-DB-Pool")));
        assertTrue(poolThreadGone, "the connection pool must be closed, not leaked, when migration fails");
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }
}
