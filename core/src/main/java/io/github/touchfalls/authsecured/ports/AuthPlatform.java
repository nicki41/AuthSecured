package io.github.touchfalls.authsecured.ports;

import java.io.File;
import java.util.UUID;

/**
 * Port representing the hosting Minecraft server platform.
 */
public interface AuthPlatform {
    File getDataFolder();
    PlatformLogger getLogger();
    PlayerRestrictionAdapter getRestrictionAdapter();
    void sendMessage(UUID playerUuid, String message);
    void runTaskOnMainThread(Runnable task);
    boolean isOnlineMode();
    boolean isPlayerOnline(UUID playerUuid);

    /**
     * Opaque identity of the player's current connection, distinct even when a
     * player with the same UUID disconnects and reconnects while an earlier
     * asynchronous result (for example Argon2 password hashing) is still
     * pending. Null if the player is not currently connected.
     */
    Object currentConnectionToken(UUID playerUuid);
    void disconnectPlayer(UUID playerUuid, String reason);
}
