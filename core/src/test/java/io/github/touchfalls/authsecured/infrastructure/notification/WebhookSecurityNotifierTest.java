package io.github.touchfalls.authsecured.infrastructure.notification;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WebhookSecurityNotifierTest {

    private static final PlatformLogger LOGGER = new PlatformLogger() {
        @Override public void info(String message) { }
        @Override public void warning(String message) { }
        @Override public void severe(String message) { }
        @Override public void severe(String message, Throwable throwable) { }
    };

    @Test
    void sanitizesUntrustedNotificationFields() {
        assertEquals("Bad_Name_newline", WebhookSecurityNotifier.sanitize("Bad Name\nnewline", 32));
        assertEquals("INVALID_", WebhookSecurityNotifier.sanitize("INVALID<script>", 8));
    }

    @Test
    void invalidWebhookCannotBreakAuthenticationFlow() {
        WebhookSecurityNotifier notifier = new WebhookSecurityNotifier(
                "not a valid URI", "", "", 10, LOGGER);

        assertDoesNotThrow(() -> notifier.loginFailure("Player", "INVALID_PASSWORD"));
    }

    @Test
    void rejectsNonDiscordHostsAndNonHttpsSchemes() {
        PlatformLogger logger = mock(PlatformLogger.class);
        WebhookSecurityNotifier first = new WebhookSecurityNotifier(
                "http://discord.com/api/webhooks/1/token", "", "", 10, logger);
        WebhookSecurityNotifier second = new WebhookSecurityNotifier(
                "https://evil.example/api/webhooks/1/token", "", "", 10, logger);
        assertDoesNotThrow(() -> first.loginFailure("Player", "INVALID_PASSWORD"));
        assertDoesNotThrow(() -> second.loginFailure("Player", "INVALID_PASSWORD"));
        verify(logger, times(2)).warning(contains("Discord webhook URL is invalid"));
    }

    @Test
    void floodingWithFakeNamesEvictsTheOldestEntryInsteadOfSilencingNewOnes() throws Exception {
        WebhookSecurityNotifier notifier = new WebhookSecurityNotifier("", "", "", 300, LOGGER);
        java.lang.reflect.Field field = WebhookSecurityNotifier.class.getDeclaredField("lastNotification");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ?> cooldowns = (java.util.Map<String, ?>) field.get(notifier);

        // Fill past capacity with distinct fake names, as an attacker rotating names would.
        for (int i = 0; i < 10_001; i++) {
            notifier.loginFailure("attacker" + i, "INVALID_PASSWORD");
        }
        assertEquals(10_000, cooldowns.size(), "the map must stay bounded");

        // A brand-new, legitimate name must still be tracked, not silently dropped.
        notifier.loginFailure("RealPlayer", "INVALID_PASSWORD");
        assertEquals(10_000, cooldowns.size());
        org.junit.jupiter.api.Assertions.assertTrue(cooldowns.containsKey("realplayer"));
    }
}
