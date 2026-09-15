package com.example.authsecured.bootstrap;

import com.example.authsecured.domain.auth.TemporaryPasswordReset;
import com.example.authsecured.domain.security.AuditEntry;
import com.example.authsecured.util.UsernameNormalizer;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Shared implementation behind the Paper and Fabric administrative command adapters. */
public final class AdminOperations {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DependencyContainer container;

    public AdminOperations(DependencyContainer container) {
        this.container = container;
    }

    public long getSessionTimeoutSeconds() {
        return container.getSessionService().getTimeoutSeconds();
    }

    public void setSessionTimeoutSeconds(long seconds) {
        container.getSessionService().setTimeoutSeconds(seconds);
    }

    public CompletableFuture<List<String>> status() {
        return container.getDatabaseManager().ping().thenApply(databaseUp -> {
            String disabled = message("messages.admin-state-disabled", "disabled");
            String databaseState = databaseUp
                    ? message("messages.admin-state-online", "online")
                    : message("messages.admin-state-unavailable", "unavailable");
            String backend = container.getRateLimitBackend();
            String localizedBackend = message("messages.admin-backend-" + backend.toLowerCase(Locale.ROOT), backend);
            String sessionSeconds = seconds(container.getSessionService().getTimeoutSeconds());
            String sessionsState = container.getSessionService().isEnabled()
                    ? message("messages.admin-state-sessions-enabled", "enabled")
                    : message("messages.admin-state-sessions-disabled", "disabled");
            String auditState = container.getSecurityService().isAuditEnabled()
                    ? message("messages.admin-state-audit-enabled", "enabled")
                    : message("messages.admin-state-audit-disabled", "disabled");
            String authTimeout = container.getAuthenticationTimeoutService().isEnabled()
                    ? seconds(container.getAuthenticationTimeoutService().getTimeoutSeconds()) : disabled;
            return List.of(
                    message("messages.admin-status-title", "&eAuthSecured status"),
                    message("messages.admin-status-database", "&7Database: {color}{type} {state}", Map.of(
                            "color", color(databaseUp),
                            "type", container.getDatabaseManager().getDatabaseType(),
                            "state", databaseState)),
                    message("messages.admin-status-database-pool",
                            "&7Database pool: &f{active} active, {idle} idle", Map.of(
                                    "active", Integer.toString(container.getDatabaseManager().getActiveConnections()),
                                    "idle", Integer.toString(container.getDatabaseManager().getIdleConnections()))),
                    message("messages.admin-status-rate-limit", "&7Rate limiting: &f{backend}",
                            Map.of("backend", localizedBackend)),
                    message("messages.admin-status-sessions", "&7Sessions: &f{state}, {timeout}", Map.of(
                            "state", sessionsState,
                            "timeout", sessionSeconds)),
                    message("messages.admin-status-auth-timeout", "&7Authentication timeout: &f{value}",
                            Map.of("value", authTimeout)),
                    message("messages.admin-status-audit", "&7Audit: &f{state}", Map.of(
                            "state", auditState))
            );
        });
    }

    public CompletableFuture<List<String>> doctor() {
        return container.getDatabaseManager().ping().thenApply(databaseUp -> {
            List<String> lines = new ArrayList<>();
            lines.add(message("messages.admin-doctor-title", "&eAuthSecured doctor"));
            addCheck(lines, databaseUp,
                    message("messages.admin-doctor-database-ok", "Database connection"),
                    message("messages.admin-doctor-database-fail", "Database is unavailable"));
            addCheck(lines, container.isIpHashSecretConfigured(),
                    message("messages.admin-doctor-ip-secret-ok", "IP hashing secret"),
                    message("messages.admin-doctor-ip-secret-fail", "AUTHSECURED_IP_SECRET is missing"));
            boolean notificationEnabled = container.getConfigManager().getBoolean(
                    "notifications.login-failures.enabled", false);
            boolean discordTarget = container.getConfigManager().getBoolean(
                    "notifications.discord.enabled", true)
                    && hasAnyValue("notifications.discord.webhook-url",
                    "notifications.discord.webhook-url-env");
            boolean telegramTarget = container.getConfigManager().getBoolean(
                    "notifications.telegram.enabled", true)
                    && hasAnyValue("notifications.telegram.bot-token", "notifications.telegram.bot-token-env")
                    && hasAnyValue("notifications.telegram.chat-id", "notifications.telegram.chat-id-env");
            boolean notificationTarget = discordTarget || telegramTarget;
            addCheck(lines, !notificationEnabled || notificationTarget,
                    message("messages.admin-doctor-alerts-ok", "Login alerts"),
                    message("messages.admin-doctor-alerts-fail",
                            "Alerts are enabled but neither Discord nor Telegram is configured"));
            addCheck(lines, container.getSecurityService().isAuditEnabled(),
                    message("messages.admin-doctor-audit-ok", "Audit logging"),
                    message("messages.admin-doctor-audit-fail", "Audit logging is disabled"));
            lines.add(lines.stream().anyMatch(line -> line.startsWith("§c"))
                    ? message("messages.admin-doctor-problems", "&cDoctor found configuration problems.")
                    : message("messages.admin-doctor-healthy", "&aDoctor found no critical problems."));
            return List.copyOf(lines);
        });
    }

    public CompletableFuture<List<String>> audit(int limit, String username) {
        String normalized = username == null || username.isBlank()
                ? "" : UsernameNormalizer.normalize(username);
        return container.getSecurityService().findRecentAuditEntries(limit, normalized)
                .thenApply(entries -> formatAudit(entries, normalized));
    }

    public CompletableFuture<TemporaryPasswordReset> issueTemporaryPassword(String username) {
        int length = container.getConfigManager().getInt(
                "admin.temporary-password.length", 18, 12, 64);
        long expiry = container.getConfigManager().getInt(
                "admin.temporary-password.expires-seconds", 900, 60, 86_400);
        return container.getAuthService().issueTemporaryPassword(username, length, expiry);
    }

    private List<String> formatAudit(List<AuditEntry> entries, String normalizedUsername) {
        List<String> lines = new ArrayList<>();
        lines.add(normalizedUsername.isBlank()
                ? message("messages.admin-audit-title", "&eRecent AuthSecured audit entries")
                : message("messages.admin-audit-title-player", "&eRecent audit entries for {player}",
                Map.of("player", normalizedUsername)));
        if (entries.isEmpty()) {
            lines.add(message("messages.admin-audit-empty", "&7No matching entries."));
            return List.copyOf(lines);
        }
        for (AuditEntry entry : entries) {
            String username = entry.username() == null
                    ? message("messages.admin-audit-unknown-player", "unknown") : entry.username();
            String category = auditToken("category", entry.category());
            String detail = auditToken("event", entry.reason() != null ? entry.reason() : entry.event());
            String outcome = entry.successful() == null ? "" : " " + (entry.successful()
                    ? message("messages.admin-audit-success", "success")
                    : message("messages.admin-audit-failure", "failure"));
            lines.add(message("messages.admin-audit-line",
                    "&7{time} &f{player} &8[{category}{outcome}] &7{detail}", Map.of(
                            "time", TIME_FORMAT.format(entry.createdAt()),
                            "player", username,
                            "category", category,
                            "outcome", outcome,
                            "detail", detail)));
        }
        return List.copyOf(lines);
    }

    private void addCheck(List<String> lines, boolean healthy, String success, String failure) {
        lines.add(message(healthy ? "messages.admin-doctor-check-ok" : "messages.admin-doctor-check-fail",
                healthy ? "&a[OK] &7{message}" : "&c[FAIL] &7{message}",
                Map.of("message", healthy ? success : failure)));
    }

    private String auditToken(String group, String value) {
        if (value == null || value.isBlank()) {
            return message("messages.admin-audit-unknown-event", "unknown");
        }
        String key = value.toLowerCase(Locale.ROOT).replace('_', '-');
        String fallback = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return message("messages.admin-audit-" + group + "-" + key, fallback);
    }

    private String seconds(long value) {
        return message("messages.admin-seconds", "{seconds} seconds",
                Map.of("seconds", Long.toString(value)));
    }

    private String message(String key, String fallback) {
        return container.getConfigManager().getMessage(key, fallback);
    }

    private String message(String key, String fallback, Map<String, String> placeholders) {
        return container.getConfigManager().getMessage(key, fallback, placeholders);
    }

    private static String color(boolean healthy) {
        return healthy ? "§a" : "§c";
    }

    private boolean hasAnyValue(String directPath, String environmentPath) {
        return !container.getConfigManager().getString(directPath, "").isBlank()
                || !container.getConfigManager().getString(environmentPath, "").isBlank();
    }
}
