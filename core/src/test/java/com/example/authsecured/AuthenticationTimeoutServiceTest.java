package com.example.authsecured;

import com.example.authsecured.application.AuthenticationTimeoutService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationTimeoutServiceTest {

    @Test
    void drainsEachExpiredPlayerOnlyOnce() {
        AuthenticationTimeoutService service = new AuthenticationTimeoutService(true, 30);
        UUID player = UUID.randomUUID();

        service.start(player);

        assertTrue(service.drainExpired(Instant.now().plusSeconds(29)).isEmpty());
        assertEquals(java.util.List.of(player), service.drainExpired(Instant.now().plusSeconds(31)));
        assertTrue(service.drainExpired(Instant.now().plusSeconds(60)).isEmpty());
    }

    @Test
    void disablingClearsDeadlinesAndValidatesConfiguration() {
        AuthenticationTimeoutService service = new AuthenticationTimeoutService(true, 60);
        service.start(UUID.randomUUID());

        service.configure(false, 60);

        assertFalse(service.isEnabled());
        assertTrue(service.drainExpired(Instant.now().plusSeconds(120)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.configure(true, 4));
    }

    @Test
    void ensureStartedDoesNotExtendAnExistingDeadline() {
        AuthenticationTimeoutService service = new AuthenticationTimeoutService(true, 30);
        UUID player = UUID.randomUUID();
        service.start(player);

        service.ensureStarted(player);

        assertEquals(java.util.List.of(player), service.drainExpired(Instant.now().plusSeconds(31)));
    }
}
