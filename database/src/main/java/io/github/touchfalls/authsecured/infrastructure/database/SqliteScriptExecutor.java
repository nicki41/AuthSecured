package io.github.touchfalls.authsecured.infrastructure.database;

import org.sqlite.SQLiteConnection;

import java.sql.Connection;
import java.sql.SQLException;

/** Uses SQLite's native script executor, which understands quoted semicolons and triggers. */
final class SqliteScriptExecutor {
    private SqliteScriptExecutor() {}

    static void execute(Connection connection, String sql) throws SQLException {
        int result = connection.unwrap(SQLiteConnection.class).getDatabase()._exec(sql);
        if (result != 0) throw new SQLException("SQLite migration script failed with code " + result);
    }
}
