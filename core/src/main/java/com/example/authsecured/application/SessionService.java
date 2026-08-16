package com.example.authsecured.application;

import com.example.authsecured.domain.session.Session;
import com.example.authsecured.ports.SessionRepository;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class SessionService {

    private final SessionRepository sessionRepository;
    private final boolean enabled;
    private final boolean persistent;
    private final long timeoutSeconds;
    private final boolean verifyIp;
    private final Map<UUID, Session> volatileSessions = new ConcurrentHashMap<>();

    public SessionService(SessionRepository sessionRepository, boolean persistent, long timeoutMinutes) {
        this(sessionRepository, true, persistent, timeoutMinutes * 60L, false);
    }

    public SessionService(SessionRepository sessionRepository, boolean enabled, boolean persistent, long timeoutSeconds, boolean verifyIp) {
        this.sessionRepository = sessionRepository;
        this.enabled = enabled;
        this.persistent = persistent;
        this.timeoutSeconds = timeoutSeconds;
        this.verifyIp = verifyIp;
    }

    public CompletableFuture<Session> createSession(Long accountId, UUID playerUuid, String serverId) {
        return createSession(accountId, playerUuid, null, serverId);
    }

    public CompletableFuture<Session> createSession(Long accountId, UUID playerUuid, byte[] ipHash,
                                                    String serverId) {
        Session session = Session.create(accountId, playerUuid, ipHash, serverId, timeoutSeconds);
        if (!enabled) {
            return CompletableFuture.completedFuture(session);
        }
        if (!persistent) {
            volatileSessions.put(playerUuid, session);
            return CompletableFuture.completedFuture(session);
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
            return CompletableFuture.completedFuture(isValid(volatileSessions.get(playerUuid), playerIpHash));
        }
        return sessionRepository.findActiveByPlayerUuid(playerUuid)
                .thenApply(opt -> opt.map(session -> isValid(session, playerIpHash)).orElse(false));
    }

    public CompletableFuture<Void> revokeSession(UUID sessionId) {
        if (!persistent) {
            volatileSessions.entrySet().removeIf(entry -> entry.getValue().getId().equals(sessionId));
            return CompletableFuture.completedFuture(null);
        }
        return sessionRepository.revoke(sessionId);
    }

    public CompletableFuture<Void> revokeAllSessionsForAccount(Long accountId) {
        if (accountId == null) return CompletableFuture.completedFuture(null);
        if (!persistent) {
            volatileSessions.entrySet().removeIf(entry -> accountId.equals(entry.getValue().getAccountId()));
            return CompletableFuture.completedFuture(null);
        }
        return sessionRepository.revokeAllForAccount(accountId);
    }

    public CompletableFuture<Void> revokeActiveSessionForPlayer(UUID playerUuid) {
        if (!persistent) {
            volatileSessions.remove(playerUuid);
            return CompletableFuture.completedFuture(null);
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
            Instant now = Instant.now();
            volatileSessions.entrySet().removeIf(entry -> !entry.getValue().isValid(now));
            return CompletableFuture.completedFuture(null);
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
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public boolean isVerifyIp() { return verifyIp; }
}
