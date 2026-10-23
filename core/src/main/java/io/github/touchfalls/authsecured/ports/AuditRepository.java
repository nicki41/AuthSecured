package io.github.touchfalls.authsecured.ports;

import io.github.touchfalls.authsecured.domain.auth.LoginAttempt;
import io.github.touchfalls.authsecured.domain.security.SecurityEvent;
import io.github.touchfalls.authsecured.domain.security.AuditEntry;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AuditRepository {
    CompletableFuture<Void> recordLoginAttempt(LoginAttempt attempt);
    CompletableFuture<Void> recordSecurityEvent(SecurityEvent event);
    CompletableFuture<Void> purgeOldRecords(int loginAttemptsDays, int securityEventsDays);
    CompletableFuture<List<AuditEntry>> findRecent(int limit, String usernameNormalized);
}
