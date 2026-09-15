package com.example.authsecured.application;

import com.example.authsecured.domain.session.Session;
import com.example.authsecured.ports.SessionRepository;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class SessionService {

    private final SessionRepository sessionRepository;
    private final boolean enabled;
    private final boolean persistent;
    private final AtomicLong timeoutSeconds;
    private final boolean verifyIp;
    private final CompletableFuture<Void> initialization;
    private final Map<UUID, Session> volatileSessions = new ConcurrentHashMap<>();

    public SessionService(SessionRepository sessionRepository, boolean persistent, long timeoutMinutes) {
        this(sessionRepository, true, persistent, timeoutMinutes * 60L, false);
    }

    public SessionService(SessionRepository sessionRepository, boolean enabled, boolean persistent, long timeoutSeconds, boolean verifyIp) {
        this.sessionRepository = sessionRepository;
        this.enabled = enabled;
        this.persistent = persistent;
        this.timeoutSeconds = new AtomicLong(validateTimeout(timeoutSeconds));
        this.verifyIp = verifyIp;
        this.initialization = persistent
                ? CompletableFuture.completedFuture(null)
                : sessionRepository.deleteAll();
    }

    public CompletableFuture<Void> initialize() {
        return initialization;
    }

    public CompletableFuture<Session> createSession(Long accountId, UUID playerUuid, String serverId) {
        return createSession(accountId, playerUuid, null, serverId);
    }

    public CompletableFuture<Session> createSession(Long accountId, UUID playerUuid, byte[] ipHash,
                                                    String serverId) {
        Session session = Session.create(accountId, playerUuid, ipHash, serverId, timeoutSeconds.get());
        if (!enabled) {
            return CompletableFuture.completedFuture(session);
        }
        if (!persistent) {
            return initialization.thenApply(ignored -> {
                volatileSessions.put(playerUuid, session);
                return session;
            });
        }
        return sessionRepository.save(session).thenApply(v -> session);
    }

    public CompletableFuture<Boolean> isValidActiveSession(UUID playerUuid) {
        return isValidActiveSession(playerUuid, null);
    }

    public CompletableFuture<Boolean> isValidActiveSession(UUID playerUuid, byte[] playerIpHash) {
        if (!enabled) {
            return CompletableFuture.completedFuture(false);
        }
        if (!persistent) {
            return initialization.thenApply(ignored -> isValid(volatileSessions.get(playerUuid), playerIpHash));
        }
        return sessionRepository.findActiveByPlayerUuid(playerUuid)
                .thenApply(opt -> opt.map(session -> isValid(session, playerIpHash)).orElse(false));
    }

    public CompletableFuture<Void> revokeSession(UUID sessionId) {
        if (!persistent) {
            return initialization.thenRun(() ->
                    volatileSessions.entrySet().removeIf(entry -> entry.getValue().getId().equals(sessionId)));
        }
        return sessionRepository.revoke(sessionId);
    }

    public CompletableFuture<Void> revokeAllSessionsForAccount(Long accountId) {
        if (accountId == null) return CompletableFuture.completedFuture(null);
        if (!persistent) {
            return initialization.thenRun(() -> volatileSessions.entrySet()
                    .removeIf(entry -> accountId.equals(entry.getValue().getAccountId())));
        }
        return sessionRepository.revokeAllForAccount(accountId);
    }

    public CompletableFuture<Void> revokeActiveSessionForPlayer(UUID playerUuid) {
        if (!persistent) {
            return initialization.thenRun(() -> volatileSessions.remove(playerUuid));
        }
        return sessionRepository.findActiveByPlayerUuid(playerUuid)
                .thenCompose(optSession -> {
                    if (optSession.isPresent()) {
                        return sessionRepository.revoke(optSession.get().getId());
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    public CompletableFuture<Void> cleanupExpiredSessions() {
        if (!persistent) {
            return initialization.thenRun(() -> {
                Instant now = Instant.now();
                volatileSessions.entrySet().removeIf(entry -> !entry.getValue().isValid(now));
            });
        }
        return sessionRepository.deleteExpired();
    }

    private boolean isValid(Session session, byte[] playerIpHash) {
        if (session == null || !session.isValid(Instant.now())) {
            return false;
        }
        if (!verifyIp) {
            return true;
        }
        byte[] storedIpHash = session.getTokenHash();
        return storedIpHash != null && playerIpHash != null
                && MessageDigest.isEqual(storedIpHash, playerIpHash);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isPersistent() { return persistent; }
    public long getTimeoutSeconds() { return timeoutSeconds.get(); }
    public void setTimeoutSeconds(long seconds) { timeoutSeconds.set(validateTimeout(seconds)); }
    public boolean isVerifyIp() { return verifyIp; }

    private static long validateTimeout(long seconds) {
        if (seconds < 1 || seconds > 2_592_000L) {
            throw new IllegalArgumentException("Session timeout must be between 1 and 2592000 seconds");
        }
        return seconds;
    }
}
