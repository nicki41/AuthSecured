package io.github.touchfalls.authsecured.infrastructure.notification;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.SecurityNotifier;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Discord and Telegram alert transport with per-player anti-spam cooldown. */
public final class WebhookSecurityNotifier implements SecurityNotifier {

    private static final int MAX_COOLDOWN_ENTRIES = 10_000;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String discordWebhookUrl;
    private final String telegramBotToken;
    private final String telegramChatId;
    private final long cooldownSeconds;
    private final PlatformLogger logger;
    private final Map<String, Instant> lastNotification = new ConcurrentHashMap<>();

    public WebhookSecurityNotifier(String discordWebhookUrl, String telegramBotToken,
                                   String telegramChatId, long cooldownSeconds,
                                   PlatformLogger logger) {
        this.discordWebhookUrl = validateDiscordUrl(safe(discordWebhookUrl), logger);
        this.telegramBotToken = safe(telegramBotToken);
        this.telegramChatId = safe(telegramChatId);
        this.cooldownSeconds = Math.max(10, cooldownSeconds);
        this.logger = logger;
    }

    @Override
    public void loginFailure(String username, String reason) {
        try {
            String safeUsername = sanitize(username, 16);
            String safeReason = sanitize(reason, 48);
            String key = safeUsername.toLowerCase(java.util.Locale.ROOT);
            Instant now = Instant.now();
            if (lastNotification.size() >= MAX_COOLDOWN_ENTRIES) {
                lastNotification.entrySet().removeIf(entry ->
                        entry.getValue().plusSeconds(cooldownSeconds).isBefore(now));
                if (lastNotification.size() >= MAX_COOLDOWN_ENTRIES && !lastNotification.containsKey(key)) {
                    return;
                }
            }
            Instant previous = lastNotification.putIfAbsent(key, now);
            if (previous != null && previous.plusSeconds(cooldownSeconds).isAfter(now)) {
                return;
            }
            lastNotification.put(key, now);
            String text = "AuthSecured: login failure for " + safeUsername + " (" + safeReason + ")";
            sendDiscord(text);
            sendTelegram(text);
        } catch (RuntimeException exception) {
            logger.warning("AuthSecured login notification configuration is invalid.");
        }
    }

    private void sendDiscord(String text) {
        if (discordWebhookUrl.isBlank()) return;
        String body = "{\"content\":\"" + jsonEscape(text) + "\"}";
        send(HttpRequest.newBuilder(URI.create(discordWebhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), "Discord");
    }

    private void sendTelegram(String text) {
        if (telegramBotToken.isBlank() || telegramChatId.isBlank()) return;
        String form = "chat_id=" + encode(telegramChatId) + "&text=" + encode(text);
        URI endpoint = URI.create("https://api.telegram.org/bot" + telegramBotToken + "/sendMessage");
        send(HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(), "Telegram");
    }

    private void send(HttpRequest request, String channel) {
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null || response == null || response.statusCode() >= 300) {
                        logger.warning("AuthSecured " + channel + " notification failed.");
                    }
                });
    }

    static String sanitize(String value, int maximumLength) {
        String safeValue = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safeValue.substring(0, Math.min(maximumLength, safeValue.length()));
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String validateDiscordUrl(String value, PlatformLogger logger) {
        if (value.isBlank()) return value;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && Set.of("discord.com", "discordapp.com", "canary.discord.com", "ptb.discord.com")
                    .contains(host.toLowerCase(java.util.Locale.ROOT))
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null && uri.getFragment() == null
                    && uri.getPath().startsWith("/api/webhooks/")) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid URL is disabled below without logging the secret URL.
        }
        logger.warning("Discord webhook URL is invalid; Discord alerts are disabled.");
        return "";
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }
}
