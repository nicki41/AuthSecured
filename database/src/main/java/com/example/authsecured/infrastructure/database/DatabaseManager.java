package com.example.authsecured.infrastructure.database;

import com.example.authsecured.ports.PlatformLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class DatabaseManager {

    private final PlatformLogger logger;
    private final String dbType;
    private HikariDataSource dataSource;
    private final ExecutorService dbExecutor;

    public DatabaseManager(PlatformLogger logger, String dbType, String host, int port, String database,
                           String username, String password, String sqlitePath, int poolSize) {
        this(logger, dbType, host, port, database, username, password, sqlitePath, poolSize, "prefer");
    }

    public DatabaseManager(PlatformLogger logger, String dbType, String host, int port, String database,
                           String username, String password, String sqlitePath, int poolSize,
                           String sslMode) {
        this.logger = logger;
        this.dbType = dbType.toLowerCase(Locale.ROOT);
        this.dbExecutor = Executors.newFixedThreadPool(Math.max(2, poolSize));
        initDataSource(host, port, database, username, password, sqlitePath, poolSize, sslMode);
    }

    private void initDataSource(String host, int port, String database, String username,
                                String password, String sqlitePath, int poolSize, String sslMode) {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(poolSize);
        config.setPoolName("AuthSecured-DB-Pool");

        if ("postgresql".equals(dbType) || "postgres".equals(dbType)) {
            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, database));
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
            config.addDataSourceProperty("sslmode", normalizeSslMode(sslMode));
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            config.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setConnectionTestQuery("SELECT 1");
        }

        this.dataSource = new HikariDataSource(config);
        runMigrations();
    }

    private String normalizeSslMode(String sslMode) {
        String normalized = sslMode == null ? "prefer" : sslMode.toLowerCase(Locale.ROOT);
        if (Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full")
                .contains(normalized)) {
            return normalized;
        }
        logger.warning("Unsupported PostgreSQL ssl-mode '" + sslMode + "'; using prefer.");
        return "prefer";
    }

    public void runMigrations() {
        try {
            if ("postgresql".equals(dbType) || "postgres".equals(dbType)) {
                Flyway flyway = Flyway.configure(DatabaseManager.class.getClassLoader())
                        .dataSource(dataSource)
                        .locations("classpath:db/migration/postgresql")
                        .load();
                flyway.migrate();
                logger.info("PostgreSQL database migrations applied successfully via Flyway.");
            } else {
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    InputStream is = getClass().getResourceAsStream("/db/migration/V1__initial_sqlite_schema.sql");
                    if (is != null) {
                        String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                                .lines().collect(Collectors.joining("\n"));
                        for (String query : sql.split(";")) {
                            if (!query.trim().isEmpty()) {
                                stmt.execute(query.trim());
                            }
                        }
                        applyAccountSecurityColumnsSqlite(conn);
                        logger.info("SQLite database schema initialized successfully.");
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Database migration failed. Connection details are hidden.");
            throw new RuntimeException("Failed to run database migrations", e);
        }
    }

    /** Adds columns introduced after the first public schema without breaking existing SQLite installs. */
    private void applyAccountSecurityColumnsSqlite(Connection connection) throws Exception {
        if (!hasColumn(connection, "accounts", "password_change_required")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE accounts ADD COLUMN password_change_required INTEGER NOT NULL DEFAULT 0");
            }
        }
        if (!hasColumn(connection, "accounts", "temporary_password_expires_at")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE accounts ADD COLUMN temporary_password_expires_at TEXT NULL");
            }
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (var columns = metadata.getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public ExecutorService getDbExecutor() {
        return dbExecutor;
    }

    public boolean isPostgres() {
        return "postgresql".equals(dbType) || "postgres".equals(dbType);
    }

    public CompletableFuture<Boolean> ping() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return true;
            } catch (Exception exception) {
                return false;
            }
        }, dbExecutor);
    }

    public String getDatabaseType() {
        return isPostgres() ? "postgresql" : "sqlite";
    }

    public int getActiveConnections() {
        return dataSource.getHikariPoolMXBean() == null ? -1
                : dataSource.getHikariPoolMXBean().getActiveConnections();
    }

    public int getIdleConnections() {
        return dataSource.getHikariPoolMXBean() == null ? -1
                : dataSource.getHikariPoolMXBean().getIdleConnections();
    }

    public void close() {
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
