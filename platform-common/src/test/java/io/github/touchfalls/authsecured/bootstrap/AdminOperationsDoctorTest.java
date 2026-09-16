package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.application.SecurityService;
import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseManager;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AdminOperationsDoctorTest {
    @TempDir Path directory;

    @Test
    void missingSecretIsAnExplicitDoctorFailure() {
        ConfigManager config = new ConfigManager(directory.toFile(), mock(PlatformLogger.class));
        assertTrue(config.loadConfigurations());
        DependencyContainer container = mock(DependencyContainer.class);
        DatabaseManager database = mock(DatabaseManager.class);
        SecurityService security = mock(SecurityService.class);
        when(container.getConfigManager()).thenReturn(config);
        when(container.getDatabaseManager()).thenReturn(database);
        when(container.getSecurityService()).thenReturn(security);
        when(container.getRateLimitBackend()).thenReturn("memory");
        when(database.ping()).thenReturn(CompletableFuture.completedFuture(true));
        when(security.isAuditEnabled()).thenReturn(true);
        when(container.isIpHashSecretConfigured()).thenReturn(false);

        var lines = new AdminOperations(container).doctor().join();
        assertTrue(lines.stream().anyMatch(line -> line.contains("[FAIL]")
                && line.contains("AUTHSECURED_IP_SECRET")));
    }
}
