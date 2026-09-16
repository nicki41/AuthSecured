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
        SecurityEvent event = new SecurityEvent(null, accountId, eventType, ipHash, metadataJson, Instant.now());
        return auditRepository.recordSecurityEvent(event);
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
