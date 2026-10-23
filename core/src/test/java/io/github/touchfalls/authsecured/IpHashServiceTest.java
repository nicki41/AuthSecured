package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.infrastructure.security.IpHashService;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class IpHashServiceTest {
    @Test
    void refusesMissingPrivateSecret() {
        assertThrows(IllegalArgumentException.class, () -> new IpHashService(null));
        assertThrows(IllegalArgumentException.class, () -> new IpHashService("  "));
    }

    @Test
    void unknownAddressDoesNotShareTheRealZeroAddressBucket() {
        PlatformLogger logger = mock(PlatformLogger.class);
        IpHashService hashing = new IpHashService("private-test-secret", logger);
        assertNull(hashing.hashIp(null));
        assertNull(hashing.hashIp("  "));
        org.junit.jupiter.api.Assertions.assertNotNull(hashing.hashIp("0.0.0.0"));
        verify(logger, times(1)).severe(contains("Player IP address is unavailable"));
    }
}
