package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.ports.AuthPlatform;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisRequiredTest {
    @TempDir Path directory;

    private DependencyContainer container(boolean required) throws Exception {
        Path data = Files.createDirectory(directory.resolve(required ? "required" : "optional"));
        Files.writeString(data.resolve("config.yml"), """
                language: en
                security:
                  ip-hashing:
                    secret-env: "private-test-secret"
                redis:
                  enabled: true
                  required: %s
                  host: "127.0.0.1"
                  port: 1
                """.formatted(required));
        AuthPlatform platform = mock(AuthPlatform.class);
        when(platform.getDataFolder()).thenReturn(data.toFile());
        when(platform.getLogger()).thenReturn(mock(PlatformLogger.class));
        return new DependencyContainer(platform);
    }

    @Test
    void requiredRedisStopsInitializationWhenUnavailable() throws Exception {
        DependencyContainer service = container(true);
        try {
            assertThrows(IllegalStateException.class, service::init);
        } finally {
            service.close();
        }
    }

    @Test
    void optionalRedisFallbackAppearsInDoctor() throws Exception {
        DependencyContainer service = container(false);
        try {
            service.init();
            assertEquals("memory-fallback", service.getRateLimitBackend());
            assertTrue(new AdminOperations(service).doctor().join().stream()
                    .anyMatch(line -> line.contains("Redis is unavailable")));
        } finally {
            service.close();
        }
    }
}
