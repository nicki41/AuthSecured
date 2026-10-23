package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.infrastructure.security.IpHashService;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void ipv4AddressesHashExactlyAsBefore() {
        IpHashService hashing = new IpHashService("private-test-secret");
        assertArrayEquals(hashing.hashIp("192.0.2.1"), hashing.hashIp("192.0.2.1"));
        assertFalse(java.util.Arrays.equals(hashing.hashIp("192.0.2.1"), hashing.hashIp("192.0.2.2")));
    }

    @Test
    void ipv6AddressesInTheSame64BitPrefixHashTheSame() {
        IpHashService hashing = new IpHashService("private-test-secret");
        byte[] first = hashing.hashIp("2001:db8:85a3::1");
        byte[] second = hashing.hashIp("2001:db8:85a3:0:ffff:ffff:ffff:ffff");
        // Same /64 network (rotating the low 64 bits, as IPv6 privacy addressing does)
        // must not let an attacker escape a per-IP rate limit or account cap.
        assertArrayEquals(first, second);
    }

    @Test
    void ipv6AddressesInDifferentPrefixesHashDifferently() {
        IpHashService hashing = new IpHashService("private-test-secret");
        byte[] a = hashing.hashIp("2001:db8:85a3::1");
        byte[] b = hashing.hashIp("2001:db8:85a4::1");
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    void ipv4MappedIpv6LiteralHashesLikePlainIpv4() {
        IpHashService hashing = new IpHashService("private-test-secret");
        assertArrayEquals(hashing.hashIp("192.0.2.1"), hashing.hashIp("::ffff:192.0.2.1"));
    }
}
