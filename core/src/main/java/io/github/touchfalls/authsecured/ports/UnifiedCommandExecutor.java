package io.github.touchfalls.authsecured.ports;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified entrypoint for authentication commands across all platforms.
 */
public interface UnifiedCommandExecutor {
    CompletableFuture<Void> executeRegister(UUID playerUuid, String playerName, char[] password, char[] confirmPassword, String ipAddress);
    CompletableFuture<Void> executeLogin(UUID playerUuid, String playerName, char[] password, String ipAddress);
    CompletableFuture<Void> executeChangePassword(UUID playerUuid, char[] oldPassword, char[] newPassword, String ipAddress);

    /** Same as {@link #executeChangePassword(UUID, char[], char[], String)} with the player name for rate limits. */
    default CompletableFuture<Void> executeChangePassword(UUID playerUuid, String playerName, char[] oldPassword,
                                                         char[] newPassword, String ipAddress) {
        return executeChangePassword(playerUuid, oldPassword, newPassword, ipAddress);
    }
    CompletableFuture<Void> executeLogout(UUID playerUuid);
    void executeAuthStatus(UUID playerUuid);
}
