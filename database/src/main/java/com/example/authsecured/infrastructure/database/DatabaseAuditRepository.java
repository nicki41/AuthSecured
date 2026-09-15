package com.example.authsecured.infrastructure.database;

import com.example.authsecured.domain.auth.LoginAttempt;
import com.example.authsecured.domain.security.SecurityEvent;
import com.example.authsecured.domain.security.AuditEntry;
import com.example.authsecured.ports.AuditRepository;
import com.example.authsecured.ports.PlatformLogger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DatabaseAuditRepository implements AuditRepository {

    private final DataSource dataSource;
    private final ExecutorService executor;
    private final boolean isPostgres;
    private final PlatformLogger logger;

    public DatabaseAuditRepository(DataSource dataSource, ExecutorService executor, boolean isPostgres,
                                   PlatformLogger logger) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.isPostgres = isPostgres;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> recordLoginAttempt(LoginAttempt attempt) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO login_attempts (account_id, ip_hash, success, reason, created_at) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (attempt.getAccountId() != null) {
                    stmt.setLong(1, attempt.getAccountId());
                } else {
                    stmt.setNull(1, java.sql.Types.BIGINT);
                }
                stmt.setBytes(2, attempt.getIpHash());
                if (isPostgres) {
                    stmt.setBoolean(3, attempt.isSuccess());
                    stmt.setString(4, attempt.getReason());
                    stmt.setTimestamp(5, Timestamp.from(attempt.getCreatedAt()));
                } else {
                    stmt.setInt(3, attempt.isSuccess() ? 1 : 0);
                    stmt.setString(4, attempt.getReason());
                    stmt.setString(5, attempt.getCreatedAt().toString());
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.warning("Failed to record a login audit entry. Database details are hidden.");
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> recordSecurityEvent(SecurityEvent event) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO security_events (account_id, event_type, ip_hash, metadata, created_at) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (event.getAccountId() != null) {
                    stmt.setLong(1, event.getAccountId());
                } else {
                    stmt.setNull(1, java.sql.Types.BIGINT);
                }
                stmt.setString(2, event.getEventType().name());
                stmt.setBytes(3, event.getIpHash());
                stmt.setString(4, event.getMetadataJson());
                if (isPostgres) {
                    stmt.setTimestamp(5, Timestamp.from(event.getCreatedAt()));
                } else {
                    stmt.setString(5, event.getCreatedAt().toString());
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.warning("Failed to record a security audit entry. Database details are hidden.");
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeOldRecords(int loginAttemptsDays, int securityEventsDays) {
        return CompletableFuture.runAsync(() -> {
            Instant loginThreshold = Instant.now().minusSeconds(loginAttemptsDays * 86400L);
            Instant eventThreshold = Instant.now().minusSeconds(securityEventsDays * 86400L);

            String sqlLogin = isPostgres ?
                    "DELETE FROM login_attempts WHERE created_at < ?" :
                    "DELETE FROM login_attempts WHERE created_at < ?";
            String sqlEvent = isPostgres ?
                    "DELETE FROM security_events WHERE created_at < ?" :
                    "DELETE FROM security_events WHERE created_at < ?";

            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(sqlLogin)) {
                    if (isPostgres) stmt.setTimestamp(1, Timestamp.from(loginThreshold));
                    else stmt.setString(1, loginThreshold.toString());
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(sqlEvent)) {
                    if (isPostgres) stmt.setTimestamp(1, Timestamp.from(eventThreshold));
                    else stmt.setString(1, eventThreshold.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.warning("Failed to purge audit records. Database details are hidden.");
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<AuditEntry>> findRecent(int limit, String usernameNormalized) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String normalized = usernameNormalized == null ? "" : usernameNormalized;
        return CompletableFuture.supplyAsync(() -> {
            List<AuditEntry> entries = new ArrayList<>();
            readLoginAttempts(entries, safeLimit, normalized);
            readSecurityEvents(entries, safeLimit, normalized);
            return entries.stream()
                    .sorted(Comparator.comparing(AuditEntry::createdAt).reversed())
                    .limit(safeLimit)
                    .toList();
        }, executor);
    }

    private void readLoginAttempts(List<AuditEntry> target, int limit, String usernameNormalized) {
        String sql = "SELECT l.created_at, l.success, l.reason, a.username " +
                "FROM login_attempts l LEFT JOIN accounts a ON a.id = l.account_id " +
                (usernameNormalized.isBlank() ? "" : "WHERE a.username_normalized = ? ") +
                "ORDER BY l.created_at DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (!usernameNormalized.isBlank()) statement.setString(index++, usernameNormalized);
            statement.setInt(index, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    target.add(new AuditEntry(readInstant(result, "created_at"), "LOGIN", "LOGIN_ATTEMPT",
                            result.getString("username"), readBoolean(result, "success"),
                            result.getString("reason")));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read login audit entries", exception);
        }
    }

    private void readSecurityEvents(List<AuditEntry> target, int limit, String usernameNormalized) {
        String sql = "SELECT s.created_at, s.event_type, a.username " +
                "FROM security_events s LEFT JOIN accounts a ON a.id = s.account_id " +
                (usernameNormalized.isBlank() ? "" : "WHERE a.username_normalized = ? ") +
                "ORDER BY s.created_at DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (!usernameNormalized.isBlank()) statement.setString(index++, usernameNormalized);
            statement.setInt(index, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    target.add(new AuditEntry(readInstant(result, "created_at"), "SECURITY",
                            result.getString("event_type"), result.getString("username"), null, null));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read security audit entries", exception);
        }
    }

    private Instant readInstant(ResultSet result, String column) throws SQLException {
        if (isPostgres) {
            Timestamp timestamp = result.getTimestamp(column);
            return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
        }
        String value = result.getString(column);
        return value == null ? Instant.EPOCH : Instant.parse(value);
    }

    private Boolean readBoolean(ResultSet result, String column) throws SQLException {
        return isPostgres ? result.getBoolean(column) : result.getInt(column) != 0;
    }
}
