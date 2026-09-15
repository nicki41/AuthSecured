package com.example.authsecured;

import com.example.authsecured.domain.account.Account;
import com.example.authsecured.infrastructure.database.DatabaseManager;
import com.example.authsecured.infrastructure.database.DatabaseAuditRepository;
import com.example.authsecured.infrastructure.database.SqliteAccountRepository;
import com.example.authsecured.domain.auth.LoginAttempt;
import com.example.authsecured.domain.security.SecurityEvent;
import com.example.authsecured.domain.security.SecurityEventType;
import com.example.authsecured.ports.PlatformLogger;
import com.example.authsecured.ports.DuplicateAccountException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteAccountRepositoryTest {

    private DatabaseManager databaseManager;
    private SqliteAccountRepository repository;
    private static final PlatformLogger LOGGER = new PlatformLogger() {
        @Override public void info(String message) { }
        @Override public void warning(String message) { }
        @Override public void severe(String message) { }
        @Override public void severe(String message, Throwable throwable) { }
    };

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("test_auth.db").toFile();
        databaseManager = new DatabaseManager(LOGGER, "sqlite", "", 0, "", "", "",
                dbFile.getAbsolutePath(), 2);
        repository = new SqliteAccountRepository(databaseManager.getDataSource(),
                databaseManager.getDbExecutor());
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    void testSaveAndFindByUuid() {
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "TestUser", "testuser", "$argon2id$dummyhash");

        Account saved = repository.save(account).join();
        assertNotNull(saved.getId());

        Optional<Account> found = repository.findByUuid(uuid).join();
        assertTrue(found.isPresent());
        assertEquals("TestUser", found.get().getUsername());
        assertEquals("testuser", found.get().getUsernameNormalized());
    }

    @Test
    void persistsTemporaryPasswordState() {
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "ResetUser", "resetuser", "$argon2id$temporary");
        Instant expiry = Instant.now().plusSeconds(900);
        account.setTemporaryPassword("$argon2id$temporary", expiry);

        repository.save(account).join();
        Account restored = repository.findByUuid(uuid).join().orElseThrow();

        assertTrue(restored.isPasswordChangeRequired());
        assertEquals(expiry, restored.getTemporaryPasswordExpiresAt());
    }

    @Test
    void rejectsDuplicateUsernameWithSpecificException() {
        repository.save(Account.createNew(UUID.randomUUID(), "Player", "player", "hash")).join();

        java.util.concurrent.CompletionException error = assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> repository.save(Account.createNew(
                        UUID.randomUUID(), "PLAYER", "player", "hash")).join());

        assertTrue(error.getCause() instanceof DuplicateAccountException);
    }

    @Test
    void failedAttemptsAreUpdatedAtomically() {
        Account saved = repository.save(Account.createNew(
                UUID.randomUUID(), "AtomicUser", "atomicuser", "hash")).join();
        Instant now = Instant.now();
        java.util.List<java.util.concurrent.CompletableFuture<Boolean>> updates =
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(ignored -> repository.incrementFailedAttemptsAtomic(
                                saved.getId(), 100, 900, now))
                        .toList();
        updates.forEach(java.util.concurrent.CompletableFuture::join);

        Account updated = repository.findByUuid(saved.getUuid()).join().orElseThrow();
        assertEquals(20, updated.getFailedLoginAttempts());
        assertTrue(repository.resetFailedAttemptsAtomic(saved.getId(), now.plusSeconds(1)).join());
        assertEquals(0, repository.findByUuid(saved.getUuid()).join().orElseThrow().getFailedLoginAttempts());
    }

    @Test
    void upgradesLegacySqliteAccountsTable(@TempDir Path legacyDir) throws Exception {
        File legacyFile = legacyDir.resolve("legacy.db").toFile();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + legacyFile);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "uuid TEXT NOT NULL UNIQUE, username TEXT NOT NULL, username_normalized TEXT NOT NULL UNIQUE, "
                    + "password_hash TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'ACTIVE', "
                    + "failed_login_attempts INTEGER NOT NULL DEFAULT 0, locked_until TEXT NULL, "
                    + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, last_login_at TEXT NULL, "
                    + "password_changed_at TEXT NOT NULL)");
        }

        DatabaseManager legacyManager = new DatabaseManager(LOGGER, "sqlite", "", 0, "", "", "",
                legacyFile.getAbsolutePath(), 2);
        try (Connection connection = legacyManager.getDataSource().getConnection();
             ResultSet columns = connection.getMetaData().getColumns(
                     null, null, "accounts", "password_change_required")) {
            assertTrue(columns.next());
        } finally {
            legacyManager.close();
        }
    }

    @Test
    void returnsRecentPrivacySafeAuditEntries() {
        Account saved = repository.save(Account.createNew(
                UUID.randomUUID(), "AuditUser", "audituser", "$argon2id$dummyhash")).join();
        DatabaseAuditRepository audit = new DatabaseAuditRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), false, LOGGER);
        audit.recordLoginAttempt(new LoginAttempt(
                null, saved.getId(), new byte[]{1, 2}, false, "INVALID_PASSWORD", Instant.now())).join();
        audit.recordSecurityEvent(new SecurityEvent(
                null, saved.getId(), SecurityEventType.ACCOUNT_UNLOCKED,
                new byte[]{1, 2}, "{}", Instant.now().plusMillis(1))).join();

        var entries = audit.findRecent(10, "audituser").join();

        assertEquals(2, entries.size());
        assertEquals("AuditUser", entries.get(0).username());
        assertEquals("ACCOUNT_UNLOCKED", entries.get(0).event());
        assertEquals("INVALID_PASSWORD", entries.get(1).reason());
        assertEquals(0, databaseManager.getActiveConnections());
    }
}
