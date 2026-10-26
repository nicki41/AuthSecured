package io.github.touchfalls.authsecured.ports;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified entrypoint for authentication commands across all platforms.
 */
public interface UnifiedCommandExecutor {
    CompletableFuture<Void> executeRegister(UUID playerUuid, String playerName, char[] password, char[] confirmPassword, String ipAddress);
    CompletableFuture<Void> executeLogin(UUID playerUuid, String playerName, char[] password, String ipAddress);
    /** The player name is used for the account rate limit, the same key {@code /login} uses. */
    CompletableFuture<Void> executeChangePassword(UUID playerUuid, String playerName, char[] oldPassword,
                                                  char[] newPassword, String ipAddress);
    CompletableFuture<Void> executeLogout(UUID playerUuid);
    void executeAuthStatus(UUID playerUuid);

    /**
     * Returns an online player to the pre-login state after an administrator invalidated
     * their credentials, so a connection that authenticated earlier cannot outlive them.
     * Does nothing if the player is offline.
     */
    void revokeAuthentication(UUID playerUuid, String messageKey, String fallbackMessage);
}
