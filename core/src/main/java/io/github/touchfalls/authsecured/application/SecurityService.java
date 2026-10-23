package io.github.touchfalls.authsecured.application;

import io.github.touchfalls.authsecured.domain.auth.LoginAttempt;
import io.github.touchfalls.authsecured.domain.security.SecurityEvent;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.domain.security.AuditEntry;
import io.github.touchfalls.authsecured.ports.AuditRepository;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.List;

public class SecurityService {

    private final AuditRepository auditRepository;
    private final boolean auditEnabled;

    public SecurityService(AuditRepository auditRepository, boolean auditEnabled) {
        this.auditRepository = auditRepository;
        this.auditEnabled = auditEnabled;
    }

    public CompletableFuture<Void> logLoginAttempt(Long accountId, byte[] ipHash, boolean success, String reason) {
        if (!auditEnabled) return CompletableFuture.completedFuture(null);
        LoginAttempt attempt = new LoginAttempt(null, accountId, ipHash, success, reason, Instant.now());
        return auditRepository.recordLoginAttempt(attempt);
    }

    public CompletableFuture<Void> logSecurityEvent(Long accountId, SecurityEventType eventType, byte[] ipHash, String metadataJson) {
        if (!auditEnabled) return CompletableFuture.completedFuture(null);
        SecurityEvent event = new SecurityEvent(null, accountId, eventType, ipHash,
                normalizeMetadata(metadataJson), Instant.now());
        return auditRepository.recordSecurityEvent(event);
    }

    /**
     * Metadata is stored as JSON (JSONB on PostgreSQL). Plain text from older callers
     * is wrapped into a JSON object instead of failing the insert.
     */
    static String normalizeMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return "{}";
        String trimmed = metadata.strip();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed;
        StringBuilder escaped = new StringBuilder(trimmed.length() + 16).append("{\"detail\":\"");
        for (char c : trimmed.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (c < 0x20) escaped.append(String.format("\\u%04x", (int) c));
                    else escaped.append(c);
                }
            }
        }
        return escaped.append("\"}").toString();
    }

    public CompletableFuture<Void> purgeAuditLogs(int loginAttemptsDays, int securityEventsDays) {
        return auditRepository.purgeOldRecords(loginAttemptsDays, securityEventsDays);
    }

    public CompletableFuture<List<AuditEntry>> findRecentAuditEntries(int limit, String usernameNormalized) {
        if (!auditEnabled) {
            return CompletableFuture.completedFuture(List.of());
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return auditRepository.findRecent(safeLimit, usernameNormalized);
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }
}
