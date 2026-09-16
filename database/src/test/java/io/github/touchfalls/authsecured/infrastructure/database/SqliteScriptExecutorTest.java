package io.github.touchfalls.authsecured.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteScriptExecutorTest {
    @TempDir Path directory;

    @Test
    void executesQuotedSemicolonsAndTriggerBodies() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("script.db"))) {
            SqliteScriptExecutor.execute(connection, """
                    CREATE TABLE source (value TEXT);
                    CREATE TABLE history (value TEXT);
                    CREATE TRIGGER remember AFTER INSERT ON source BEGIN
                        INSERT INTO history(value) VALUES (new.value);
                    END;
                    INSERT INTO source(value) VALUES ('before;after');
                    """);
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT value FROM history")) {
                assertTrue(rows.next());
                assertEquals("before;after", rows.getString(1));
            }
        }
    }
}
