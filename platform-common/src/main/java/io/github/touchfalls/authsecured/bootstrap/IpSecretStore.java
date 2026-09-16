package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.ports.PlatformLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.HexFormat;

/** Loads an explicit override or persists a unique, owner-readable IP hashing secret. */
final class IpSecretStore {
    private static final String FILE_NAME = "ip-secret.key";
    private static final String OWNER_ONLY = "rw-------";

    private IpSecretStore() {}

    static String loadOrCreate(File dataFolder, String configuredSecret, PlatformLogger logger) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            logger.warning("AUTHSECURED_IP_SECRET overrides ip-secret.key. Changing the secret invalidates "
                    + "IP-bound sessions and rate-limit identities.");
            return configuredSecret;
        }

        Path path = dataFolder.toPath().resolve(FILE_NAME);
        try {
            Files.createDirectories(dataFolder.toPath());
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                String generated = HexFormat.of().formatHex(random);
                try {
                    if (Files.getFileAttributeView(dataFolder.toPath(), PosixFileAttributeView.class) != null) {
                        Files.createFile(path, PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString(OWNER_ONLY)));
                    } else {
                        Files.createFile(path);
                    }
                    Files.writeString(path, generated + "\n", StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    logger.warning("Generated ip-secret.key in the AuthSecured data directory. Back it up. "
                            + "Changing it invalidates IP-bound sessions and rate-limit identities.");
                } catch (FileAlreadyExistsException ignored) {
                    // Another startup created the file. Read its value below.
                } finally {
                    java.util.Arrays.fill(random, (byte) 0);
                }
            }
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("ip-secret.key must be a regular file, not a link");
            }
            if (Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(OWNER_ONLY));
            } else {
                File file = path.toFile();
                if (!file.setReadable(false, false) || !file.setWritable(false, false)
                        || !file.setReadable(true, true) || !file.setWritable(true, true)) {
                    throw new IllegalStateException("Cannot restrict ip-secret.key to its owner");
                }
            }
            String secret = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (secret.isBlank()) {
                throw new IllegalStateException("ip-secret.key is empty; set a private secret in this file");
            }
            return secret;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load or create private ip-secret.key", exception);
        }
    }
}
