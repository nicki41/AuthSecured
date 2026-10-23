package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @TempDir
    File tempFolder;

    private ConfigManager configManager;
    private static final PlatformLogger DUMMY_LOGGER = new PlatformLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
        @Override public void severe(String message, Throwable throwable) {}
    };

    @BeforeEach
    void setUp() {
        configManager = new ConfigManager(tempFolder, DUMMY_LOGGER);
        configManager.loadConfigurations();
    }

    @Test
    @DisplayName("Verify config.yml creation and default value reading")
    void testConfigDefaults() {
        assertTrue(new File(tempFolder, "config.yml").exists());

        assertEquals("sqlite", configManager.getString("database.type", "sqlite"));
        assertEquals(65536, configManager.getInt("security.password.argon2.memory-kib", 65536));
        assertTrue(configManager.getBoolean("session.enabled", true));
        assertTrue(configManager.getBoolean("auth-timeout.enabled", false));
        assertEquals(60, configManager.getInt("auth-timeout.timeout-seconds", 0));
        assertEquals(18, configManager.getInt("admin.temporary-password.length", 0));
        assertFalse(configManager.getBoolean("notifications.login-failures.enabled", true));
        assertEquals(30, configManager.getInt("logging.audit.retention-days", 0));
        assertEquals(3600, configManager.getInt("logging.audit.cleanup-interval-seconds", 0));

        List<String> allowedCommands = configManager.getStringList(
                "restrictions.allowed-commands", List.of("/fallback"));
        assertNotNull(allowedCommands);
        assertTrue(allowedCommands.contains("/login"));
    }

    @Test
    @DisplayName("Verify fallback values for missing config paths")
    void testMissingPathFallback() {
        assertEquals("default_val", configManager.getString("nonexistent.path", "default_val"));
        assertEquals(999, configManager.getInt("nonexistent.int", 999));
        assertFalse(configManager.getBoolean("nonexistent.bool", false));
    }

    @Test
    @DisplayName("Invalid numeric settings use safe bounded defaults")
    void testBoundedIntegerFallback() throws Exception {
        Files.writeString(new File(tempFolder, "config.yml").toPath(), "database:\n  port: 99999\n");
        configManager.loadConfigurations();

        assertEquals(5432, configManager.getInt("database.port", 5432, 1, 65535));
    }

    @Test
    @DisplayName("A broken reload keeps the last valid configuration")
    void testBrokenReloadKeepsLastValidSnapshot() throws Exception {
        File configFile = new File(tempFolder, "config.yml");
        Files.writeString(configFile.toPath(), "language: ru\nrestrictions:\n  freeze-player: false\n");
        configManager.loadConfigurations();
        assertFalse(configManager.getBoolean("restrictions.freeze-player", true));

        Files.writeString(configFile.toPath(), "language: [broken\n");
        configManager.loadConfigurations();

        assertFalse(configManager.getBoolean("restrictions.freeze-player", true));
        assertEquals("ru", configManager.getLocalizationManager().getCurrentLanguage());
    }

    @Test
    void oldConfigReceivesDocumentedNotificationSettings() throws Exception {
        File configFile = new File(tempFolder, "config.yml");
        Files.writeString(configFile.toPath(), "language: en\nsession:\n  timeout-seconds: 120\n");
        ConfigManager migrated = new ConfigManager(tempFolder, DUMMY_LOGGER);

        assertTrue(migrated.loadConfigurations());
        String text = Files.readString(configFile.toPath());
        assertTrue(text.contains("notifications:"));
        assertTrue(text.contains("webhook-url:"));
        assertTrue(text.contains("bot-token:"));
        assertFalse(migrated.getBoolean("notifications.discord.enabled", true));
    }

    @Test
    void invalidValidatedReloadKeepsPreviousSnapshot() throws Exception {
        int original = configManager.getInt("session.timeout-seconds", 0);
        Files.writeString(new File(tempFolder, "config.yml").toPath(),
                "language: en\nsession:\n  timeout-seconds: 0\n");

        assertFalse(configManager.loadConfigurations());
        assertEquals(original, configManager.getInt("session.timeout-seconds", 0));
    }

    @Test
    void rejectsInvalidAuditRetention() throws Exception {
        Files.writeString(new File(tempFolder, "config.yml").toPath(),
                "logging:\n  audit:\n    retention-days: 0\n");
        assertFalse(configManager.loadConfigurations());
        assertEquals(30, configManager.getInt("logging.audit.retention-days", 0));
    }
}
