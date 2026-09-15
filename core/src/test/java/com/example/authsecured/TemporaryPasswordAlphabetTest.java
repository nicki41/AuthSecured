package com.example.authsecured;

import com.example.authsecured.infrastructure.security.SecureRandomProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryPasswordAlphabetTest {
    @Test
    void generatedPasswordContainsOnlyMinecraftSafeAlphanumericCharacters() {
        for (int i = 0; i < 100; i++) {
            String password = new String(SecureRandomProvider.generateTemporaryPassword(64));
            assertTrue(password.matches("[A-HJ-NP-Za-km-z2-9]{64}"), password);
        }
    }
}
