package io.github.touchfalls.authsecured.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-neutral deadline tracker for players who have not authenticated yet.
 * Platform adapters own scheduling and disconnection; this service only owns state.
 */
public final class AuthenticationTimeoutService {

    private final ConcurrentHashMap<UUID, Instant> deadlines = new ConcurrentHashMap<>();
    private volatile boolean enabled;
    private volatile long timeoutSeconds;

    public AuthenticationTimeoutService(boolean enabled, long timeoutSeconds) {
        configure(enabled, timeoutSeconds);
    }

    public void configure(boolean enabled, long timeoutSeconds) {
        if (timeoutSeconds < 5 || timeoutSeconds > 3_600) {
            throw new IllegalArgumentException("Authentication timeout must be between 5 and 3600 seconds");
        }
        this.enabled = enabled;
        this.timeoutSeconds = timeoutSeconds;
        if (!enabled) {
            deadlines.clear();
        }
    }

    public void start(UUID playerUuid) {
        if (enabled && playerUuid != null) {
            deadlines.put(playerUuid, Instant.now().plus(Duration.ofSeconds(timeoutSeconds)));
        }
    }

    public void ensureStarted(UUID playerUuid) {
        if (enabled && playerUuid != null) {
            deadlines.putIfAbsent(playerUuid, Instant.now().plus(Duration.ofSeconds(timeoutSeconds)));
        }
    }

    public void clear(UUID playerUuid) {
        if (playerUuid != null) {
            deadlines.remove(playerUuid);
        }
    }

    public List<UUID> drainExpired(Instant now) {
        if (!enabled) {
            return List.of();
        }
        List<UUID> expired = new ArrayList<>();
        deadlines.forEach((uuid, deadline) -> {
            if (!now.isBefore(deadline) && deadlines.remove(uuid, deadline)) {
                expired.add(uuid);
            }
        });
        return expired;
    }

    public boolean isEnabled() { return enabled; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
}
