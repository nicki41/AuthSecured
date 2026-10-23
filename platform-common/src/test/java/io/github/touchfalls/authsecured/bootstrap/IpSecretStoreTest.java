package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class IpSecretStoreTest {
    @TempDir Path dataDirectory;
    private final PlatformLogger logger = mock(PlatformLogger.class);

    @Test
    void generatesPersistentSecretWithOwnerOnlyPermissions() throws Exception {
        String first = IpSecretStore.loadOrCreate(dataDirectory.toFile(), "", logger).value();
        IpSecretStore.Secret second = IpSecretStore.loadOrCreate(dataDirectory.toFile(), null, logger);
        Path file = dataDirectory.resolve("ip-secret.key");

        assertEquals(64, first.length());
        assertEquals(first, second.value());
        assertFalse(second.fromEnvironment());
        assertEquals(first, Files.readString(file).trim());
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        }
    }

    @Test
    void existingFileCanBeChangedAndEnvironmentSecretOverridesIt() throws Exception {
        Path file = dataDirectory.resolve("ip-secret.key");
        Files.writeString(file, "first-private-secret\n");
        assertEquals("first-private-secret",
                IpSecretStore.loadOrCreate(dataDirectory.toFile(), "", logger).value());
        Files.writeString(file, "replacement-private-secret\n");
        assertEquals("replacement-private-secret",
                IpSecretStore.loadOrCreate(dataDirectory.toFile(), "", logger).value());
        IpSecretStore.Secret fromEnv =
                IpSecretStore.loadOrCreate(dataDirectory.toFile(), "environment-private-secret", logger);
        assertEquals("environment-private-secret", fromEnv.value());
        assertTrue(fromEnv.fromEnvironment());
        assertEquals("replacement-private-secret", Files.readString(file).trim());
    }

    @Test
    void rejectsEmptySecretFile() throws Exception {
        Files.createFile(dataDirectory.resolve("ip-secret.key"));
        assertThrows(IllegalStateException.class,
                () -> IpSecretStore.loadOrCreate(dataDirectory.toFile(), "", logger));
    }

    @Test
    void rejectsSymlinkSecretFile() throws Exception {
        Path target = dataDirectory.resolve("target");
        Files.writeString(target, "private-secret");
        Files.createSymbolicLink(dataDirectory.resolve("ip-secret.key"), target);
        assertThrows(IllegalStateException.class,
                () -> IpSecretStore.loadOrCreate(dataDirectory.toFile(), "", logger));
    }
}
