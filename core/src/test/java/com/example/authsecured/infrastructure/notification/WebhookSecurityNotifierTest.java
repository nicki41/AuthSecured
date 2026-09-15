package com.example.authsecured.infrastructure.notification;

import com.example.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
