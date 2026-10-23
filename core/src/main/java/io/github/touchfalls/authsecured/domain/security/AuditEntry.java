package io.github.touchfalls.authsecured.domain.security;

import java.time.Instant;

/** Privacy-safe projection used by administrative audit views. */
public record AuditEntry(
        Instant createdAt,
        String category,
        String event,
        String username,
        Boolean successful,
        String reason
) {
}
