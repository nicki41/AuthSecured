package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.infrastructure.security.Argon2PasswordHasher;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Argon2PasswordHasherTest {

    @Test
    void testHashAndVerify() {
        // Low memory and iteration settings for fast test execution
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1024, 1, 1, 16, 8);

        char[] password = "MySecretPassword123!".toCharArray();
        String hash = hasher.hash(password);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$argon2id$"));

        assertTrue(hasher.verify(password, hash));
        assertFalse(hasher.verify("WrongPassword".toCharArray(), hash));
    }

    @Test
    void rejectsHostileOrCorruptStoredCostsBeforeAllocatingMemory() {
        PlatformLogger logger = org.mockito.Mockito.mock(PlatformLogger.class);
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1024, 1, 1, 16, 8, logger);
        String hash = hasher.hash("correct-password".toCharArray());
        assertFalse(hasher.verify("correct-password".toCharArray(),
                hash.replace("m=1024", "m=999999999")));
        assertFalse(hasher.verify("correct-password".toCharArray(),
                hash.replace("t=1", "t=21")));
        assertFalse(hasher.verify("correct-password".toCharArray(),
                hash.replace("p=1", "p=0")));
        assertFalse(hasher.verify("correct-password".toCharArray(),
                hash.replace("m=1024", "m=broken")));
        org.mockito.Mockito.verify(logger, org.mockito.Mockito.atLeastOnce())
                .warning(org.mockito.ArgumentMatchers.contains("Stored Argon2 hash rejected"));
    }

    @Test
    void verifiesUnicodePasswordAfterUtf8BufferIsCleared() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1024, 1, 1, 16, 8);
        char[] password = "Пароль-秘密-123".toCharArray();
        assertTrue(hasher.verify(password, hasher.hash(password)));
    }
}
