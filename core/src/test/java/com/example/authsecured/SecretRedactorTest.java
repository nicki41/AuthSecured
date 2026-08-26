package com.example.authsecured;

import com.example.authsecured.util.SecretRedactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretRedactorTest {

    @Test
    void redactsAllPlayerPasswordCommandArguments() {
        assertEquals("login [REDACTED]", SecretRedactor.redactCommand("login hunter2"));
        assertEquals("/register [REDACTED] [REDACTED]",
                SecretRedactor.redactCommand("/register one two"));
        assertEquals("authsecured:changepassword [REDACTED] [REDACTED]",
                SecretRedactor.redactCommand("authsecured:changepassword old new"));
    }

    @Test
    void keepsAdminTargetButRedactsReplacementPassword() {
        assertEquals("/authadmin resetpassword Steve [REDACTED]",
                SecretRedactor.redactCommand("/authadmin resetpassword Steve verySecret"));
    }

    @Test
    void leavesNonSensitiveCommandsUnchanged() {
        assertEquals("authadmin unlock Steve", SecretRedactor.redactCommand("authadmin unlock Steve"));
        assertEquals("help login", SecretRedactor.redactCommand("help login"));
    }
}
