package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.PasswordService;
import io.github.touchfalls.authsecured.infrastructure.security.Argon2PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void testPasswordPolicyValidation() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1024, 1, 1, 16, 8);
        PasswordService passwordService = new PasswordService(hasher, 10, 128, 2);

        assertFalse(passwordService.validatePolicy("short".toCharArray()));
        assertTrue(passwordService.validatePolicy("securePassword123".toCharArray()));
        assertFalse(passwordService.validatePolicy(null));

        passwordService.shutdown();
    }
}
